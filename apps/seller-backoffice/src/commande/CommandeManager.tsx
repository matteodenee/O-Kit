import { type FormEvent, useCallback, useEffect, useState } from 'react';

import type { CatalogueRequest } from '../api/catalogue';
import {
    actionsPossibles,
    changerStatut,
    type ActionStatut,
    type Commande,
    getCommande,
    libelleStatut,
    listerCommandes,
    type StatutCommande,
} from '../api/commande';

const filtres: { valeur: StatutCommande | null; libelle: string }[] = [
    { valeur: null, libelle: 'Toutes' },
    { valeur: 'CONFIRMEE', libelle: libelleStatut.CONFIRMEE },
    { valeur: 'EN_PREPARATION', libelle: libelleStatut.EN_PREPARATION },
    { valeur: 'EXPEDIEE', libelle: libelleStatut.EXPEDIEE },
    { valeur: 'LIVREE', libelle: libelleStatut.LIVREE },
    { valeur: 'ANNULEE', libelle: libelleStatut.ANNULEE },
];

const TAILLE_PAGE = 20;
const RAFRAICHISSEMENT_MS = 30_000;

const euros = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' });

function formatDate(iso: string | null) {
    if (!iso) return '—';
    return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' });
}

function nbArticles(commande: Commande) {
    return commande.lignes.filter((l) => l.statut === 'ACTIVE').reduce((n, l) => n + l.quantite, 0);
}

