import { useCallback, useEffect, useState } from 'react';
import { Link, Redirect } from 'expo-router';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';

import { getProductById, ProductDetail } from '../api/catalogue';
import {
  aggregateProductStockLevel,
  getStockStatusMap,
  ProductStockLevel,
} from '../api/stock';
import {
  listWishlist,
  removeWishlistProduct,
  WishlistItem,
} from '../api/wishlist';
import { useAuth } from '../auth/AuthProvider';

type WishlistProduct = {
  item: WishlistItem;
  product: ProductDetail | null;
};

type WishlistState = 'loading' | 'ready' | 'error';

export default function WishlistScreen() {
  const { accessToken, authenticatedFetch, isReady } = useAuth();
  const [products, setProducts] = useState<WishlistProduct[]>([]);
  const [stockLevels, setStockLevels] = useState<Record<number, ProductStockLevel>>({});
  const [state, setState] = useState<WishlistState>('loading');
  const [message, setMessage] = useState<string | null>(null);
  const [pendingProductIds, setPendingProductIds] = useState<Set<number>>(new Set());

  // Même logique que le Catalogue : on agrège le stock réel de ms-stock par produit.
  const loadStockLevels = useCallback(
    async (enriched: WishlistProduct[]) => {
      const varianteIds = enriched.flatMap(
        ({ product }) => product?.variantes.map((variante) => variante.id) ?? []
      );
      if (varianteIds.length === 0) {
        return {};
      }
      try {
        const statuses = await getStockStatusMap(authenticatedFetch, varianteIds);
        const levels: Record<number, ProductStockLevel> = {};
        for (const { product } of enriched) {
          if (!product) continue;
          levels[product.id] = aggregateProductStockLevel(
            product.variantes.map((variante) => variante.id),
            statuses
          );
        }
        return levels;
      } catch {
        return {};
      }
    },
    [authenticatedFetch]
  );

  const fetchWishlist = useCallback(async () => {
    const items = await listWishlist(authenticatedFetch);
    const enrichedProducts = await Promise.all(
      items.map(async (item) => ({
        item,
        product: await getProductById(authenticatedFetch, item.productId),
      }))
    );
    const levels = await loadStockLevels(enrichedProducts);
    return { enrichedProducts, levels };
  }, [authenticatedFetch, loadStockLevels]);

  const loadWishlist = useCallback(async () => {
    setState('loading');
    setMessage(null);
    try {
      const { enrichedProducts, levels } = await fetchWishlist();
      setProducts(enrichedProducts);
      setStockLevels(levels);
      setState('ready');
    } catch (wishlistError: unknown) {
      setState('error');
      setMessage(
        wishlistError instanceof Error
          ? wishlistError.message
          : 'Tes favoris sont temporairement indisponibles.'
      );
    }
  }, [fetchWishlist]);

  useEffect(() => {
    if (!isReady || !accessToken) {
      return;
    }
    let active = true;

    fetchWishlist()
      .then(({ enrichedProducts, levels }) => {
        if (!active) return;
        setProducts(enrichedProducts);
        setStockLevels(levels);
        setState('ready');
      })
      .catch((wishlistError: unknown) => {
        if (!active) return;
        setState('error');
        setMessage(
          wishlistError instanceof Error
            ? wishlistError.message
            : 'Tes favoris sont temporairement indisponibles.'
        );
      });

    return () => {
      active = false;
    };
  }, [accessToken, fetchWishlist, isReady]);

  if (!isReady) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator color="#c95f2b" size="large" />
      </View>
    );
  }

  if (!accessToken) {
    return <Redirect href="/" />;
  }

  async function removeFavorite(productId: number) {
    if (pendingProductIds.has(productId)) {
      return;
    }

    setPendingProductIds((current) => new Set(current).add(productId));
    setMessage(null);
    try {
      await removeWishlistProduct(authenticatedFetch, productId);
      setProducts((current) =>
        current.filter(({ item }) => item.productId !== productId)
      );
    } catch (removeError: unknown) {
      setMessage(
        removeError instanceof Error
          ? removeError.message
          : 'Le favori n’a pas pu être retiré.'
      );
    } finally {
      setPendingProductIds((current) => {
        const updated = new Set(current);
        updated.delete(productId);
        return updated;
      });
    }
  }

  return (
    <ScrollView contentContainerStyle={styles.page}>
      <View style={styles.container}>
        <View style={styles.header}>
          <View style={styles.headerCopy}>
            <Text style={styles.eyebrow}>COMPTE ACHETEUR</Text>
            <Text style={styles.title}>Mes favoris</Text>
            <Text style={styles.subtitle}>
              Retrouve les produits que tu veux garder sous la main.
            </Text>
          </View>
          <View style={styles.navigation}>
            <Link href="/catalogue" style={styles.navigationLink}>
              Catalogue
            </Link>
            <Link href="/?profile=1" style={styles.navigationLink}>
              Mon profil
            </Link>
            <Link href="/addresses" style={styles.navigationLink}>
              Mes adresses
            </Link>
          </View>
        </View>

        {message && state === 'ready' ? (
          <View style={styles.notice}>
            <Text style={styles.errorText}>{message}</Text>
          </View>
        ) : null}

        {state === 'loading' ? (
          <View style={styles.statePanel}>
            <ActivityIndicator color="#c95f2b" />
            <Text style={styles.stateText}>Chargement de tes favoris…</Text>
          </View>
        ) : null}

        {state === 'error' ? (
          <View style={styles.statePanel}>
            <Text style={styles.errorText}>{message}</Text>
            <Pressable
              accessibilityRole="button"
              onPress={() => void loadWishlist()}
              style={styles.primaryButton}
            >
              <Text style={styles.primaryButtonText}>Réessayer</Text>
            </Pressable>
          </View>
        ) : null}

        {state === 'ready' && products.length === 0 ? (
          <View style={styles.statePanel}>
            <Text style={styles.stateTitle}>Ta wishlist est vide.</Text>
            <Text style={styles.stateText}>
              Explore le Catalogue et touche le cœur d’un produit pour l’ajouter.
            </Text>
            <Link href="/catalogue" style={styles.catalogueLink}>
              Découvrir le catalogue
            </Link>
          </View>
        ) : null}

        {state === 'ready' && products.length > 0 ? (
          <View style={styles.grid}>
            {products.map(({ item, product }) => {
              const unavailable = !product || product.statut === 'DESACTIVE';
              const stockLevel = product
                ? stockLevels[product.id] ?? (product.enStock ? 'IN_STOCK' : 'OUT_OF_STOCK')
                : 'OUT_OF_STOCK';
              return (
                <View key={item.productId} style={styles.card}>
                  <View style={styles.cardHeading}>
                    <View style={styles.cardCopy}>
                      <Text style={styles.cardBrand}>
                        {product?.marque?.nomMarque ?? 'O’kit'}
                      </Text>
                      <Text style={styles.cardTitle}>
                        {product?.titre ?? `Produit #${item.productId}`}
                      </Text>
                    </View>
                    <Text
                      style={
                        unavailable || stockLevel === 'OUT_OF_STOCK'
                          ? styles.unavailable
                          : stockLevel === 'LOW_STOCK'
                            ? styles.lowStock
                            : styles.available
                      }
                    >
                      {unavailable
                        ? 'Indisponible'
                        : stockLevel === 'OUT_OF_STOCK'
                          ? 'Rupture de stock'
                          : stockLevel === 'LOW_STOCK'
                            ? 'Derniers exemplaires'
                            : 'En stock'}
                    </Text>
                  </View>

                  {product ? (
                    <>
                      <Text style={styles.cardMeta}>
                        {[product.equipe?.nom, product.saison?.nom]
                          .filter(Boolean)
                          .join(' · ') || 'Collection O’kit'}
                      </Text>
                      <Text style={styles.price}>
                        {Number(product.prixApresPromotionTTC ?? product.prixTTC).toFixed(2)} €
                      </Text>
                    </>
                  ) : (
                    <Text style={styles.cardMeta}>
                      Le Catalogue ne retrouve plus ce produit. Tu peux le retirer de tes favoris.
                    </Text>
                  )}

                  <View style={styles.cardFooter}>
                    <Text style={styles.addedAt}>
                      Ajouté le {formatDate(item.addedAt)}
                    </Text>
                    <Pressable
                      accessibilityLabel={`Retirer ${product?.titre ?? `le produit ${item.productId}`} des favoris`}
                      accessibilityRole="button"
                      disabled={pendingProductIds.has(item.productId)}
                      onPress={() => void removeFavorite(item.productId)}
                      style={({ pressed }) => [
                        styles.removeButton,
                        pressed && styles.pressed,
                        pendingProductIds.has(item.productId) && styles.disabled,
                      ]}
                    >
                      {pendingProductIds.has(item.productId) ? (
                        <ActivityIndicator color="#9f3e27" size="small" />
                      ) : (
                        <Text style={styles.removeButtonText}>Retirer</Text>
                      )}
                    </Pressable>
                  </View>
                </View>
              );
            })}
          </View>
        ) : null}
      </View>
    </ScrollView>
  );
}

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? 'date inconnue' : date.toLocaleDateString('fr-FR');
}

