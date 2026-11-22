import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';

import {
  type CatalogueRequest,
  type ProductDetail,
  type ProductStatus,
  type ProductSummary,
  getProduct,
  listAllProducts,
  updateProduct,
} from '../api/catalogue';
import { listStocks, type StockStatus } from '../api/stock';

type CatalogueManagerProps = {
  request: CatalogueRequest;
};

type PriceSort = 'default' | 'prix_asc' | 'prix_desc';

/**
 * Niveau de stock d'un produit calculé à partir de ses tailles (ms-stock).
 * Si ms-stock est indisponible, on retombe sur le drapeau Catalogue.
 */
function resolveStockLevel(
  product: ProductSummary,
  stockByVariant: Map<number, StockStatus> | null
): StockStatus {
  const varianteIds = product.varianteIds ?? [];
  if (!stockByVariant || varianteIds.length === 0) {
    return product.enStock ? 'IN_STOCK' : 'OUT_OF_STOCK';
  }
  let hasLowStock = false;
  for (const varianteId of varianteIds) {
    const status = stockByVariant.get(varianteId) ?? 'OUT_OF_STOCK';
    if (status === 'IN_STOCK') return 'IN_STOCK';
    if (status === 'LOW_STOCK') hasLowStock = true;
  }
  return hasLowStock ? 'LOW_STOCK' : 'OUT_OF_STOCK';
}

function stockLabel(status: StockStatus) {
  if (status === 'OUT_OF_STOCK') return 'Rupture';
  if (status === 'LOW_STOCK') return 'Derniers exemplaires';
  return 'Disponible';
}

type ProductForm = {
  couleursPrincipales: string;
  description: string;
  estFlocable: boolean;
  impactEnvi: string;
  photoUrl: string;
  poids: string;
  prixDeBase: string;
  statut: ProductStatus;
  titre: string;
};

function formFromProduct(product: ProductDetail): ProductForm {
  return {
    couleursPrincipales: product.couleursPrincipales ?? '',
    description: product.description ?? '',
    estFlocable: product.estFlocable ?? false,
    impactEnvi: product.impactEnvi ?? '',
    photoUrl: product.photoUrl ?? '',
    poids: product.poids?.toString() ?? '',
    prixDeBase: product.prixDeBaseHT.toString(),
    statut: product.statut,
    titre: product.titre,
  };
}

function formatPrice(value: number) {
  return new Intl.NumberFormat('fr-FR', {
    style: 'currency',
    currency: 'EUR',
  }).format(value);
}

function uniqueSorted(values: (string | null | undefined)[]): string[] {
  return Array.from(
    new Set(
      values
        .map((value) => value?.trim())
        .filter((value): value is string => Boolean(value))
    )
  ).sort((a, b) => a.localeCompare(b, 'fr'));
}

