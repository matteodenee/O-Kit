import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';

import {
  type CatalogueRequest,
  type ProductDetail,
  type ProductStatus,
  type ProductSummary,
  getProduct,
  listAllProducts,
  updateProduct,
  updateProductStock,
} from '../api/catalogue';

type CatalogueManagerProps = {
  request: CatalogueRequest;
};

type ProductForm = {
  couleursPrincipales: string;
  description: string;
  estFlocable: boolean;
  impactEnvi: string;
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

export default function CatalogueManager({ request }: CatalogueManagerProps) {
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [selectedProduct, setSelectedProduct] = useState<ProductDetail | null>(null);
  const [form, setForm] = useState<ProductForm | null>(null);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<'TOUS' | ProductStatus>('TOUS');
  const [stockFilter, setStockFilter] = useState<'TOUS' | 'EN_STOCK' | 'RUPTURE'>('TOUS');
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const loadProducts = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setProducts(await listAllProducts(request));
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Chargement du catalogue impossible.');
    } finally {
      setLoading(false);
    }
  }, [request]);

  useEffect(() => {
    let active = true;
    listAllProducts(request)
      .then((loadedProducts) => {
        if (active) setProducts(loadedProducts);
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
  }, [request]);

  const visibleProducts = useMemo(() => {
    const normalizedSearch = search.trim().toLocaleLowerCase('fr');
    return products.filter((product) => {
      const searchable = [product.titre, product.nomMarque, product.nomEquipe, product.nomSaison]
        .filter(Boolean)
        .join(' ')
        .toLocaleLowerCase('fr');
      const matchesSearch = !normalizedSearch || searchable.includes(normalizedSearch);
      const matchesStatus = statusFilter === 'TOUS' || product.statut === statusFilter;
      const matchesStock = stockFilter === 'TOUS'
        || (stockFilter === 'EN_STOCK' ? product.enStock : !product.enStock);
      return matchesSearch && matchesStatus && matchesStock;
    });
  }, [products, search, statusFilter, stockFilter]);

  async function selectProduct(id: number) {
    setDetailLoading(true);
    setError(null);
    setSuccess(null);
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

    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const updated = await updateProduct(request, selectedProduct.id, {
        couleursPrincipales: form.couleursPrincipales,
        description: form.description,
        estFlocable: form.estFlocable,
        impactEnvi: form.impactEnvi,
        ...(weight === undefined ? {} : { poids: weight }),
        prixDeBase: price,
        statut: form.statut,
        titre: form.titre.trim(),
      });
      setSelectedProduct(updated);
      setForm(formFromProduct(updated));
      await loadProducts();
      setSuccess('Produit enregistré avec succès.');
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Enregistrement impossible.');
    } finally {
      setSaving(false);
    }
  }

  async function toggleStock() {
    if (!selectedProduct) return;
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      await updateProductStock(request, selectedProduct.id, !selectedProduct.enStock);
      const refreshed = await getProduct(request, selectedProduct.id);
      setSelectedProduct(refreshed);
      setForm(formFromProduct(refreshed));
      await loadProducts();
      setSuccess(refreshed.enStock ? 'Produit marqué disponible.' : 'Produit marqué en rupture.');
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Mise à jour du stock impossible.');
    } finally {
      setSaving(false);
    }
  }

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
            <label>
              <span>Rechercher</span>
              <input
                type="search"
                placeholder="Produit, marque, équipe…"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
              />
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
                  {visibleProducts.map((product) => (
                    <tr
                      className={selectedProduct?.id === product.id ? 'selected' : undefined}
                      key={product.id}
                      onClick={() => void selectProduct(product.id)}
                    >
                      <td><strong>{product.titre}</strong><small>{product.nomEquipe ?? 'Sans équipe'} · {product.nomSaison ?? 'Sans saison'}</small></td>
                      <td>{product.nomMarque ?? '—'}</td>
                      <td>{formatPrice(product.prixTTC)}</td>
                      <td><span className={`status-badge ${product.statut.toLowerCase()}`}>{product.statut}</span></td>
                      <td>{product.enStock ? 'Disponible' : 'Rupture'}</td>
                    </tr>
                  ))}
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

              <div className="readonly-relations">
                <span><small>Marque</small>{selectedProduct.marque?.nomMarque ?? '—'}</span>
                <span><small>Équipe</small>{selectedProduct.equipe?.nom ?? '—'}</span>
                <span><small>Saison</small>{selectedProduct.saison?.nom ?? '—'}</span>
                <span><small>TVA</small>{selectedProduct.tauxTva} %</span>
              </div>

              <label><span>Titre *</span><input required value={form.titre} onChange={(event) => setForm({ ...form, titre: event.target.value })} /></label>
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
                <button className="secondary-action" disabled={saving} type="button" onClick={() => void toggleStock()}>{selectedProduct.enStock ? 'Marquer en rupture' : 'Marquer disponible'}</button>
              </div>
            </form>
          )}
        </aside>
      </div>
    </section>
  );
}