const styles = StyleSheet.create({
  page: { backgroundColor: '#f5f0e8', flexGrow: 1, padding: 24 },
  container: { alignSelf: 'center', gap: 20, maxWidth: 980, width: '100%' },
  centered: { alignItems: 'center', backgroundColor: '#f5f0e8', flex: 1, justifyContent: 'center' },
  header: { alignItems: 'flex-start', flexDirection: 'row', flexWrap: 'wrap', gap: 20, justifyContent: 'space-between' },
  headerCopy: { flex: 1, minWidth: 260 },
  eyebrow: { color: '#c95f2b', fontSize: 12, fontWeight: '900', letterSpacing: 2 },
  title: { color: '#1f2925', fontSize: 38, fontWeight: '800' },
  subtitle: { color: '#655f57', marginTop: 6 },
  navigation: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  navigationLink: { backgroundColor: '#fff', borderRadius: 10, color: '#1f2925', fontWeight: '700', padding: 12 },
  notice: { backgroundColor: '#fff1e8', borderRadius: 14, padding: 16 },
  statePanel: { alignItems: 'center', backgroundColor: '#fff', borderRadius: 16, gap: 12, padding: 28 },
  stateTitle: { color: '#1f2925', fontSize: 21, fontWeight: '800' },
  stateText: { color: '#655f57', textAlign: 'center' },
  errorText: { color: '#9f3e27', textAlign: 'center' },
  primaryButton: { backgroundColor: '#c95f2b', borderRadius: 10, paddingHorizontal: 18, paddingVertical: 12 },
  primaryButtonText: { color: '#fff', fontWeight: '800' },
  catalogueLink: { color: '#9f3e27', fontWeight: '800', textDecorationLine: 'underline' },
  grid: { gap: 14 },
  card: { backgroundColor: '#fff', borderRadius: 16, gap: 10, padding: 20 },
  cardHeading: { alignItems: 'flex-start', flexDirection: 'row', gap: 14, justifyContent: 'space-between' },
  cardCopy: { flex: 1 },
  cardBrand: { color: '#c95f2b', fontSize: 12, fontWeight: '800', letterSpacing: 1.5 },
  cardTitle: { color: '#1f2925', fontSize: 21, fontWeight: '800', marginTop: 4 },
  cardMeta: { color: '#716a61', lineHeight: 20 },
  price: { color: '#1f2925', fontSize: 20, fontWeight: '800' },
  available: { color: '#337357', fontWeight: '700' },
  lowStock: { color: '#c95f2b', fontWeight: '700' },
  unavailable: { color: '#9f3e27', fontWeight: '700' },
  cardFooter: { alignItems: 'center', flexDirection: 'row', flexWrap: 'wrap', gap: 12, justifyContent: 'space-between', marginTop: 8 },
  addedAt: { color: '#81786e', fontSize: 12 },
  removeButton: { alignItems: 'center', borderColor: '#d7b7aa', borderRadius: 10, borderWidth: 1, justifyContent: 'center', minHeight: 42, minWidth: 92, paddingHorizontal: 14 },
  removeButtonText: { color: '#9f3e27', fontWeight: '800' },
  pressed: { opacity: 0.72, transform: [{ scale: 0.98 }] },
  disabled: { opacity: 0.5 },
});
