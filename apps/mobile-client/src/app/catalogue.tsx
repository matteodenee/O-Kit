import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, Redirect } from 'expo-router';
import {
  ActivityIndicator,
  Image,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';

import { getActiveProducts, PriceSort, ProductSummary } from '../api/catalogue';
import {
  aggregateProductStockLevel,
  getStockStatusMap,
  ProductStockLevel,
} from '../api/stock';
import {
  addWishlistProduct,
  listWishlist,
  removeWishlistProduct,
  WishlistApiError,
} from '../api/wishlist';
import { useAuth } from '../auth/AuthProvider';

type CatalogueState = 'loading' | 'ready' | 'error';

const PRICE_SORT_OPTIONS: { label: string; value: PriceSort }[] = [
  { label: 'Par défaut', value: 'default' },
  { label: 'Prix croissant', value: 'prix_asc' },
  { label: 'Prix décroissant', value: 'prix_desc' },
];

function uniqueSorted(values: (string | null | undefined)[]): string[] {
  return Array.from(
      new Set(
          values
              .map((value) => value?.trim())
              .filter((value): value is string => Boolean(value))
      )
  ).sort((a, b) => a.localeCompare(b, 'fr'));
}

export default function CatalogueScreen() {
  const { accessToken, authenticatedFetch, isReady, logout } = useAuth();
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [stockLevels, setStockLevels] = useState<Record<number, ProductStockLevel>>({});
  const [state, setState] = useState<CatalogueState>('loading');
  const [message, setMessage] = useState<string | null>(null);
  const [favoriteProductIds, setFavoriteProductIds] = useState<Set<number>>(new Set());
  const [pendingProductIds, setPendingProductIds] = useState<Set<number>>(new Set());
  const [selectedBrand, setSelectedBrand] = useState<string | null>(null);
  const [selectedSeason, setSelectedSeason] = useState<string | null>(null);
  const [selectedTeam, setSelectedTeam] = useState<string | null>(null);
  const [priceSort, setPriceSort] = useState<PriceSort>('default');
  const [failedImages, setFailedImages] = useState<Record<number, boolean>>({});

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

  // Le stock réel vit dans ms-stock : on agrège les statuts de toutes les tailles
  // en un seul appel. En cas d'indisponibilité, on retombe sur le drapeau Catalogue.
  const loadStockLevels = useCallback(
      async (page: ProductSummary[]) => {
        const varianteIds = page.flatMap((product) => product.varianteIds ?? []);
        if (varianteIds.length === 0) {
          return {};
        }
        try {
          const statuses = await getStockStatusMap(authenticatedFetch, varianteIds);
          const levels: Record<number, ProductStockLevel> = {};
          for (const product of page) {
            const ids = product.varianteIds ?? [];
            levels[product.id] =
                ids.length === 0
                    ? product.enStock
                        ? 'IN_STOCK'
                        : 'OUT_OF_STOCK'
                    : aggregateProductStockLevel(ids, statuses);
          }
          return levels;
        } catch {
          return {};
        }
      },
      [authenticatedFetch]
  );

  const fetchCatalogue = useCallback(async () => {
    const [page, wishlist] = await Promise.all([
      getActiveProducts(authenticatedFetch),
      loadFavoriteProductIds(),
    ]);
    const levels = await loadStockLevels(page.content);
    return { page, wishlist, levels };
  }, [authenticatedFetch, loadFavoriteProductIds, loadStockLevels]);

  const loadProducts = useCallback(async () => {
    setState('loading');
    setMessage(null);
    try {
      const { page, wishlist, levels } = await fetchCatalogue();
      setProducts(page.content);
      setFavoriteProductIds(wishlist);
      setStockLevels(levels);
      setFailedImages({});
      setState('ready');
    } catch (catalogueError: unknown) {
      setState('error');
      setMessage(
          catalogueError instanceof Error
              ? catalogueError.message
              : 'Le catalogue est temporairement indisponible.'
      );
    }
  }, [fetchCatalogue]);

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
    if (!isReady || !accessToken) {
      return;
    }
    let active = true;

    fetchCatalogue()
        .then(({ page, wishlist, levels }) => {
          if (!active) return;
          setProducts(page.content);
          setFavoriteProductIds(wishlist);
          setStockLevels(levels);
          setFailedImages({});
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

    return () => {
      active = false;
    };
  }, [accessToken, fetchCatalogue, isReady]);

  const brands = useMemo(
      () => uniqueSorted(products.map((product) => product.nomMarque)),
      [products]
  );
  const seasons = useMemo(
      () => uniqueSorted(products.map((product) => product.nomSaison)),
      [products]
  );
  const teams = useMemo(
      () => uniqueSorted(products.map((product) => product.nomEquipe)),
      [products]
  );

  const hasActiveFilters =
      selectedBrand !== null ||
      selectedSeason !== null ||
      selectedTeam !== null ||
      priceSort !== 'default';

  const resetFilters = useCallback(() => {
    setSelectedBrand(null);
    setSelectedSeason(null);
    setSelectedTeam(null);
    setPriceSort('default');
  }, []);

  const displayedProducts = useMemo(() => {
    const filtered = products.filter((product) => {
      if (selectedBrand && product.nomMarque !== selectedBrand) return false;
      if (selectedSeason && product.nomSaison !== selectedSeason) return false;
      if (selectedTeam && product.nomEquipe !== selectedTeam) return false;
      return true;
    });

    if (priceSort === 'prix_asc') {
      return [...filtered].sort((a, b) => Number(a.prixTTC) - Number(b.prixTTC));
    }
    if (priceSort === 'prix_desc') {
      return [...filtered].sort((a, b) => Number(b.prixTTC) - Number(a.prixTTC));
    }
    return filtered;
  }, [priceSort, products, selectedBrand, selectedSeason, selectedTeam]);

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
            <>
              <View style={styles.filtersPanel}>
                <View style={styles.filtersHeader}>
                  <Text style={styles.filtersHeading}>Filtrer & trier</Text>
                  <Text style={styles.resultsCount}>
                    {displayedProducts.length} maillot{displayedProducts.length > 1 ? 's' : ''}
                  </Text>
                </View>

                <View style={styles.filterGroup}>
                  <Text style={styles.filterLabel}>Marque</Text>
                  <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipsRow}>
                    <Pressable
                        accessibilityRole="button"
                        onPress={() => setSelectedBrand(null)}
                        style={[styles.chip, selectedBrand === null && styles.chipActive]}
                    >
                      <Text style={[styles.chipText, selectedBrand === null && styles.chipTextActive]}>Toutes</Text>
                    </Pressable>
                    {brands.map((brand) => {
                      const active = selectedBrand === brand;
                      return (
                          <Pressable
                              key={brand}
                              accessibilityRole="button"
                              onPress={() => setSelectedBrand(active ? null : brand)}
                              style={[styles.chip, active && styles.chipActive]}
                          >
                            <Text style={[styles.chipText, active && styles.chipTextActive]}>{brand}</Text>
                          </Pressable>
                      );
                    })}
                  </ScrollView>
                </View>

                <View style={styles.filterGroup}>
                  <Text style={styles.filterLabel}>Saison</Text>
                  <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipsRow}>
                    <Pressable
                        accessibilityRole="button"
                        onPress={() => setSelectedSeason(null)}
                        style={[styles.chip, selectedSeason === null && styles.chipActive]}
                    >
                      <Text style={[styles.chipText, selectedSeason === null && styles.chipTextActive]}>Toutes</Text>
                    </Pressable>
                    {seasons.map((season) => {
                      const active = selectedSeason === season;
                      return (
                          <Pressable
                              key={season}
                              accessibilityRole="button"
                              onPress={() => setSelectedSeason(active ? null : season)}
                              style={[styles.chip, active && styles.chipActive]}
                          >
                            <Text style={[styles.chipText, active && styles.chipTextActive]}>{season}</Text>
                          </Pressable>
                      );
                    })}
                  </ScrollView>
                </View>

                <View style={styles.filterGroup}>
                  <Text style={styles.filterLabel}>Club / Équipe</Text>
                  <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipsRow}>
                    <Pressable
                        accessibilityRole="button"
                        onPress={() => setSelectedTeam(null)}
                        style={[styles.chip, selectedTeam === null && styles.chipActive]}
                    >
                      <Text style={[styles.chipText, selectedTeam === null && styles.chipTextActive]}>Tous</Text>
                    </Pressable>
                    {teams.map((team) => {
                      const active = selectedTeam === team;
                      return (
                          <Pressable
                              key={team}
                              accessibilityRole="button"
                              onPress={() => setSelectedTeam(active ? null : team)}
                              style={[styles.chip, active && styles.chipActive]}
                          >
                            <Text style={[styles.chipText, active && styles.chipTextActive]}>{team}</Text>
                          </Pressable>
                      );
                    })}
                  </ScrollView>
                </View>

                <View style={styles.filterGroup}>
                  <Text style={styles.filterLabel}>Tri par prix</Text>
                  <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipsRow}>
                    {PRICE_SORT_OPTIONS.map((option) => {
                      const active = priceSort === option.value;
                      return (
                          <Pressable
                              key={option.value}
                              accessibilityRole="button"
                              onPress={() => setPriceSort(option.value)}
                              style={[styles.chip, active && styles.chipActive]}
                          >
                            <Text style={[styles.chipText, active && styles.chipTextActive]}>{option.label}</Text>
                          </Pressable>
                      );
                    })}
                  </ScrollView>
                </View>

                {hasActiveFilters ? (
                    <Pressable accessibilityRole="button" onPress={resetFilters} style={styles.resetButton}>
                      <Text style={styles.resetButtonText}>Réinitialiser les filtres</Text>
                    </Pressable>
                ) : null}
              </View>

              {displayedProducts.length === 0 ? (
                  <View style={styles.statePanel}>
                    <Text style={styles.stateTitle}>Aucun maillot trouvé</Text>
                    <Text style={styles.stateText}>Aucun produit actif ne correspond aux filtres sélectionnés.</Text>
                    <Pressable accessibilityRole="button" onPress={resetFilters} style={styles.button}>
                      <Text style={styles.buttonText}>Réinitialiser les filtres</Text>
                    </Pressable>
                  </View>
              ) : (
                  <View style={styles.grid}>
                    {displayedProducts.map((product) => {
                      const stockLevel =
                          stockLevels[product.id] ?? (product.enStock ? 'IN_STOCK' : 'OUT_OF_STOCK');
                      const hasImage = Boolean(product.photoUrl?.trim()) && !failedImages[product.id];
                      return (
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
                              <Pressable accessibilityRole="link" style={styles.cardBody}>
                                <View style={[styles.cardMedia, !hasImage && styles.cardMediaFallback]}>
                                  {hasImage ? (
                                      <Image
                                          source={{ uri: product.photoUrl! }}
                                          style={styles.cardImage}
                                          resizeMode="contain"
                                          onError={() =>
                                              setFailedImages((current) => ({ ...current, [product.id]: true }))
                                          }
                                      />
                                  ) : (
                                      <View style={styles.jerseyFallback}>
                                        <Text style={styles.jerseyFallbackClub}>
                                          {(product.nomEquipe ?? product.nomMarque ?? 'O’KIT').toUpperCase()}
                                        </Text>
                                        <Text style={styles.jerseyFallbackCaption}>Visuel bientôt disponible</Text>
                                      </View>
                                  )}
                                </View>
                                <Text style={styles.cardTitle}>{product.titre}</Text>
                                <Text style={styles.cardMeta}>
                                  {[product.nomEquipe, product.nomSaison].filter(Boolean).join(' · ') || 'Collection O’kit'}
                                </Text>
                                <View style={styles.cardFooter}>
                                  <Text style={styles.price}>{Number(product.prixTTC).toFixed(2)} €</Text>
                                  <Text
                                      style={
                                        stockLevel === 'OUT_OF_STOCK'
                                            ? styles.outOfStock
                                            : stockLevel === 'LOW_STOCK'
                                                ? styles.lowStock
                                                : styles.inStock
                                      }
                                  >
                                    {stockLabel(stockLevel)}
                                  </Text>
                                </View>
                              </Pressable>
                            </Link>
                          </View>
                      );
                    })}
                  </View>
              )}
            </>
        ) : null}
      </ScrollView>
  );
}

