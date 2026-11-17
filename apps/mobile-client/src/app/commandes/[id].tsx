import { Redirect, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { annulerCommande, annulerLigne, Commande, getCommande } from '../../api/commande';
import { useAuth } from '../../auth/AuthProvider';
import { BoutonLien } from '../../commande/BoutonLien';
import { LigneArticle, Totaux } from '../../commande/LigneArticle';
import {
  couleurs,
  estAnnulable,
  formatDate,
  libelleStatut,
  styleStatut,
  ui,
} from '../../commande/ui';

/** Ce que l'utilisateur est en train de confirmer : toute la commande ou une ligne. */
type Confirmation = { type: 'commande' } | { type: 'ligne'; idLigne: number } | null;

export default function CommandeDetailScreen() {
  const { id, confirmee } = useLocalSearchParams<{ id: string; confirmee?: string }>();
  const idCommande = Number(id);
  const { accessToken, authenticatedFetch, isReady } = useAuth();

  const [commande, setCommande] = useState<Commande | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [confirmation, setConfirmation] = useState<Confirmation>(null);
  const [message, setMessage] = useState<string | null>(
    confirmee === '1' ? 'Merci ! Ta commande est confirmée.' : null
  );
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isReady || !accessToken || !Number.isFinite(idCommande)) return;
    let active = true;
    getCommande(authenticatedFetch, idCommande)
      .then((loaded) => {
        if (active) setCommande(loaded);
      })
      .catch((loadError: unknown) => {
        if (active) setError(loadError instanceof Error ? loadError.message : 'Cette commande est indisponible.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [accessToken, authenticatedFetch, idCommande, isReady]);

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

  async function confirmer() {
    if (!confirmation || !commande) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      if (confirmation.type === 'commande') {
        setCommande(await annulerCommande(authenticatedFetch, commande.idCommande));
        setMessage('Ta commande est annulée. Le remboursement est en cours.');
      } else {
        setCommande(await annulerLigne(authenticatedFetch, commande.idCommande, confirmation.idLigne));
        setMessage('L’article est annulé. Le remboursement est en cours.');
      }
      setConfirmation(null);
    } catch (cancelError) {
      setError(cancelError instanceof Error ? cancelError.message : "L'annulation a échoué.");
    } finally {
      setBusy(false);
    }
  }

  const annulable = commande ? estAnnulable(commande.statut) : false;
  const nbLignesActives = commande?.lignes.filter((l) => l.statut === 'ACTIVE').length ?? 0;

  const boiteConfirmation = (texte: string) => (
    <View style={ui.confirmBox}>
      <Text style={ui.confirmText}>{texte}</Text>
      <View style={ui.inlineActions}>
        <Pressable accessibilityRole="button" disabled={busy} onPress={() => setConfirmation(null)} style={ui.secondarySmall}>
          <Text style={ui.secondarySmallText}>Garder</Text>
        </Pressable>
        <Pressable
          accessibilityRole="button"
          disabled={busy}
          onPress={() => void confirmer()}
          style={[ui.dangerSmall, busy && ui.disabled]}
        >
          <Text style={ui.dangerSmallText}>{busy ? 'Annulation…' : 'Confirmer l’annulation'}</Text>
        </Pressable>
      </View>
    </View>
  );

  return (
    <ScrollView contentContainerStyle={ui.page}>
      <View style={ui.container}>
        <View style={ui.header}>
          <View style={ui.headerCopy}>
            <Text style={ui.eyebrow}>MES COMMANDES</Text>
            <Text style={ui.title}>Commande n° {Number.isFinite(idCommande) ? idCommande : ''}</Text>
            {commande ? (
              <Text style={ui.subtitle}>Passée le {formatDate(commande.dateCommande)}</Text>
            ) : null}
          </View>
          <View style={ui.headerActions}>
            <BoutonLien href="/commandes" label="Mes commandes" />
            <BoutonLien href="/catalogue" label="Catalogue" variante="accent" />
          </View>
        </View>

        {message ? (
          <View style={[ui.notice, ui.noticeSuccess]}>
            <Text style={ui.noticeSuccessText}>{message}</Text>
          </View>
        ) : null}
        {error ? (
          <View style={[ui.notice, ui.noticeError]}>
            <Text style={ui.noticeErrorText}>{error}</Text>
          </View>
        ) : null}

        {loading ? (
          <View style={ui.loadingBlock}>
            <ActivityIndicator color={couleurs.accent} />
            <Text style={ui.muted}>Chargement de la commande…</Text>
          </View>
        ) : null}

        {commande ? (
          <View style={styles.layout}>
            <View style={[ui.card, styles.suivi]}>
              <View style={ui.row}>
                <Text style={ui.sectionTitle}>Suivi</Text>
                <Text style={[ui.badge, styleStatut(commande.statut)]}>
                  {libelleStatut[commande.statut].toUpperCase()}
                </Text>
              </View>
              {commande.codeSuiviTransporteur ? (
                <Text style={ui.cardMeta}>Numéro de suivi : {commande.codeSuiviTransporteur}</Text>
              ) : null}
              {commande.adresseLivraison ? (
                <Text style={ui.cardMeta}>
                  Livraison : {commande.adresseLivraison.rue}
                  {commande.adresseLivraison.complement ? `, ${commande.adresseLivraison.complement}` : ''},{' '}
                  {commande.adresseLivraison.codePostal} {commande.adresseLivraison.ville}
                </Text>
              ) : null}
            </View>

            <View style={styles.lignes}>
              {commande.lignes.map((ligne) => {
                const enConfirmation =
                  confirmation?.type === 'ligne' && confirmation.idLigne === ligne.idLigneCommande;
                const peutAnnulerLigne = annulable && ligne.statut === 'ACTIVE' && nbLignesActives > 1;
                return (
                  <LigneArticle key={ligne.idLigneCommande} ligne={ligne}>
                    {peutAnnulerLigne && !enConfirmation ? (
                      <Pressable
                        accessibilityRole="button"
                        onPress={() => setConfirmation({ type: 'ligne', idLigne: ligne.idLigneCommande })}
                        style={styles.ligneAction}
                      >
                        <Text style={ui.dangerText}>Annuler cet article</Text>
                      </Pressable>
                    ) : null}
                    {enConfirmation ? boiteConfirmation('Annuler cet article ? Il te sera remboursé.') : null}
                  </LigneArticle>
                );
              })}
            </View>

            <Totaux commande={commande} />

            {annulable && confirmation?.type !== 'commande' ? (
              <Pressable
                accessibilityRole="button"
                onPress={() => setConfirmation({ type: 'commande' })}
                style={({ pressed }) => [ui.secondaryAction, pressed && ui.pressed]}
              >
                <Text style={ui.dangerText}>Annuler la commande</Text>
              </Pressable>
            ) : null}
            {confirmation?.type === 'commande'
              ? boiteConfirmation('Annuler toute la commande ? Tu seras intégralement remboursé.')
              : null}
          </View>
        ) : null}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  layout: { gap: 16 },
  suivi: { gap: 8 },
  lignes: { gap: 14 },
  ligneAction: { marginTop: 14 },
});
