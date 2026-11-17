import { Redirect, useLocalSearchParams } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
} from 'react-native';

import { getProduct, ORDRE_TAILLES, ProductDetail } from '../../api/catalogue';
import {
  ajouterAuPanier,
  FLOCAGE_PRIX_HT,
  FLOCAGE_TEXTE_MAX,
  QUANTITE_MAX,
} from '../../api/commande';
import { getStockStatuses, StockStatus } from '../../api/stock';
import { useAuth } from '../../auth/AuthProvider';
import { BoutonLien } from '../../commande/BoutonLien';
import { couleurs, formatPrix, prixTtc, ui } from '../../commande/ui';

const TEXTE_FLOCAGE_VALIDE = /^[\p{L} .'-]+$/u;

export default function ProduitScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const idProduit = Number(id);
  const { accessToken, authenticatedFetch, isReady } = useAuth();

  const [produit, setProduit] = useState<ProductDetail | null>(null);
  const [stocks, setStocks] = useState<Record<number, StockStatus>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [idVariante, setIdVariante] = useState<number | null>(null);
  const [quantite, setQuantite] = useState(1);
  const [avecFlocage, setAvecFlocage] = useState(false);
  const [texteFlocage, setTexteFlocage] = useState('');
  const [numeroFlocage, setNumeroFlocage] = useState('');

  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    if (!isReady || !accessToken || !Number.isFinite(idProduit)) return;
    let active = true;

    getProduct(authenticatedFetch, idProduit)
      .then(async (loaded) => {
        if (!active) return;
        setProduit(loaded);
        // Le statut de stock est un bonus : si ms-stock ne répond pas, on affiche quand même les tailles.
        try {
          const statuts = await getStockStatuses(authenticatedFetch, loaded.variantes.map((v) => v.id));
          if (active) setStocks(Object.fromEntries(statuts.map((s) => [s.variantId, s.status])));
        } catch {
          // ignoré volontairement
        }
      })
      .catch((loadError: unknown) => {
        if (active) setError(loadError instanceof Error ? loadError.message : 'Ce maillot est indisponible.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [accessToken, authenticatedFetch, idProduit, isReady]);

  const variantes = useMemo(
    () =>
      [...(produit?.variantes ?? [])].sort(
        (a, b) => ORDRE_TAILLES.indexOf(a.taille) - ORDRE_TAILLES.indexOf(b.taille)
      ),
    [produit]
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

  const prixUnitaire = produit ? Number(produit.prixApresPromotionTTC ?? produit.prixTTC) : 0;
  const prixFlocage = produit ? prixTtc(FLOCAGE_PRIX_HT, Number(produit.tauxTva)) : 0;
  const flocable = produit?.estFlocable === true;
  const total = (prixUnitaire + (avecFlocage ? prixFlocage : 0)) * quantite;

  async function ajouter() {
    setMessage(null);
    setActionError(null);
    if (!produit || idVariante === null) {
      setActionError('Choisis une taille.');
      return;
    }
    let flocage: { texte: string; numero: number } | undefined;
    if (avecFlocage) {
      const texte = texteFlocage.trim();
      const numero = Number(numeroFlocage);
      if (!texte || !TEXTE_FLOCAGE_VALIDE.test(texte)) {
        setActionError('Le nom floqué ne peut contenir que des lettres, espaces, points, apostrophes et tirets.');
        return;
      }
      if (numeroFlocage === '' || !Number.isInteger(numero) || numero < 0 || numero > 99) {
        setActionError('Le numéro doit être compris entre 0 et 99.');
        return;
      }
      flocage = { texte, numero };
    }

    setBusy(true);
    try {
      await ajouterAuPanier(authenticatedFetch, { idProduit: produit.id, idVariante, quantite, flocage });
      setMessage('Ajouté à ton panier.');
      setQuantite(1);
    } catch (addError) {
      setActionError(addError instanceof Error ? addError.message : "L'article n'a pas pu être ajouté.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <ScrollView contentContainerStyle={ui.page}>
      <View style={ui.container}>
        <View style={ui.header}>
          <View style={ui.headerCopy}>
            <Text style={ui.eyebrow}>{(produit?.marque?.nomMarque ?? 'O’KIT').toUpperCase()}</Text>
            <Text style={ui.title}>{produit?.titre ?? 'Maillot'}</Text>
            {produit ? (
              <Text style={ui.subtitle}>
                {[produit.equipe?.nom, produit.saison?.nom].filter(Boolean).join(' · ') || 'Collection O’kit'}
              </Text>
            ) : null}
          </View>
          <View style={ui.headerActions}>
            <BoutonLien href="/catalogue" label="Retour au catalogue" />
            <BoutonLien href="/panier" label="Mon panier" variante="accent" />
          </View>
        </View>

        {loading ? (
          <View style={ui.loadingBlock}>
            <ActivityIndicator color={couleurs.accent} />
            <Text style={ui.muted}>Chargement du maillot…</Text>
          </View>
        ) : null}

        {error ? (
          <View style={[ui.notice, ui.noticeError]}>
            <Text style={ui.noticeErrorText}>{error}</Text>
          </View>
        ) : null}

        {produit ? (
          <View style={styles.layout}>
            <View style={[ui.card, styles.section]}>
              <View style={ui.row}>
                <Text style={styles.price}>{formatPrix(prixUnitaire)}</Text>
                {produit.prixApresPromotionTTC != null ? (
                  <Text style={styles.oldPrice}>{formatPrix(produit.prixTTC)}</Text>
                ) : null}
              </View>
              {produit.description ? <Text style={ui.cardMeta}>{produit.description}</Text> : null}
              {produit.couleursPrincipales ? (
                <Text style={ui.muted}>Couleurs : {produit.couleursPrincipales}</Text>
              ) : null}
            </View>

            <View style={[ui.card, styles.section]}>
              <Text style={ui.sectionTitle}>Taille</Text>
              <View style={styles.sizes}>
                {variantes.map((variante) => {
                  const statut = stocks[variante.id];
                  const epuise = statut === 'OUT_OF_STOCK';
                  const choisie = variante.id === idVariante;
                  return (
                    <Pressable
                      key={variante.id}
                      accessibilityRole="button"
                      accessibilityState={{ selected: choisie, disabled: epuise }}
                      disabled={epuise}
                      onPress={() => setIdVariante(variante.id)}
                      style={[styles.size, choisie && styles.sizeSelected, epuise && ui.disabled]}
                    >
                      <Text style={[styles.sizeText, choisie && styles.sizeTextSelected]}>{variante.taille}</Text>
                      {statut === 'LOW_STOCK' ? <Text style={styles.sizeHint}>Derniers</Text> : null}
                      {epuise ? <Text style={styles.sizeHint}>Épuisé</Text> : null}
                    </Pressable>
                  );
                })}
              </View>
              {variantes.length === 0 ? <Text style={ui.muted}>Aucune taille disponible.</Text> : null}

              <View style={[ui.row, styles.quantityRow]}>
                <Text style={ui.sectionTitle}>Quantité</Text>
                <View style={ui.stepper}>
                  <Pressable
                    accessibilityLabel="Diminuer la quantité"
                    disabled={quantite <= 1}
                    onPress={() => setQuantite((q) => q - 1)}
                    style={[ui.stepperButton, quantite <= 1 && ui.disabled]}
                  >
                    <Text style={ui.stepperButtonText}>−</Text>
                  </Pressable>
                  <Text style={ui.stepperValue}>{quantite}</Text>
                  <Pressable
                    accessibilityLabel="Augmenter la quantité"
                    disabled={quantite >= QUANTITE_MAX}
                    onPress={() => setQuantite((q) => q + 1)}
                    style={[ui.stepperButton, quantite >= QUANTITE_MAX && ui.disabled]}
                  >
                    <Text style={ui.stepperButtonText}>+</Text>
                  </Pressable>
                </View>
              </View>
            </View>

            {flocable ? (
              <View style={[ui.card, styles.section]}>
                <View style={ui.row}>
                  <View style={styles.flex}>
                    <Text style={ui.sectionTitle}>Flocage personnalisé</Text>
                    <Text style={ui.muted}>Nom et numéro au dos · +{formatPrix(prixFlocage)} par maillot</Text>
                  </View>
                  <Switch
                    accessibilityLabel="Ajouter un flocage"
                    onValueChange={setAvecFlocage}
                    thumbColor={couleurs.carte}
                    trackColor={{ false: '#d2c5b3', true: couleurs.accent }}
                    value={avecFlocage}
                  />
                </View>

                {avecFlocage ? (
                  <View style={styles.flocageForm}>
                    <View style={styles.flocagePreview}>
                      <Text style={styles.previewName}>{texteFlocage.trim().toUpperCase() || 'TON NOM'}</Text>
                      <Text style={styles.previewNumber}>{numeroFlocage || '10'}</Text>
                    </View>
                    <View style={styles.field}>
                      <Text style={ui.fieldLabel}>Nom ({FLOCAGE_TEXTE_MAX} caractères max)</Text>
                      <TextInput
                        autoCapitalize="characters"
                        maxLength={FLOCAGE_TEXTE_MAX}
                        onChangeText={setTexteFlocage}
                        placeholder="ZIDANE"
                        placeholderTextColor="#a59d91"
                        style={ui.input}
                        value={texteFlocage}
                      />
                    </View>
                    <View style={styles.field}>
                      <Text style={ui.fieldLabel}>Numéro (0 à 99)</Text>
                      <TextInput
                        keyboardType="number-pad"
                        maxLength={2}
                        onChangeText={(value) => setNumeroFlocage(value.replace(/\D/g, ''))}
                        placeholder="10"
                        placeholderTextColor="#a59d91"
                        style={ui.input}
                        value={numeroFlocage}
                      />
                    </View>
                  </View>
                ) : null}
              </View>
            ) : null}

            {message ? (
              <View style={[ui.notice, ui.noticeSuccess]}>
                <Text style={ui.noticeSuccessText}>{message}</Text>
                <View style={styles.noticeLink}>
                  <BoutonLien href="/panier" label="Voir mon panier →" variante="texte" />
                </View>
              </View>
            ) : null}
            {actionError ? (
              <View style={[ui.notice, ui.noticeError]}>
                <Text style={ui.noticeErrorText}>{actionError}</Text>
              </View>
            ) : null}

            <View style={[ui.card, ui.row]}>
              <View>
                <Text style={ui.totalLabel}>Total</Text>
                <Text style={ui.grandTotalValue}>{formatPrix(total)}</Text>
              </View>
              <Pressable
                accessibilityRole="button"
                disabled={busy || produit.statut !== 'ACTIF'}
                onPress={() => void ajouter()}
                style={({ pressed }) => [
                  ui.primaryAction,
                  pressed && ui.pressed,
                  (busy || produit.statut !== 'ACTIF') && ui.disabled,
                ]}
              >
                {busy ? (
                  <ActivityIndicator color={couleurs.accentTexte} />
                ) : (
                  <Text style={ui.primaryActionText}>Ajouter au panier</Text>
                )}
              </Pressable>
            </View>
          </View>
        ) : null}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  layout: { gap: 16 },
  section: { gap: 10 },
  flex: { flex: 1 },
  price: { color: couleurs.encre, fontSize: 28, fontWeight: '800' },
  oldPrice: { color: couleurs.attenue, fontSize: 16, textDecorationLine: 'line-through' },
  sizes: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  size: { alignItems: 'center', backgroundColor: '#f5efe4', borderColor: couleurs.bordure, borderRadius: 12, borderWidth: 1, minWidth: 64, paddingHorizontal: 14, paddingVertical: 10 },
  sizeSelected: { backgroundColor: couleurs.encre, borderColor: couleurs.encre },
  sizeText: { color: couleurs.encre, fontSize: 15, fontWeight: '800' },
  sizeTextSelected: { color: couleurs.accentTexte },
  sizeHint: { color: couleurs.eyebrow, fontSize: 10, fontWeight: '800', marginTop: 2 },
  quantityRow: { marginTop: 14 },
  flocageForm: { gap: 14, marginTop: 10 },
  flocagePreview: { alignItems: 'center', alignSelf: 'center', backgroundColor: couleurs.encre, borderRadius: 18, paddingHorizontal: 34, paddingVertical: 20, minWidth: 200 },
  previewName: { color: couleurs.accentTexte, fontSize: 18, fontWeight: '900', letterSpacing: 2 },
  previewNumber: { color: couleurs.accent, fontSize: 56, fontWeight: '900', lineHeight: 62 },
  field: { gap: 7 },
  noticeLink: { marginTop: 8 },
});