function stockLabel(level: ProductStockLevel) {
  if (level === 'OUT_OF_STOCK') return 'Indisponible';
  if (level === 'LOW_STOCK') return 'Derniers exemplaires';
  return 'En stock';
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
  filtersPanel: { backgroundColor: '#fff', borderRadius: 16, padding: 16, gap: 14 },
  filtersHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  filtersHeading: { color: '#1f2925', fontSize: 16, fontWeight: '800' },
  resultsCount: { color: '#655f57', fontSize: 13, fontWeight: '700' },
  filterGroup: { gap: 8 },
  filterLabel: { color: '#655f57', fontSize: 12, fontWeight: '800', textTransform: 'uppercase', letterSpacing: 0.8 },
  chipsRow: { flexDirection: 'row', gap: 8, paddingVertical: 2 },
  chip: {
    backgroundColor: '#f5f0e8',
    borderRadius: 999,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderWidth: 1,
    borderColor: '#e4ddd2',
  },
  chipActive: { backgroundColor: '#c95f2b', borderColor: '#c95f2b' },
  chipText: { color: '#1f2925', fontSize: 13, fontWeight: '700' },
  chipTextActive: { color: '#fff' },
  resetButton: {
    alignSelf: 'flex-start',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 10,
    backgroundColor: '#f5f0e8',
    borderWidth: 1,
    borderColor: '#c95f2b',
  },
  resetButtonText: { color: '#c95f2b', fontSize: 13, fontWeight: '800' },
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
  cardMedia: {
    width: '100%',
    height: 180,
    borderRadius: 12,
    backgroundColor: '#f5f0e8',
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
    marginBottom: 6,
    padding: 12,
  },
  cardMediaFallback: {
    borderWidth: 1,
    borderColor: '#e4ddd2',
  },
  cardImage: { width: '100%', height: '100%' },
  jerseyFallback: { alignItems: 'center', gap: 6 },
  jerseyFallbackClub: { color: '#1f2925', fontSize: 15, fontWeight: '800', letterSpacing: 1.2, textAlign: 'center' },
  jerseyFallbackCaption: { color: '#655f57', fontSize: 12, fontWeight: '600' },
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
  lowStock: { color: '#c95f2b', fontWeight: '700' },
  outOfStock: { color: '#9f3e27', fontWeight: '700' },
});