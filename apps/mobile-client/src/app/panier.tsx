import { Redirect, router, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import {
  Commande,
  getPanier,
  modifierQuantite,
  QUANTITE_MAX,
  supprimerLigne,
} from '../api/commande';
import { useAuth } from '../auth/AuthProvider';
import { BoutonLien } from '../commande/BoutonLien';
import { LigneArticle, Totaux } from '../commande/LigneArticle';
import { couleurs, ui } from '../commande/ui';

export default function PanierScreen() {
  const { accessToken, authenticatedFetch, isReady } = useAuth();
  const [panier, setPanier] = useState<Commande | null>(null);
  const [loading, setLoading] = useState(true);
  const [busyLigne, setBusyLigne] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Recharge à chaque affichage de l'écran (ex. retour depuis une fiche produit)
  useFocusEffect(
    useCallback(() => {
      if (!isReady || !accessToken) return;
      let active = true;
      setError(null);
      getPanier(authenticatedFetch)
        .then((loaded) => {
          if (active) setPanier(loaded);
        })
        .catch((loadError: unknown) => {
          if (active) setError(loadError instanceof Error ? loadError.message : 'Le panier est indisponible.');
        })
        .finally(() => {
          if (active) setLoading(false);
        });
      return () => {
        active = false;
      };
    }, [accessToken, authenticatedFetch, isReady])
  );

  async function executer(idLigne: number, action: () => Promise<Commande>) {
    setBusyLigne(idLigne);
    setError(null);
    try {
      setPanier(await action());
    } catch (actionError) {
      setError(actionError instanceof Error ? actionError.message : 'Le panier n’a pas pu être modifié.');
    } finally {
      setBusyLigne(null);
    }
  }

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

  const lignes = panier?.lignes ?? [];
  const nbArticles = lignes.reduce((total, ligne) => total + ligne.quantite, 0);

  return (
    <ScrollView contentContainerStyle={ui.page}>
      <View style={ui.container}>
        <View style={ui.header}>
          <View style={ui.headerCopy}>
            <Text style={ui.eyebrow}>MON PANIER</Text>
            <Text style={ui.title}>Panier</Text>
            <Text style={ui.subtitle}>
              {nbArticles > 0
                ? `${nbArticles} maillot${nbArticles > 1 ? 's' : ''} prêt${nbArticles > 1 ? 's' : ''} pour le vestiaire.`
                : 'Retrouve ici les maillots que tu as choisis.'}
            </Text>
          </View>
          <View style={ui.headerActions}>
            <BoutonLien href="/catalogue" label="Continuer mes achats" />
            <BoutonLien href="/commandes" label="Mes commandes" />
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
            <Text style={ui.muted}>Chargement du panier…</Text>
          </View>
        ) : null}

        {!loading && panier && lignes.length === 0 ? (
          <View style={ui.emptyCard}>
            <Text style={ui.emptyTitle}>Ton panier est vide.</Text>
            <Text style={ui.emptyText}>Choisis un maillot dans le catalogue, avec ou sans flocage.</Text>
            <View style={styles.emptyAction}>
              <BoutonLien href="/catalogue" label="Voir le catalogue" variante="accent" />
            </View>
          </View>
        ) : null}

        {panier && lignes.length > 0 ? (
          <View style={styles.layout}>
            <View style={styles.lignes}>
              {lignes.map((ligne) => {
                const busy = busyLigne === ligne.idLigneCommande;
                return (
                  <LigneArticle key={ligne.idLigneCommande} ligne={ligne}>
                    <View style={[ui.row, styles.actions]}>
                      <View style={ui.stepper}>
                        <Pressable
                          accessibilityLabel="Diminuer la quantité"
                          disabled={busy || ligne.quantite <= 1}
                          onPress={() =>
                            void executer(ligne.idLigneCommande, () =>
                              modifierQuantite(authenticatedFetch, ligne.idLigneCommande, ligne.quantite - 1)
                            )
                          }
                          style={[ui.stepperButton, (busy || ligne.quantite <= 1) && ui.disabled]}
                        >
                          <Text style={ui.stepperButtonText}>−</Text>
                        </Pressable>
                        <Text style={ui.stepperValue}>{ligne.quantite}</Text>
                        <Pressable
                          accessibilityLabel="Augmenter la quantité"
                          disabled={busy || ligne.quantite >= QUANTITE_MAX}
                          onPress={() =>
                            void executer(ligne.idLigneCommande, () =>
                              modifierQuantite(authenticatedFetch, ligne.idLigneCommande, ligne.quantite + 1)
                            )
                          }
                          style={[ui.stepperButton, (busy || ligne.quantite >= QUANTITE_MAX) && ui.disabled]}
                        >
                          <Text style={ui.stepperButtonText}>+</Text>
                        </Pressable>
                      </View>
                      <Pressable
                        accessibilityRole="button"
                        disabled={busy}
                        onPress={() =>
                          void executer(ligne.idLigneCommande, () =>
                            supprimerLigne(authenticatedFetch, ligne.idLigneCommande)
                          )
                        }
                      >
                        <Text style={ui.dangerText}>Retirer</Text>
                      </Pressable>
                    </View>
                  </LigneArticle>
                );
              })}
            </View>

            <Totaux commande={panier} />

            <Pressable
              accessibilityRole="button"
              onPress={() => router.push('/validation')}
              style={({ pressed }) => [ui.primaryAction, pressed && ui.pressed]}
            >
              <Text style={ui.primaryActionText}>Passer la commande</Text>
            </Pressable>
          </View>
        ) : null}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  layout: { gap: 16 },
  lignes: { gap: 14 },
  actions: { borderTopColor: '#e3d9ca', borderTopWidth: 1, marginTop: 16, paddingTop: 14 },
  emptyAction: { alignSelf: 'flex-start' },
});
