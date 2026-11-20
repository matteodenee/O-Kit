import { Redirect, router } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { CommandeApiError, Commande, getPanier, validerCommande } from '../api/commande';
import { listUserAddresses, UserAddress } from '../api/userAddresses';
import { useAuth } from '../auth/AuthProvider';
import { BoutonLien } from '../commande/BoutonLien';
import { Totaux } from '../commande/LigneArticle';
import { couleurs, formatPrix, ui } from '../commande/ui';

/** ms-commande exige un code postal français à 5 chiffres. */
function estLivrable(adresse: UserAddress) {
  return /^\d{5}$/.test(adresse.postalCode ?? '');
}

export default function ValidationScreen() {
  const { accessToken, authenticatedFetch, isReady } = useAuth();
  const [panier, setPanier] = useState<Commande | null>(null);
  const [adresses, setAdresses] = useState<UserAddress[]>([]);
  const [idAdresse, setIdAdresse] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isReady || !accessToken) return;
    let active = true;

    Promise.all([getPanier(authenticatedFetch), listUserAddresses(authenticatedFetch)])
      .then(([loadedPanier, loadedAdresses]) => {
        if (!active) return;
        setPanier(loadedPanier);
        setAdresses(loadedAdresses);
        const parDefaut =
          loadedAdresses.find((a) => a.defaultAddress && estLivrable(a)) ?? loadedAdresses.find(estLivrable);
        setIdAdresse(parDefaut?.id ?? null);
      })
      .catch((loadError: unknown) => {
        if (active) setError(loadError instanceof Error ? loadError.message : 'La validation est indisponible.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [accessToken, authenticatedFetch, isReady]);

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

  const adresse = adresses.find((a) => a.id === idAdresse) ?? null;
  const panierVide = !panier || panier.lignes.length === 0;

  async function commander() {
    if (!adresse) {
      setError('Choisis une adresse de livraison.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const commande = await validerCommande(authenticatedFetch, {
        rue: adresse.addressLine1,
        complement: adresse.addressLine2,
        codePostal: adresse.postalCode ?? '',
        ville: adresse.city,
      });
      router.replace(`/commandes/${commande.idCommande}?confirmee=1`);
    } catch (orderError) {
      if (orderError instanceof CommandeApiError && orderError.manques.length > 0) {
        setError(`${orderError.message}. Ajuste les quantités dans ton panier.`);
      } else {
        setError(orderError instanceof Error ? orderError.message : "La commande n'a pas pu être validée.");
      }
      // 409 : le serveur a pu mettre à jour les prix du panier, on réaffiche le total à jour
      if (orderError instanceof CommandeApiError && orderError.status === 409) {
        getPanier(authenticatedFetch).then(setPanier).catch(() => undefined);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <ScrollView contentContainerStyle={ui.page}>
      <View style={ui.container}>
        <View style={ui.header}>
          <View style={ui.headerCopy}>
            <Text style={ui.eyebrow}>VALIDATION</Text>
            <Text style={ui.title}>Ma commande</Text>
            <Text style={ui.subtitle}>Vérifie ta livraison avant de payer.</Text>
          </View>
          <View style={ui.headerActions}>
            <BoutonLien href="/panier" label="Retour au panier" />
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
            <Text style={ui.muted}>Préparation de ta commande…</Text>
          </View>
        ) : null}

        {!loading && panierVide ? (
          <View style={ui.emptyCard}>
            <Text style={ui.emptyTitle}>Ton panier est vide.</Text>
            <View style={styles.alignStart}>
              <BoutonLien href="/catalogue" label="Voir le catalogue" variante="accent" />
            </View>
          </View>
        ) : null}

        {!loading && panier && !panierVide ? (
          <View style={styles.layout}>
            <View>
              <Text style={ui.sectionTitle}>Adresse de livraison</Text>
              {adresses.length === 0 ? (
                <View style={ui.emptyCard}>
                  <Text style={ui.emptyText}>Tu n’as pas encore d’adresse enregistrée.</Text>
                  <View style={styles.alignStart}>
                    <BoutonLien href="/addresses" label="Ajouter une adresse" variante="accent" />
                  </View>
                </View>
              ) : (
                <View style={styles.adresses}>
                  {adresses.map((a) => {
                    const livrable = estLivrable(a);
                    const choisie = a.id === idAdresse;
                    return (
                      <Pressable
                        key={a.id}
                        accessibilityRole="radio"
                        accessibilityState={{ checked: choisie, disabled: !livrable }}
                        disabled={!livrable}
                        onPress={() => setIdAdresse(a.id)}
                        style={[ui.card, choisie && styles.choisie, !livrable && ui.disabled]}
                      >
                        <View style={ui.row}>
                          <Text style={ui.cardTitle}>{a.label || 'Adresse'}</Text>
                          {choisie ? <Text style={[ui.badge, styles.badgeChoisie]}>LIVRAISON</Text> : null}
                        </View>
                        <Text style={styles.destinataire}>
                          {a.recipientFirstName} {a.recipientLastName}
                        </Text>
                        <Text style={ui.cardMeta}>{a.addressLine1}</Text>
                        {a.addressLine2 ? <Text style={ui.cardMeta}>{a.addressLine2}</Text> : null}
                        <Text style={ui.cardMeta}>
                          {[a.postalCode, a.city].filter(Boolean).join(' ')}
                        </Text>
                        {!livrable ? (
                          <Text style={[ui.dangerText, styles.mention]}>Livraison en France uniquement (code postal à 5 chiffres)</Text>
                        ) : null}
                      </Pressable>
                    );
                  })}
                  <BoutonLien href="/addresses" label="Gérer mes adresses" variante="texte" />
                </View>
              )}
            </View>

            <View>
              <Text style={ui.sectionTitle}>Récapitulatif</Text>
              <View style={[ui.card, styles.recap]}>
                {panier.lignes.map((ligne) => (
                  <View key={ligne.idLigneCommande} style={ui.row}>
                    <Text style={[ui.cardMeta, styles.flex]}>
                      {ligne.quantite} × {ligne.titreProduit} ({ligne.taille})
                      {ligne.flocage ? ` · ${ligne.flocage.texte} ${ligne.flocage.numero}` : ''}
                    </Text>
                    <Text style={ui.totalValue}>{formatPrix(ligne.totalTtc)}</Text>
                  </View>
                ))}
              </View>
            </View>

            <Totaux commande={panier} />

            <Pressable
              accessibilityRole="button"
              disabled={busy || !adresse}
              onPress={() => void commander()}
              style={({ pressed }) => [ui.primaryAction, pressed && ui.pressed, (busy || !adresse) && ui.disabled]}
            >
              {busy ? (
                <ActivityIndicator color={couleurs.accentTexte} />
              ) : (
                <Text style={ui.primaryActionText}>Payer {formatPrix(panier.totalTtc)}</Text>
              )}
            </Pressable>
            <Text style={[ui.muted, styles.center]}>Paiement de démonstration : aucune carte n’est débitée.</Text>
          </View>
        ) : null}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  layout: { gap: 20 },
  adresses: { gap: 12 },
  choisie: { borderColor: '#24705e', borderWidth: 2 },
  badgeChoisie: { backgroundColor: '#dcebe5', color: '#1b614f' },
  destinataire: { color: '#2f4841', fontSize: 16, fontWeight: '800', marginBottom: 4, marginTop: 8 },
  mention: { marginTop: 8 },
  recap: { gap: 10 },
  flex: { flex: 1 },
  center: { textAlign: 'center' },
  alignStart: { alignSelf: 'flex-start' },
});
