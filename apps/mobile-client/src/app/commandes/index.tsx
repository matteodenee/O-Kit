import { Link, Redirect, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';

import { Commande, getCommandes } from '../../api/commande';
import { useAuth } from '../../auth/AuthProvider';
import { BoutonLien } from '../../commande/BoutonLien';
import { couleurs, formatDate, formatPrix, libelleStatut, styleStatut, ui } from '../../commande/ui';

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
          {commandes.map((commande) => {
            const nbArticles = commande.lignes
              .filter((l) => l.statut === 'ACTIVE')
              .reduce((total, l) => total + l.quantite, 0);
            return (
              <Link asChild href={`/commandes/${commande.idCommande}`} key={commande.idCommande}>
                <Pressable accessibilityRole="button" style={({ pressed }) => [ui.card, pressed && ui.pressed]}>
                  <View style={ui.row}>
                    <Text style={ui.cardTitle}>Commande n° {commande.idCommande}</Text>
                    <Text style={[ui.badge, styleStatut(commande.statut)]}>
                      {libelleStatut[commande.statut].toUpperCase()}
                    </Text>
                  </View>
                  <Text style={[ui.cardMeta, styles.meta]}>
                    {formatDate(commande.dateCommande)} · {nbArticles} article{nbArticles > 1 ? 's' : ''}
                  </Text>
                  <View style={[ui.row, styles.footer]}>
                    <Text style={styles.total}>{formatPrix(commande.totalTtc)}</Text>
                    <Text style={ui.actionText}>Voir le détail →</Text>
                  </View>
                </Pressable>
              </Link>
            );
          })}
        </View>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  liste: { gap: 14 },
  meta: { marginTop: 6 },
  footer: { borderTopColor: '#e3d9ca', borderTopWidth: 1, marginTop: 14, paddingTop: 12 },
  total: { color: '#17322c', fontSize: 20, fontWeight: '800' },
  alignStart: { alignSelf: 'flex-start' },
});
