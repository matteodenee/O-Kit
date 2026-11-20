import { Fragment, useCallback, useEffect, useMemo, useState } from 'react';
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

function askInt(message: string, def: string, min: number): number | null {
    const raw = window.prompt(message, def);
    if (raw === null || raw.trim() === '') return null;
    const n = Number(raw);
    return Number.isInteger(n) && n >= min ? n : null;
}

export default function StockManager({ request }: { request: CatalogueRequest }) {
    const [products, setProducts] = useState<ProductDetail[]>([]);
    const [stocks, setStocks] = useState<Map<number, StockDto>>(new Map());
    const [error, setError] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);
    const [query, setQuery] = useState('');
    const [onlyAlerts, setOnlyAlerts] = useState(false);
    const [history, setHistory] = useState<{ variantId: number; rows: MovementDto[] } | null>(null);

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
            setError(e instanceof Error ? e.message : failMsg);
        }
    };

    const initOne = (id: number) => {
        const q = askInt('Quantité initiale ?', '10', 0);
        if (q !== null) void apply(id, () => createStock(request, id, q), 'Échec de l’initialisation.');
    };
    const add = (id: number) => {
        const q = askInt('Quantité à ajouter ?', '10', 1);
        if (q !== null) void apply(id, () => restock(request, id, q), 'Échec de l’ajout.');
    };
    const correct = (id: number, current: number) => {
        const q = askInt('Stock réel après inventaire ?', String(current), 0);
        if (q !== null) void apply(id, () => adjustStock(request, id, q), 'Échec de la correction.');
    };
    const editThreshold = (id: number, current: number) => {
        const q = askInt('Seuil d’alerte (stock bas sous cette valeur) ?', String(current), 0);
        if (q !== null) void apply(id, () => setThreshold(request, id, q), 'Échec de la mise à jour du seuil.');
    };

    const initAll = async (ids: number[]) => {
        const q = askInt('Quantité initiale pour chaque taille manquante ?', '10', 0);
        if (q === null) return;
        try {
            const created = await createStocksBulk(request, ids, q);
            setStocks((m) => {
                const next = new Map(m);
                created.forEach((s) => next.set(s.variantId, s));
                return next;
            });
            setError(null);
            window.dispatchEvent(new Event('stock:changed'));
        } catch (e) {
            setError(e instanceof Error ? e.message : 'Échec de l’initialisation.');
        }
    };

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
        </section>
    );
}