import { Fragment, useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { getProduct, listAllProducts, type CatalogueRequest, type ProductDetail } from '../api/catalogue';
import {
    adjustStock, createStock, createStocksBulk, listMovements, listStocks, restock, setThreshold,
    type MovementDto, type StockDto,
} from '../api/stock';

const STATUS_LABEL = {
    OUT_OF_STOCK: 'Rupture',
    LOW_STOCK: 'Stock bas',
    IN_STOCK: 'En stock',
} as const;

const REASON_LABEL: Record<MovementDto['reason'], string> = {
    INITIAL: 'Initialisation',
    RESTOCK: 'Réapprovisionnement',
    SELLER_ADJUSTMENT: 'Correction',
    ORDER_PAID: 'Commande payée',
    ORDER_CANCELLED: 'Commande annulée',
};

type StockPrompt = {
    title: string;
    label: string;
    defaultValue: string;
    min: number;
    confirmLabel: string;
    run: (value: number) => Promise<void>;
};

function QuantityDialog({ prompt, onClose }: { prompt: StockPrompt; onClose: () => void }) {
    const [value, setValue] = useState(prompt.defaultValue);
    const [error, setError] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape' && !busy) onClose();
        };
        window.addEventListener('keydown', onKeyDown);
        return () => window.removeEventListener('keydown', onKeyDown);
    }, [busy, onClose]);

    const submit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const parsed = Number(value.trim());
        if (!Number.isInteger(parsed) || parsed < prompt.min) {
            setError(`Saisis un nombre entier supérieur ou égal à ${prompt.min}.`);
            return;
        }

        setBusy(true);
        setError(null);
        try {
            await prompt.run(parsed);
            onClose();
        } catch (e) {
            setError(e instanceof Error ? e.message : 'Opération impossible.');
        } finally {
            setBusy(false);
        }
    };

    return (
        <div
            className="modal-backdrop"
            role="presentation"
            onMouseDown={(event) => {
                if (event.target === event.currentTarget && !busy) onClose();
            }}
        >
            <form className="modal-card" role="dialog" aria-modal="true" aria-labelledby="stock-dialog-title" onSubmit={submit}>
                <h2 id="stock-dialog-title">{prompt.title}</h2>
                <label className="modal-field">
                    <span>{prompt.label}</span>
                    <input
                        autoFocus
                        inputMode="numeric"
                        min={prompt.min}
                        step={1}
                        type="number"
                        value={value}
                        onChange={(event) => setValue(event.target.value)}
                    />
                </label>
                {error && <p className="alert error" role="alert">{error}</p>}
                <div className="modal-actions">
                    <button type="button" className="secondary-action" disabled={busy} onClick={onClose}>
                        Annuler
                    </button>
                    <button type="submit" className="primary-action" disabled={busy}>
                        {busy ? 'En cours…' : prompt.confirmLabel}
                    </button>
                </div>
            </form>
        </div>
    );
}

