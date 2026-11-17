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
import { useAuth } from '../auth/AuthProvider';

type CatalogueState = 'loading' | 'ready' | 'error';

export default function CatalogueScreen() {
  const { accessToken, authenticatedFetch, isReady, logout } = useAuth();
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [state, setState] = useState<CatalogueState>('loading');
  const [message, setMessage] = useState<string | null>(null);

  const loadProducts = useCallback(async () => {
    setState('loading');
    setMessage(null);
    try {
      const page = await getActiveProducts(authenticatedFetch);
      setProducts(page.content);
      setState('ready');
    } catch (catalogueError: unknown) {
      setState('error');
      setMessage(
        catalogueError instanceof Error
          ? catalogueError.message
          : 'Le catalogue est temporairement indisponible.'
      );
    }
  }, [authenticatedFetch]);

  useEffect(() => {
    let active = true;

    if (isReady && accessToken) {
      getActiveProducts(authenticatedFetch)
        .then((page) => {
          if (!active) return;
          setProducts(page.content);
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
  }, [accessToken, authenticatedFetch, isReady]);

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
        <Link href="/commandes" style={styles.navigationLink}>
          Mes commandes
        </Link>
        <Link href="/panier" style={[styles.navigationLink, styles.cartLink]}>
          Mon panier
        </Link>
      </View>

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
            <Link asChild href={`/produit/${product.id}`} key={product.id}>
            <Pressable
              accessibilityRole="button"
              style={({ pressed }) => [styles.card, pressed && styles.cardPressed]}
            >
              <Text style={styles.cardBrand}>{product.nomMarque ?? 'O’kit'}</Text>
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
  cardPressed: { opacity: 0.85, transform: [{ scale: 0.99 }] },
  navigationLink: { color: '#1f2925', fontWeight: '700', backgroundColor: '#fff', padding: 12, borderRadius: 10 },
  statePanel: { backgroundColor: '#fff', borderRadius: 16, padding: 24, alignItems: 'center', gap: 12 },
  stateTitle: { color: '#1f2925', fontSize: 20, fontWeight: '800' },
  stateText: { color: '#655f57', textAlign: 'center' },
  errorText: { color: '#9f3e27', textAlign: 'center' },
  button: { backgroundColor: '#c95f2b', borderRadius: 10, paddingHorizontal: 18, paddingVertical: 12 },
  buttonText: { color: '#fff', fontWeight: '800' },
  grid: { gap: 14 },
  card: { backgroundColor: '#fff', borderRadius: 16, padding: 20, gap: 8 },
  cardBrand: { color: '#c95f2b', fontSize: 12, fontWeight: '800', letterSpacing: 1.5 },
  cardTitle: { color: '#1f2925', fontSize: 21, fontWeight: '800' },
  cardMeta: { color: '#716a61' },
  cardFooter: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 10 },
  price: { color: '#1f2925', fontSize: 20, fontWeight: '800' },
  inStock: { color: '#337357', fontWeight: '700' },
  outOfStock: { color: '#9f3e27', fontWeight: '700' },
});