export default function CommandeManager({ request }: { request: CatalogueRequest }) {
    const [filtre, setFiltre] = useState<StatutCommande | null>('CONFIRMEE');
    const [page, setPage] = useState(0);
    const [commandes, setCommandes] = useState<Commande[]>([]);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [message, setMessage] = useState<string | null>(null);

    const [selection, setSelection] = useState<Commande | null>(null);
    const [actionEnCours, setActionEnCours] = useState<ActionStatut | null>(null);
    const [codeSuivi, setCodeSuivi] = useState('');
    const [busy, setBusy] = useState(false);

    // Incrémenter « rechargement » relance la requête (bouton Actualiser, après un changement de statut).
    const [rechargement, setRechargement] = useState(0);
    const charger = useCallback(() => setRechargement((n) => n + 1), []);

    useEffect(() => {
        let active = true;
        const lire = () => {
            listerCommandes(request, { statut: filtre, page, size: TAILLE_PAGE })
                .then((resultat) => {
                    if (!active) return;
                    setCommandes(resultat.content);
                    setTotalPages(resultat.page.totalPages);
                    setTotalElements(resultat.page.totalElements);
                    setError(null);
                })
                .catch((e: unknown) => {
                    if (active) setError(e instanceof Error ? e.message : 'Les commandes sont indisponibles.');
                })
                .finally(() => {
                    if (active) setLoading(false);
                });
        };
        lire();
        // Les acheteurs peuvent passer ou annuler des commandes à tout moment
        const timer = window.setInterval(lire, RAFRAICHISSEMENT_MS);
        return () => {
            active = false;
            window.clearInterval(timer);
        };
    }, [request, filtre, page, rechargement]);

    const ouvrir = async (idCommande: number) => {
        setActionEnCours(null);
        setMessage(null);
        try {
            setSelection(await getCommande(request, idCommande));
        } catch (e) {
            setError(e instanceof Error ? e.message : 'Commande indisponible.');
        }
    };

    const choisirAction = (action: ActionStatut) => {
        setActionEnCours(action);
        setCodeSuivi(selection?.codeSuiviTransporteur ?? '');
        setMessage(null);
        setError(null);
    };

    const valider = async (event: FormEvent) => {
        event.preventDefault();
        if (!selection || !actionEnCours) return;
        const code = codeSuivi.trim();
        if (actionEnCours.cible === 'EXPEDIEE' && !code) {
            setError('Le code de suivi transporteur est obligatoire pour expédier.');
            return;
        }
        setBusy(true);
        setError(null);
        try {
            const majour = await changerStatut(
                request,
                selection.idCommande,
                actionEnCours.cible,
                actionEnCours.cible === 'EXPEDIEE' ? code : undefined,
            );
            setSelection(majour);
            setActionEnCours(null);
            setMessage(`Commande n° ${majour.idCommande} : ${libelleStatut[majour.statut].toLowerCase()}.`);
            charger();
        } catch (e) {
            setError(e instanceof Error ? e.message : 'Le changement de statut a échoué.');
            // Statut peut-être modifié entre-temps (client qui annule, autre vendeur) : on resynchronise
            void ouvrir(selection.idCommande);
            charger();
        } finally {
            setBusy(false);
        }
    };

    return (
        <div className="catalogue-page">
            <header className="catalogue-header">
                <div>
                    <p className="eyebrow">COMMANDES</p>
                    <h1>Suivi des commandes</h1>
                    <p>
                        Accepte, prépare, expédie ou annule les commandes. L’acheteur voit le nouveau
                        statut dans son application.
                    </p>
                </div>
                <button className="compact-button" type="button" disabled={loading} onClick={() => { setLoading(true); charger(); }}>
                    Actualiser
                </button>
            </header>

            {error && <p className="alert error" role="alert">{error}</p>}
            {message && <p className="alert success" role="status">{message}</p>}

            <div className="catalogue-layout">
                <section className="catalogue-list-panel">
                    <div className="order-filters" role="tablist" aria-label="Filtrer par statut">
                        {filtres.map((f) => (
                            <button
                                key={f.libelle}
                                type="button"
                                role="tab"
                                aria-selected={filtre === f.valeur}
                                className={filtre === f.valeur ? 'active' : undefined}
                                onClick={() => { setLoading(true); setFiltre(f.valeur); setPage(0); }}
                            >
                                {f.libelle}
                            </button>
                        ))}
                    </div>

                    {loading && commandes.length === 0 ? (
                        <div className="catalogue-state"><div className="loader" /> Chargement des commandes…</div>
                    ) : commandes.length === 0 ? (
                        <div className="catalogue-state">Aucune commande pour ce filtre.</div>
                    ) : (
                        <div className="table-scroll">
                            <table className="product-table">
                                <thead>
                                    <tr>
                                        <th>Commande</th>
                                        <th>Date</th>
                                        <th>Articles</th>
                                        <th>Total TTC</th>
                                        <th>Statut</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {commandes.map((c) => (
                                        <tr
                                            key={c.idCommande}
                                            className={selection?.idCommande === c.idCommande ? 'selected' : undefined}
                                            onClick={() => void ouvrir(c.idCommande)}
                                        >
                                            <td>
                                                <strong>n° {c.idCommande}</strong>
                                                <small>
                                                    {c.adresseLivraison
                                                        ? `${c.adresseLivraison.codePostal} ${c.adresseLivraison.ville}`
                                                        : 'Adresse non renseignée'}
                                                </small>
                                            </td>
                                            <td>{formatDate(c.dateCommande)}</td>
                                            <td>{nbArticles(c)}</td>
                                            <td>{euros.format(c.totalTtc)}</td>
                                            <td><span className={`status-badge order-${c.statut.toLowerCase()}`}>{libelleStatut[c.statut]}</span></td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}

                    {totalPages > 1 && (
                        <div className="order-pagination">
                            <button type="button" className="secondary-action" disabled={page === 0} onClick={() => { setLoading(true); setPage((p) => p - 1); }}>
                                ← Précédentes
                            </button>
                            <span>Page {page + 1} / {totalPages} · {totalElements} commandes</span>
                            <button type="button" className="secondary-action" disabled={page + 1 >= totalPages} onClick={() => { setLoading(true); setPage((p) => p + 1); }}>
                                Suivantes →
                            </button>
                        </div>
                    )}
                </section>

                <aside className="product-editor">
                    {!selection ? (
                        <div className="empty-editor">
                            <p className="eyebrow">DÉTAIL</p>
                            <h2>Sélectionne une commande</h2>
                            <p>Clique sur une ligne pour voir les articles et changer son statut.</p>
                        </div>
                    ) : (
                        <>
                            <div className="editor-heading">
                                <div>
                                    <p className="eyebrow">COMMANDE N° {selection.idCommande}</p>
                                    <h2>{libelleStatut[selection.statut]}</h2>
                                </div>
                                <button className="close-button" type="button" aria-label="Fermer" onClick={() => setSelection(null)}>×</button>
                            </div>

                            <div className="readonly-relations">
                                <span><small>Passée le</small>{formatDate(selection.dateCommande)}</span>
                                <span><small>Total TTC</small>{euros.format(selection.totalTtc)}</span>
                                <span>
                                    <small>Livraison</small>
                                    {selection.adresseLivraison
                                        ? `${selection.adresseLivraison.rue}${selection.adresseLivraison.complement ? `, ${selection.adresseLivraison.complement}` : ''}, ${selection.adresseLivraison.codePostal} ${selection.adresseLivraison.ville}`
                                        : '—'}
                                </span>
                                <span><small>Suivi transporteur</small>{selection.codeSuiviTransporteur ?? '—'}</span>
                            </div>

                            <ul className="order-lines">
                                {selection.lignes.map((l) => (
                                    <li key={l.idLigneCommande} className={l.statut === 'ANNULEE' ? 'cancelled' : undefined}>
                                        <div>
                                            <strong>{l.titreProduit}</strong>
                                            <small>
                                                Taille {l.taille ?? '—'} · × {l.quantite}
                                                {l.flocage ? ` · Flocage « ${l.flocage.texte} » n° ${l.flocage.numero}` : ''}
                                                {l.statut === 'ANNULEE' ? ' · annulée' : ''}
                                            </small>
                                        </div>
                                        <span>{euros.format(l.totalTtc)}</span>
                                    </li>
                                ))}
                            </ul>

                            {actionsPossibles(selection.statut).length === 0 ? (
                                <p className="order-final">Cette commande est dans un état final : aucune action possible.</p>
                            ) : actionEnCours ? (
                                <form onSubmit={valider}>
                                    {actionEnCours.cible === 'EXPEDIEE' && (
                                        <label>
                                            CODE DE SUIVI TRANSPORTEUR
                                            <input
                                                autoFocus
                                                maxLength={100}
                                                required
                                                value={codeSuivi}
                                                onChange={(e) => setCodeSuivi(e.target.value)}
                                                placeholder="ex : 6A12345678901"
                                            />
                                        </label>
                                    )}
                                    {actionEnCours.cible === 'ANNULEE' && (
                                        <p className="catalogue-limit">
                                            L’annulation remet les articles en stock et rembourse intégralement l’acheteur.
                                            Elle est définitive.
                                        </p>
                                    )}
                                    <div className="editor-actions">
                                        <button type="button" className="secondary-action" disabled={busy} onClick={() => setActionEnCours(null)}>
                                            Retour
                                        </button>
                                        <button type="submit" className={actionEnCours.danger ? 'danger-action' : 'primary-action'} disabled={busy}>
                                            {busy ? 'Enregistrement…' : `Confirmer : ${actionEnCours.libelle.toLowerCase()}`}
                                        </button>
                                    </div>
                                </form>
                            ) : (
                                <div className="order-actions">
                                    {actionsPossibles(selection.statut).map((a) => (
                                        <button
                                            key={a.cible}
                                            type="button"
                                            className={a.danger ? 'danger-action' : 'primary-action'}
                                            onClick={() => choisirAction(a)}
                                        >
                                            {a.libelle}
                                        </button>
                                    ))}
                                </div>
                            )}
                        </>
                    )}
                </aside>
            </div>
        </div>
    );
}