export default function StockManager({ request }: { request: CatalogueRequest }) {
    const [products, setProducts] = useState<ProductDetail[]>([]);
    const [stocks, setStocks] = useState<Map<number, StockDto>>(new Map());
    const [error, setError] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);
    const [query, setQuery] = useState('');
    const [onlyAlerts, setOnlyAlerts] = useState(false);
    const [history, setHistory] = useState<{ variantId: number; rows: MovementDto[] } | null>(null);
    const [stockPrompt, setStockPrompt] = useState<StockPrompt | null>(null);

    const load = useCallback(async () => {
        try {
            const summaries = await listAllProducts(request);
            const [details, rows] = await Promise.all([
                Promise.all(summaries.map((p) => getProduct(request, p.id))),
                listStocks(request),
            ]);
            setProducts(details);
            setStocks(new Map(rows.map((s) => [s.variantId, s])));
            setError(null);
        } catch (e) {
            setError(e instanceof Error ? e.message : 'Stock indisponible.');
        } finally {
            setLoading(false);
        }
    }, [request]);

    useEffect(() => { void load(); }, [load]);

    const refreshHistory = async (variantId: number) => {
        if (history?.variantId === variantId) {
            try { setHistory({ variantId, rows: await listMovements(request, variantId) }); } catch { /* ignoré */ }
        }
    };

    const apply = async (variantId: number, action: () => Promise<StockDto>, failMsg: string) => {
        try {
            const updated = await action();
            setStocks((m) => new Map(m).set(variantId, updated));
            setError(null);
            window.dispatchEvent(new Event('stock:changed'));
            await refreshHistory(variantId);
        } catch (e) {
            const message = e instanceof Error ? e.message : failMsg;
            setError(message);
            throw new Error(message, { cause: e });
        }
    };

    const initOne = (id: number) => setStockPrompt({
        title: 'Initialiser le stock',
        label: 'Quantité initiale',
        defaultValue: '10',
        min: 0,
        confirmLabel: 'Initialiser',
        run: (quantity) => apply(id, () => createStock(request, id, quantity), 'Échec de l’initialisation.'),
    });

    const add = (id: number) => setStockPrompt({
        title: 'Ajouter du stock',
        label: 'Quantité à ajouter',
        defaultValue: '10',
        min: 1,
        confirmLabel: 'Ajouter',
        run: (quantity) => apply(id, () => restock(request, id, quantity), 'Échec de l’ajout.'),
    });

    const correct = (id: number, current: number) => setStockPrompt({
        title: 'Corriger le stock',
        label: 'Stock réel après inventaire',
        defaultValue: String(current),
        min: 0,
        confirmLabel: 'Corriger',
        run: (quantity) => apply(id, () => adjustStock(request, id, quantity), 'Échec de la correction.'),
    });

    const editThreshold = (id: number, current: number) => setStockPrompt({
        title: 'Modifier le seuil d’alerte',
        label: 'Seuil d’alerte (stock bas sous cette valeur)',
        defaultValue: String(current),
        min: 0,
        confirmLabel: 'Enregistrer',
        run: (quantity) => apply(id, () => setThreshold(request, id, quantity), 'Échec de la mise à jour du seuil.'),
    });

    const initAll = (ids: number[]) => setStockPrompt({
        title: 'Initialiser les tailles manquantes',
        label: 'Quantité initiale pour chaque taille',
        defaultValue: '10',
        min: 0,
        confirmLabel: 'Initialiser',
        run: async (quantity) => {
            try {
                const created = await createStocksBulk(request, ids, quantity);
                setStocks((m) => {
                    const next = new Map(m);
                    created.forEach((s) => next.set(s.variantId, s));
                    return next;
                });
                setError(null);
                window.dispatchEvent(new Event('stock:changed'));
            } catch (e) {
                const message = e instanceof Error ? e.message : 'Échec de l’initialisation.';
                setError(message);
                throw new Error(message, { cause: e });
            }
        },
    });

    const toggleHistory = async (variantId: number) => {
        if (history?.variantId === variantId) { setHistory(null); return; }
        try {
            setHistory({ variantId, rows: await listMovements(request, variantId) });
        } catch (e) {
            setError(e instanceof Error ? e.message : 'Historique indisponible.');
        }
    };

    const visible = useMemo(() => {
        const q = query.trim().toLowerCase();
        return products.filter((p) => {
            if (q && !p.titre.toLowerCase().includes(q)) return false;
            if (onlyAlerts) {
                return p.variantes.some((v) => {
                    const s = stocks.get(v.id);
                    return s && s.status !== 'IN_STOCK';
                });
            }
            return true;
        });
    }, [products, stocks, query, onlyAlerts]);

    if (loading) return <p aria-busy="true">Chargement du stock…</p>;

    return (
        <section className="stock-page">
            <header className="stock-header">
                <div>
                    <p className="eyebrow">STOCKS</p>
                    <h1>Gestion du stock</h1>
                </div>
                <div className="stock-tools">
                    <input
                        type="search"
                        placeholder="Rechercher un maillot…"
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                    />
                    <label className="stock-check">
                        <input type="checkbox" checked={onlyAlerts} onChange={(e) => setOnlyAlerts(e.target.checked)} />
                        Alertes seulement
                    </label>
                </div>
            </header>

            {error && <p className="alert error" role="alert">{error}</p>}
            {visible.length === 0 && <p className="stock-empty">Aucun produit à afficher.</p>}

            {visible.map((p) => {
                const missing = p.variantes.filter((v) => !stocks.has(v.id));
                const low = p.variantes.filter((v) => {
                    const s = stocks.get(v.id);
                    return s && s.status !== 'IN_STOCK';
                }).length;
                const total = p.variantes.reduce((sum, v) => sum + (stocks.get(v.id)?.stock ?? 0), 0);

                return (
                    <details key={p.id} className="stock-card">
                        <summary>
                            <span className="stock-title">{p.titre}</span>
                            <span className="stock-badges">
                <span className="badge-pill">{total} en stock</span>
                                {low > 0 && <span className="badge-pill badge-warn">{low} alerte{low > 1 ? 's' : ''}</span>}
                                {missing.length > 0 && <span className="badge-pill badge-muted">{missing.length} non initialisée{missing.length > 1 ? 's' : ''}</span>}
              </span>
                        </summary>

                        {missing.length > 0 && (
                            <div className="stock-bulk">
                                <button type="button" className="btn-primary" onClick={() => initAll(missing.map((v) => v.id))}>
                                    Initialiser toutes les tailles manquantes
                                </button>
                            </div>
                        )}

                        <table className="stock-table">
                            <thead>
                            <tr><th>Taille</th><th>Stock</th><th>Seuil</th><th>Statut</th><th aria-label="Actions" /></tr>
                            </thead>
                            <tbody>
                            {p.variantes.map((v) => {
                                const s = stocks.get(v.id);
                                const open = history?.variantId === v.id;
                                return (
                                    <Fragment key={v.id}>
                                        <tr className={s && s.status !== 'IN_STOCK' ? 'row-alert' : undefined}>
                                            <td className="cell-size">{v.taille}</td>
                                            <td className="cell-num">{s ? s.stock : '—'}</td>
                                            <td className="cell-num">{s ? s.threshold : '—'}</td>
                                            <td>
                                                {s
                                                    ? <span className={`status status-${s.status}`}>{STATUS_LABEL[s.status]}</span>
                                                    : <span className="status status-NONE">Non initialisée</span>}
                                            </td>
                                            <td className="cell-actions">
                                                {s ? (
                                                    <>
                                                        <button type="button" className="btn-sm" onClick={() => add(v.id)}>+ Ajouter</button>
                                                        <button type="button" className="btn-sm" onClick={() => correct(v.id, s.stock)}>Corriger</button>
                                                        <button type="button" className="btn-sm" onClick={() => editThreshold(v.id, s.threshold)}>Seuil</button>
                                                        <button type="button" className="btn-sm btn-ghost" onClick={() => toggleHistory(v.id)}>
                                                            {open ? 'Masquer' : 'Historique'}
                                                        </button>
                                                    </>
                                                ) : (
                                                    <button type="button" className="btn-sm" onClick={() => initOne(v.id)}>Initialiser</button>
                                                )}
                                            </td>
                                        </tr>
                                        {open && history && (
                                            <tr className="row-history">
                                                <td colSpan={5}>
                                                    {history.rows.length === 0 ? 'Aucun mouvement.' : (
                                                        <ul>
                                                            {history.rows.map((m) => (
                                                                <li key={m.id}>
                                                                    <time>{new Date(m.createdAt).toLocaleString('fr-FR')}</time>
                                                                    <span>{REASON_LABEL[m.reason]}{m.reference ? ` (${m.reference})` : ''}</span>
                                                                    <strong className={m.change > 0 ? 'plus' : 'minus'}>
                                                                        {m.change > 0 ? `+${m.change}` : m.change}
                                                                    </strong>
                                                                    <span>→ {m.stockAfter}</span>
                                                                </li>
                                                            ))}
                                                        </ul>
                                                    )}
                                                </td>
                                            </tr>
                                        )}
                                    </Fragment>
                                );
                            })}
                            </tbody>
                        </table>
                    </details>
                );
            })}

            {stockPrompt && (
                <QuantityDialog prompt={stockPrompt} onClose={() => setStockPrompt(null)} />
            )}
        </section>
    );
}