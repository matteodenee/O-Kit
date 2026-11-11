import { useCallback, useEffect, useState } from 'react';
import { getProduct, listAllProducts, type CatalogueRequest, type ProductDetail } from '../api/catalogue';
import { createStock, listStocks, restock, type StockDto } from '../api/stock';

export default function StockManager({ request }: { request: CatalogueRequest }) {
    const [products, setProducts] = useState<ProductDetail[]>([]);
    const [stocks, setStocks] = useState<Map<number, StockDto>>(new Map());
    const [error, setError] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);

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

    const add = async (variantId: number, initialised: boolean) => {
        const raw = window.prompt(initialised ? 'Quantité à ajouter ?' : 'Quantité initiale ?', '10');
        const quantity = Number(raw);
        if (!raw || !Number.isInteger(quantity) || quantity < (initialised ? 1 : 0)) return;
        try {
            const updated = initialised ? await restock(request, variantId, quantity) : await createStock(request, variantId, quantity);
            setStocks((m) => new Map(m).set(variantId, updated));
        } catch (e) {
            setError(e instanceof Error ? e.message : 'Échec de la mise à jour.');
        }
    };

    if (loading) return <p aria-busy="true">Chargement du stock…</p>;

    return (
        <section>
            <h1>Stock</h1>
            {error && <p className="alert error" role="alert">{error}</p>}
            {products.map((p) => (
                <article key={p.id}>
                    <h3>{p.titre}</h3>
                    <ul>
                        {p.variantes.map((v) => {
                            const s = stocks.get(v.id);
                            return (
                                <li key={v.id} style={{ color: s?.status === 'LOW_STOCK' || s?.status === 'OUT_OF_STOCK' ? '#c62828' : undefined }}>
                                    {v.taille} : {s ? s.stock : 'non initialisée'}
                                    <button type="button" onClick={() => add(v.id, Boolean(s))}>
                                        {s ? 'Ajouter du stock' : 'Initialiser'}
                                    </button>
                                </li>
                            );
                        })}
                    </ul>
                </article>
            ))}
        </section>
    );
}