import { Link, Redirect, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';

import { Commande, getCommandes } from '../../api/commande';
import { useAuth } from '../../auth/AuthProvider';
import { BoutonLien } from '../../commande/BoutonLien';
import { couleurs, couleurStatut, formatPrix, libelleStatut, ui } from '../../commande/ui';

export default function CommandesScreen() {
  const { accessToken, authenticatedFetch, isReady } = useAuth();
  const [commandes, setCommandes] = useState<Commande[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  /** « Tirer pour actualiser » : récupère les statuts mis à jour par le vendeur. */
  async function actualiser() {
    setRefreshing(true);
    try {
      setCommandes(await getCommandes(authenticatedFetch));
      setError(null);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Tes commandes sont indisponibles.');
    } finally {
      setRefreshing(false);
    }
  }

  useFocusEffect(
    useCallback(() => {
      if (!isReady || !accessToken) return;
      let active = true;
      setError(null);
      getCommandes(authenticatedFetch)
        .then((loaded) => {
          if (active) setCommandes(loaded);
        })
        .catch((loadError: unknown) => {
          if (active) setError(loadError instanceof Error ? loadError.message : 'Tes commandes sont indisponibles.');
        })
        .finally(() => {
          if (active) setLoading(false);
        });
      return () => {
        active = false;
      };
    }, [accessToken, authenticatedFetch, isReady])
  );

  if (!isReady) {
    return (
      <View style={ui.loadingPage}>
        <ActivityIndicator color={couleurs.accent} size="large" />
      </View>
    );
  }

  if (!accessToken) {
    return <Redirect href="/" />;
  }

  return (
    <ScrollView
      contentContainerStyle={ui.page}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void actualiser()} />}
    >
      <View style={ui.container}>
        <View style={ui.header}>
          <View style={ui.headerCopy}>
            <Text style={ui.eyebrow}>MON COMPTE</Text>
            <Text style={ui.title}>Mes commandes</Text>
            <Text style={ui.subtitle}>Suis tes maillots, de la validation à la livraison.</Text>
          </View>
          <View style={ui.headerActions}>
            <BoutonLien href="/catalogue" label="Catalogue" />
            <BoutonLien href="/assistant" label="Une question ?" />
            <BoutonLien href="/panier" label="Mon panier" variante="accent" />
          </View>
        </View>

        {error ? (
          <View style={[ui.notice, ui.noticeError]}>
            <Text style={ui.noticeErrorText}>{error}</Text>
          </View>
        ) : null}

        {loading ? (
          <View style={ui.loadingBlock}>
            <ActivityIndicator color={couleurs.accent} />
            <Text style={ui.muted}>Chargement de tes commandes…</Text>
          </View>
        ) : null}

        {!loading && !error && commandes.length === 0 ? (
          <View style={ui.emptyCard}>
            <Text style={ui.emptyTitle}>Aucune commande pour l’instant.</Text>
            <Text style={ui.emptyText}>Ta première commande apparaîtra ici dès qu’elle sera validée.</Text>
            <View style={styles.alignStart}>
              <BoutonLien href="/catalogue" label="Voir le catalogue" variante="accent" />
            </View>
          </View>
        ) : null}

        <View style={styles.liste}>
          {commandes.map((commande) => (
            <Link asChild href={`/commandes/${commande.idCommande}`} key={commande.idCommande}>
              <Pressable
                accessibilityLabel={`Commande n° ${commande.idCommande}, ${libelleStatut[commande.statut]}, ${formatPrix(commande.totalTtc)}`}
                accessibilityRole="link"
                style={styles.ligne}
              >
                <Text style={[styles.numero, { color: couleurStatut(commande.statut) }]}>
                  Commande n° {commande.idCommande}
                </Text>
                <View style={styles.droite}>
                  <Text style={styles.total}>{formatPrix(commande.totalTtc)}</Text>
                  <Text style={styles.chevron}>›</Text>
                </View>
              </Pressable>
            </Link>
          ))}
        </View>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  liste: {
    backgroundColor: couleurs.carte,
    borderColor: couleurs.bordure,
    borderRadius: 18,
    borderWidth: 1,
    overflow: 'hidden',
  },
  ligne: {
    alignItems: 'center',
    borderBottomColor: '#e3d9ca',
    borderBottomWidth: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 16,
  },
  numero: { fontSize: 17, fontWeight: '800' },
  droite: { alignItems: 'center', flexDirection: 'row', gap: 12 },
  total: { color: couleurs.encre, fontSize: 16, fontWeight: '800' },
  chevron: { color: couleurs.attenue, fontSize: 22, fontWeight: '700' },
  alignStart: { alignSelf: 'flex-start' },
});
