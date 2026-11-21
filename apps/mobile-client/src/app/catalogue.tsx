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

import { getActiveProducts, ProductSummary } from '../api/catalogue';
import {
  addWishlistProduct,
  listWishlist,
  removeWishlistProduct,
  WishlistApiError,
} from '../api/wishlist';
import { useAuth } from '../auth/AuthProvider';

type CatalogueState = 'loading' | 'ready' | 'error';

export default function CatalogueScreen() {
  const { accessToken, authenticatedFetch, isReady, logout } = useAuth();
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [state, setState] = useState<CatalogueState>('loading');
  const [message, setMessage] = useState<string | null>(null);
  const [favoriteProductIds, setFavoriteProductIds] = useState<Set<number>>(new Set());
  const [pendingProductIds, setPendingProductIds] = useState<Set<number>>(new Set());

  const loadFavoriteProductIds = useCallback(async () => {
    try {
      const wishlist = await listWishlist(authenticatedFetch);
      return new Set(wishlist.map((item) => item.productId));
    } catch (wishlistError: unknown) {
      if (wishlistError instanceof WishlistApiError && wishlistError.status === 409) {
        return new Set<number>();
      }
      throw wishlistError;
    }
  }, [authenticatedFetch]);

  const loadProducts = useCallback(async () => {
    setState('loading');
    setMessage(null);
    try {
      const [page, wishlist] = await Promise.all([
        getActiveProducts(authenticatedFetch),
        loadFavoriteProductIds(),
      ]);
      setProducts(page.content);
      setFavoriteProductIds(wishlist);
      setState('ready');
    } catch (catalogueError: unknown) {
      setState('error');
      setMessage(
        catalogueError instanceof Error
          ? catalogueError.message
          : 'Le catalogue est temporairement indisponible.'
      );
    }
  }, [authenticatedFetch, loadFavoriteProductIds]);

  const toggleFavorite = useCallback(
    async (productId: number) => {
      if (pendingProductIds.has(productId)) {
        return;
      }

      const wasFavorite = favoriteProductIds.has(productId);
      setPendingProductIds((current) => new Set(current).add(productId));
      setMessage(null);
      try {
        if (wasFavorite) {
          await removeWishlistProduct(authenticatedFetch, productId);
        } else {
          await addWishlistProduct(authenticatedFetch, productId);
        }
        setFavoriteProductIds((current) => {
          const updated = new Set(current);
          if (wasFavorite) {
            updated.delete(productId);
          } else {
            updated.add(productId);
          }
          return updated;
        });
      } catch (wishlistError: unknown) {
        setMessage(
          wishlistError instanceof WishlistApiError && wishlistError.status === 409
            ? 'Complète ton profil avant d’ajouter des favoris.'
            : wishlistError instanceof Error
              ? wishlistError.message
              : 'La wishlist est temporairement indisponible.'
        );
      } finally {
        setPendingProductIds((current) => {
          const updated = new Set(current);
          updated.delete(productId);
          return updated;
        });
      }
    },
    [authenticatedFetch, favoriteProductIds, pendingProductIds]
  );

  useEffect(() => {
    let active = true;

    if (isReady && accessToken) {
      Promise.all([getActiveProducts(authenticatedFetch), loadFavoriteProductIds()])
        .then(([page, wishlist]) => {
          if (!active) return;
          setProducts(page.content);
          setFavoriteProductIds(wishlist);
          setState('ready');
        })
        .catch((catalogueError: unknown) => {
          if (!active) return;
          setState('error');
          setMessage(
            catalogueError instanceof Error
              ? catalogueError.message
              : 'Le catalogue est temporairement indisponible.'
          );
        });
    }

    return () => {
      active = false;
    };
  }, [accessToken, authenticatedFetch, isReady, loadFavoriteProductIds]);

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

  return (
    <ScrollView contentContainerStyle={styles.page}>
      <View style={styles.header}>
        <View>
          <Text style={styles.eyebrow}>O’KIT</Text>
          <Text style={styles.title}>Catalogue</Text>
          <Text style={styles.subtitle}>Les maillots disponibles pour ton équipe.</Text>
        </View>
        <Pressable accessibilityRole="button" onPress={() => void logout()}>
          <Text style={styles.logout}>Déconnexion</Text>
        </Pressable>
      </View>

      <View style={styles.navigation}>
        <Link href="/?profile=1" style={styles.navigationLink}>
          Mon profil
        </Link>
        <Link href="/addresses" style={styles.navigationLink}>
          Mes adresses
        </Link>
        <Link href="/wishlist" style={styles.navigationLink}>
          Mes favoris
        </Link>
        <Link href="/commandes" style={styles.navigationLink}>
          Mes commandes
        </Link>
        <Link href="/panier" style={[styles.navigationLink, styles.cartLink]}>
          Mon panier
        </Link>
      </View>

      {message && state === 'ready' ? (
        <View style={styles.notice}>
          <Text style={styles.errorText}>{message}</Text>
          {message.includes('profil') ? (
            <Link href="/?profile=1" style={styles.noticeLink}>
              Compléter mon profil
            </Link>
          ) : null}
        </View>
      ) : null}

      {state === 'loading' ? (
        <View style={styles.statePanel}>
          <ActivityIndicator color="#c95f2b" />
          <Text style={styles.stateText}>Chargement du catalogue…</Text>
        </View>
      ) : null}

      {state === 'error' ? (
        <View style={styles.statePanel}>
          <Text style={styles.errorText}>{message}</Text>
          <Pressable accessibilityRole="button" onPress={() => void loadProducts()} style={styles.button}>
            <Text style={styles.buttonText}>Réessayer</Text>
          </Pressable>
        </View>
      ) : null}

      {state === 'ready' && products.length === 0 ? (
        <View style={styles.statePanel}>
          <Text style={styles.stateTitle}>Le vestiaire se prépare.</Text>
          <Text style={styles.stateText}>Aucun produit actif n’est disponible pour le moment.</Text>
        </View>
      ) : null}

      {state === 'ready' && products.length > 0 ? (
        <View style={styles.grid}>
          {products.map((product) => (
            <View key={product.id} style={styles.card}>
              <View style={styles.cardHeading}>
                <Text style={styles.cardBrand}>{product.nomMarque ?? 'O’kit'}</Text>
                <Pressable
                  accessibilityLabel={
                    favoriteProductIds.has(product.id)
                      ? `Retirer ${product.titre} des favoris`
                      : `Ajouter ${product.titre} aux favoris`
                  }
                  accessibilityRole="button"
                  disabled={pendingProductIds.has(product.id)}
                  onPress={() => void toggleFavorite(product.id)}
                  style={({ pressed }) => [
                    styles.favoriteButton,
                    favoriteProductIds.has(product.id) && styles.favoriteButtonActive,
                    pressed && styles.favoriteButtonPressed,
                    pendingProductIds.has(product.id) && styles.favoriteButtonDisabled,
                  ]}
                >
                  {pendingProductIds.has(product.id) ? (
                    <ActivityIndicator color="#c95f2b" size="small" />
                  ) : (
                    <Text
                      style={
                        favoriteProductIds.has(product.id)
                          ? styles.favoriteIconActive
                          : styles.favoriteIcon
                      }
                    >
                      {favoriteProductIds.has(product.id) ? '♥' : '♡'}
                    </Text>
                  )}
                </Pressable>
              </View>
              <Link asChild href={`/produit/${product.id}`}>
                <Pressable
                  accessibilityRole="link"
                  style={styles.cardBody}
                >
                  <Text style={styles.cardTitle}>{product.titre}</Text>
                  <Text style={styles.cardMeta}>
                    {[product.nomEquipe, product.nomSaison].filter(Boolean).join(' · ') || 'Collection O’kit'}
                  </Text>
                  <View style={styles.cardFooter}>
                    <Text style={styles.price}>{Number(product.prixTTC).toFixed(2)} €</Text>
                    <Text style={product.enStock ? styles.inStock : styles.outOfStock}>
                      {product.enStock ? 'En stock' : 'Indisponible'}
                    </Text>
                  </View>
                </Pressable>
              </Link>
            </View>
          ))}
        </View>
      ) : null}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  page: { flexGrow: 1, backgroundColor: '#f5f0e8', padding: 24, gap: 20 },
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: '#f5f0e8' },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16 },
  eyebrow: { color: '#c95f2b', fontWeight: '800', letterSpacing: 2 },
  title: { color: '#1f2925', fontSize: 38, fontWeight: '800' },
  subtitle: { color: '#655f57', marginTop: 6 },
  logout: { color: '#9f3e27', fontWeight: '700', paddingVertical: 8 },
  navigation: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  cartLink: { backgroundColor: '#c95f2b', color: '#fff' },
  cardBody: { gap: 8 },
  navigationLink: { color: '#1f2925', fontWeight: '700', backgroundColor: '#fff', padding: 12, borderRadius: 10 },
  statePanel: { backgroundColor: '#fff', borderRadius: 16, padding: 24, alignItems: 'center', gap: 12 },
  stateTitle: { color: '#1f2925', fontSize: 20, fontWeight: '800' },
  stateText: { color: '#655f57', textAlign: 'center' },
  errorText: { color: '#9f3e27', textAlign: 'center' },
  notice: { backgroundColor: '#fff1e8', borderRadius: 14, padding: 16, alignItems: 'center', gap: 8 },
  noticeLink: { color: '#9f3e27', fontWeight: '800', textDecorationLine: 'underline' },
  button: { backgroundColor: '#c95f2b', borderRadius: 10, paddingHorizontal: 18, paddingVertical: 12 },
  buttonText: { color: '#fff', fontWeight: '800' },
  grid: { gap: 14 },
  card: { backgroundColor: '#fff', borderRadius: 16, padding: 20, gap: 8 },
  cardHeading: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 },
  cardBrand: { color: '#c95f2b', fontSize: 12, fontWeight: '800', letterSpacing: 1.5 },
  favoriteButton: { alignItems: 'center', borderColor: '#d9cec0', borderRadius: 22, borderWidth: 1, height: 44, justifyContent: 'center', width: 44 },
  favoriteButtonActive: { backgroundColor: '#fff0e8', borderColor: '#c95f2b' },
  favoriteButtonPressed: { opacity: 0.72, transform: [{ scale: 0.96 }] },
  favoriteButtonDisabled: { opacity: 0.55 },
  favoriteIcon: { color: '#655f57', fontSize: 25, lineHeight: 29 },
  favoriteIconActive: { color: '#c95f2b', fontSize: 23, lineHeight: 29 },
  cardTitle: { color: '#1f2925', fontSize: 21, fontWeight: '800' },
  cardMeta: { color: '#716a61' },
  cardFooter: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 10 },
  price: { color: '#1f2925', fontSize: 20, fontWeight: '800' },
  inStock: { color: '#337357', fontWeight: '700' },
  outOfStock: { color: '#9f3e27', fontWeight: '700' },
});