export default function CatalogueManager({ request }: CatalogueManagerProps) {
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [stockByVariant, setStockByVariant] = useState<Map<number, StockStatus> | null>(null);
  const [selectedProduct, setSelectedProduct] = useState<ProductDetail | null>(null);
  const [form, setForm] = useState<ProductForm | null>(null);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<'TOUS' | ProductStatus>('TOUS');
  const [stockFilter, setStockFilter] = useState<'TOUS' | 'EN_STOCK' | 'RUPTURE'>('TOUS');
  const [brandFilter, setBrandFilter] = useState<string>('TOUTES');
  const [seasonFilter, setSeasonFilter] = useState<string>('TOUTES');
  const [teamFilter, setTeamFilter] = useState<string>('TOUS');
  const [priceSort, setPriceSort] = useState<PriceSort>('default');
  const [failedImages, setFailedImages] = useState<Record<number, boolean>>({});
  const [previewFailed, setPreviewFailed] = useState(false);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const loadStocks = useCallback(async () => {
    try {
      const stocks = await listStocks(request);
      return new Map(stocks.map((stock) => [stock.variantId, stock.status]));
    } catch {
      // ms-stock indisponible : on garde le drapeau produit du Catalogue.
      return null;
    }
  }, [request]);

  const loadProducts = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [loadedProducts, stocks] = await Promise.all([
        listAllProducts(request),
        loadStocks(),
      ]);
      setProducts(loadedProducts);
      setStockByVariant(stocks);
      setFailedImages({});
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Chargement du catalogue impossible.');
    } finally {
      setLoading(false);
    }
  }, [request, loadStocks]);

  useEffect(() => {
    let active = true;
    Promise.all([listAllProducts(request), loadStocks()])
      .then(([loadedProducts, stocks]) => {
        if (active) {
          setProducts(loadedProducts);
          setStockByVariant(stocks);
          setFailedImages({});
        }
      })
      .catch((loadError: unknown) => {
        if (active) {
          setError(loadError instanceof Error ? loadError.message : 'Chargement du catalogue impossible.');
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [request, loadStocks]);

  const stockLevels = useMemo(() => {
    const levels = new Map<number, StockStatus>();
    for (const product of products) {
      levels.set(product.id, resolveStockLevel(product, stockByVariant));
    }
    return levels;
  }, [products, stockByVariant]);

  const brands = useMemo(() => uniqueSorted(products.map((product) => product.nomMarque)), [products]);
  const seasons = useMemo(() => uniqueSorted(products.map((product) => product.nomSaison)), [products]);
  const teams = useMemo(() => uniqueSorted(products.map((product) => product.nomEquipe)), [products]);

  const hasActiveFilters = search.trim() !== ''
    || statusFilter !== 'TOUS'
    || stockFilter !== 'TOUS'
    || brandFilter !== 'TOUTES'
    || seasonFilter !== 'TOUTES'
    || teamFilter !== 'TOUS'
    || priceSort !== 'default';

  const resetFilters = useCallback(() => {
    setSearch('');
    setStatusFilter('TOUS');
    setStockFilter('TOUS');
    setBrandFilter('TOUTES');
    setSeasonFilter('TOUTES');
    setTeamFilter('TOUS');
    setPriceSort('default');
  }, []);

  const visibleProducts = useMemo(() => {
    const normalizedSearch = search.trim().toLocaleLowerCase('fr');
    const filtered = products.filter((product) => {
      const searchable = [product.titre, product.nomMarque, product.nomEquipe, product.nomSaison]
        .filter(Boolean)
        .join(' ')
        .toLocaleLowerCase('fr');
      const matchesSearch = !normalizedSearch || searchable.includes(normalizedSearch);
      const matchesStatus = statusFilter === 'TOUS' || product.statut === statusFilter;
      const stockLevel = stockLevels.get(product.id) ?? 'OUT_OF_STOCK';
      const matchesStock = stockFilter === 'TOUS'
        || (stockFilter === 'EN_STOCK' ? stockLevel !== 'OUT_OF_STOCK' : stockLevel === 'OUT_OF_STOCK');
      const matchesBrand = brandFilter === 'TOUTES' || product.nomMarque === brandFilter;
      const matchesSeason = seasonFilter === 'TOUTES' || product.nomSaison === seasonFilter;
      const matchesTeam = teamFilter === 'TOUS' || product.nomEquipe === teamFilter;
      return matchesSearch && matchesStatus && matchesStock && matchesBrand && matchesSeason && matchesTeam;
    });

    if (priceSort === 'prix_asc') {
      return [...filtered].sort((a, b) => a.prixTTC - b.prixTTC);
    }
    if (priceSort === 'prix_desc') {
      return [...filtered].sort((a, b) => b.prixTTC - a.prixTTC);
    }
    return filtered;
  }, [brandFilter, priceSort, products, search, seasonFilter, statusFilter, stockFilter, stockLevels, teamFilter]);

  async function selectProduct(id: number) {
    setDetailLoading(true);
    setError(null);
    setSuccess(null);
    setPreviewFailed(false);
    try {
      const detail = await getProduct(request, id);
      setSelectedProduct(detail);
      setForm(formFromProduct(detail));
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Détail du produit indisponible.');
    } finally {
      setDetailLoading(false);
    }
  }

  async function saveProduct(event: FormEvent) {
    event.preventDefault();
    if (!selectedProduct || !form) return;

    const price = Number(form.prixDeBase);
    const weight = form.poids === '' ? undefined : Number(form.poids);
    if (!form.titre.trim()) {
      setError('Le titre est obligatoire.');
      return;
    }
    if (!Number.isFinite(price) || price <= 0) {
      setError('Le prix HT doit être strictement supérieur à zéro.');
      return;
    }
    if (weight !== undefined && (!Number.isFinite(weight) || weight <= 0)) {
      setError('Le poids doit être strictement supérieur à zéro.');
      return;
    }

    const initialPhotoUrl = (selectedProduct.photoUrl ?? '').trim();
    const trimmedPhotoUrl = form.photoUrl.trim();
    const shouldIncludePhotoUrl = trimmedPhotoUrl !== '' || initialPhotoUrl !== '';

    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const updated = await updateProduct(request, selectedProduct.id, {
        couleursPrincipales: form.couleursPrincipales,
        description: form.description,
        estFlocable: form.estFlocable,
        impactEnvi: form.impactEnvi,
        ...(shouldIncludePhotoUrl ? { photoUrl: trimmedPhotoUrl } : {}),
        ...(weight === undefined ? {} : { poids: weight }),
        prixDeBase: price,
        statut: form.statut,
        titre: form.titre.trim(),
      });
      setSelectedProduct(updated);
      setForm(formFromProduct(updated));
      setPreviewFailed(false);
      await loadProducts();
      setSuccess('Produit enregistré avec succès.');
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Enregistrement impossible.');
    } finally {
      setSaving(false);
    }
  }

  const previewUrl = form?.photoUrl.trim() ?? '';
  const showPreviewImage = Boolean(previewUrl) && !previewFailed;

  return (
    <section className="catalogue-page" aria-labelledby="catalogue-title">
      <header className="catalogue-header">
        <div>
          <p className="eyebrow">GESTION COMMERCIALE</p>
          <h1 id="catalogue-title">Catalogue produits</h1>
          <p>{products.length} produit{products.length > 1 ? 's' : ''} chargé{products.length > 1 ? 's' : ''}</p>
        </div>
        <button className="compact-button" type="button" onClick={() => void loadProducts()} disabled={loading}>
          Actualiser
        </button>
      </header>

      <aside className="catalogue-limit" role="note">
        <strong>Création en attente des référentiels Catalogue.</strong>
        <span> L’API ne permet pas encore de lister les marques, saisons, équipes et taux de TVA nécessaires.</span>
      </aside>

      {error && <p className="alert error" role="alert">{error}</p>}
      {success && <p className="alert success" role="status">{success}</p>}

      <div className="catalogue-layout">
        <div className="catalogue-list-panel">
          <div className="catalogue-filters">
            <label className="catalogue-search-field">
              <span>Rechercher</span>
              <input
                type="search"
                placeholder="Produit, marque, équipe…"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
              />
            </label>
            <label>
              <span>Marque</span>
              <select value={brandFilter} onChange={(event) => setBrandFilter(event.target.value)}>
                <option value="TOUTES">Toutes les marques</option>
                {brands.map((brand) => (
                  <option key={brand} value={brand}>{brand}</option>
                ))}
              </select>
            </label>
            <label>
              <span>Saison</span>
              <select value={seasonFilter} onChange={(event) => setSeasonFilter(event.target.value)}>
                <option value="TOUTES">Toutes les saisons</option>
                {seasons.map((season) => (
                  <option key={season} value={season}>{season}</option>
                ))}
              </select>
            </label>
            <label>
              <span>Club / Équipe</span>
              <select value={teamFilter} onChange={(event) => setTeamFilter(event.target.value)}>
                <option value="TOUS">Tous les clubs</option>
                {teams.map((team) => (
                  <option key={team} value={team}>{team}</option>
                ))}
              </select>
            </label>
            <label>
              <span>Statut</span>
              <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as typeof statusFilter)}>
                <option value="TOUS">Tous</option>
                <option value="ACTIF">Actifs</option>
                <option value="DESACTIVE">Désactivés</option>
              </select>
            </label>
            <label>
              <span>Disponibilité</span>
              <select value={stockFilter} onChange={(event) => setStockFilter(event.target.value as typeof stockFilter)}>
                <option value="TOUS">Toutes</option>
                <option value="EN_STOCK">En stock</option>
                <option value="RUPTURE">Rupture</option>
              </select>
            </label>
            <label>
              <span>Tri par prix</span>
              <select value={priceSort} onChange={(event) => setPriceSort(event.target.value as PriceSort)}>
                <option value="default">Tri par défaut</option>
                <option value="prix_asc">Prix croissant</option>
                <option value="prix_desc">Prix décroissant</option>
              </select>
            </label>
            {hasActiveFilters && (
              <div className="filter-reset-cell">
                <button className="secondary-action filter-reset-button" type="button" onClick={resetFilters}>
                  Réinitialiser
                </button>
              </div>
            )}
          </div>

          {loading ? (
            <div className="catalogue-state" aria-busy="true"><div className="loader" />Chargement du catalogue…</div>
          ) : visibleProducts.length === 0 ? (
            <div className="catalogue-state">Aucun produit ne correspond aux filtres.</div>
          ) : (
            <div className="table-scroll">
              <table className="product-table">
                <thead>
                  <tr>
                    <th>Produit</th>
                    <th>Marque</th>
                    <th>Prix TTC</th>
                    <th>Statut</th>
                    <th>Stock</th>
                  </tr>
                </thead>
                <tbody>
                  {visibleProducts.map((product) => {
                    const hasThumb = Boolean(product.photoUrl?.trim()) && !failedImages[product.id];
                    return (
                      <tr
                        className={selectedProduct?.id === product.id ? 'selected' : undefined}
                        key={product.id}
                        onClick={() => void selectProduct(product.id)}
                      >
                        <td>
                          <div className="product-cell">
                            <span className="product-thumb">
                              {hasThumb ? (
                                <img
                                  src={product.photoUrl!}
                                  alt={product.titre}
                                  className="product-thumb-img"
                                  onError={() =>
                                    setFailedImages((current) => ({ ...current, [product.id]: true }))
                                  }
                                />
                              ) : (
                                <span className="product-thumb-fallback" aria-hidden="true">
                                  {(product.nomEquipe ?? product.nomMarque ?? 'OK').slice(0, 3).toUpperCase()}
                                </span>
                              )}
                            </span>
                            <div>
                              <strong>{product.titre}</strong>
                              <small>{product.nomEquipe ?? 'Sans équipe'} · {product.nomSaison ?? 'Sans saison'}</small>
                            </div>
                          </div>
                        </td>
                        <td>{product.nomMarque ?? '—'}</td>
                        <td>{formatPrice(product.prixTTC)}</td>
                        <td><span className={`status-badge ${product.statut.toLowerCase()}`}>{product.statut}</span></td>
                        <td>{stockLabel(stockLevels.get(product.id) ?? 'OUT_OF_STOCK')}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <aside className="product-editor">
          {detailLoading ? (
            <div className="catalogue-state" aria-busy="true"><div className="loader" />Chargement du produit…</div>
          ) : !selectedProduct || !form ? (
            <div className="empty-editor">
              <p className="eyebrow">FICHE PRODUIT</p>
              <h2>Sélectionne un produit</h2>
              <p>Son détail et les champs modifiables apparaîtront ici.</p>
            </div>
          ) : (
            <form onSubmit={saveProduct}>
              <div className="editor-heading">
                <div><p className="eyebrow">PRODUIT #{selectedProduct.id}</p><h2>{selectedProduct.titre}</h2></div>
                <button className="close-button" type="button" aria-label="Fermer la fiche" onClick={() => { setSelectedProduct(null); setForm(null); }}>×</button>
              </div>

              <div className="product-preview">
                {showPreviewImage ? (
                  <img
                    src={previewUrl}
                    alt={selectedProduct.titre}
                    className="product-preview-img"
                    onError={() => setPreviewFailed(true)}
                  />
                ) : (
                  <div className="product-preview-fallback">
                    <strong>{(selectedProduct.equipe?.nom ?? selectedProduct.marque?.nomMarque ?? 'O’KIT').toUpperCase()}</strong>
                    <span>Aucune image renseignée</span>
                  </div>
                )}
              </div>

              <div className="readonly-relations">
                <span><small>Marque</small>{selectedProduct.marque?.nomMarque ?? '—'}</span>
                <span><small>Équipe</small>{selectedProduct.equipe?.nom ?? '—'}</span>
                <span><small>Saison</small>{selectedProduct.saison?.nom ?? '—'}</span>
                <span><small>TVA</small>{selectedProduct.tauxTva} %</span>
              </div>

              <label><span>Titre *</span><input required value={form.titre} onChange={(event) => setForm({ ...form, titre: event.target.value })} /></label>
              <label>
                <span>Image du maillot (URL ou data-URI)</span>
                <input
                  placeholder="https://… ou data:image/png;base64,…"
                  value={form.photoUrl}
                  onChange={(event) => {
                    setPreviewFailed(false);
                    setForm({ ...form, photoUrl: event.target.value });
                  }}
                />
              </label>
              <label><span>Description</span><textarea rows={4} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></label>
              <div className="form-row">
                <label><span>Prix HT (€) *</span><input required min="0.01" step="0.01" type="number" value={form.prixDeBase} onChange={(event) => setForm({ ...form, prixDeBase: event.target.value })} /></label>
                <label><span>Poids (kg)</span><input min="0.001" step="0.001" type="number" value={form.poids} onChange={(event) => setForm({ ...form, poids: event.target.value })} /></label>
              </div>
              <label><span>Couleurs principales</span><input value={form.couleursPrincipales} onChange={(event) => setForm({ ...form, couleursPrincipales: event.target.value })} /></label>
              <label><span>Impact environnemental</span><input value={form.impactEnvi} onChange={(event) => setForm({ ...form, impactEnvi: event.target.value })} /></label>
              <div className="form-row">
                <label><span>Statut</span><select value={form.statut} onChange={(event) => setForm({ ...form, statut: event.target.value as ProductStatus })}><option value="ACTIF">Actif</option><option value="DESACTIVE">Désactivé</option></select></label>
                <label className="checkbox-label"><input type="checkbox" checked={form.estFlocable} onChange={(event) => setForm({ ...form, estFlocable: event.target.checked })} /><span>Flocable</span></label>
              </div>

              <div className="editor-actions">
                <button className="primary-action" disabled={saving} type="submit">{saving ? 'Enregistrement…' : 'Enregistrer'}</button>
              </div>
            </form>
          )}
        </aside>
      </div>
    </section>
  );
}
