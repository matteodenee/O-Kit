import type { ReactNode } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import type { Commande, LigneCommande } from '../api/commande';
import { formatPrix, prixTtc, ui } from './ui';

/** Une ligne d'article (panier ou commande), avec son flocage éventuel. */
export function LigneArticle({ ligne, children }: { ligne: LigneCommande; children?: ReactNode }) {
  const annulee = ligne.statut === 'ANNULEE';
  const unitaireTtc = prixTtc(Number(ligne.prixUnitaireHt), Number(ligne.tauxTva));

  return (
    <View style={[ui.card, annulee && styles.annulee]}>
      <View style={[ui.row, styles.top]}>
        <View style={styles.flex}>
          <Text style={[ui.cardTitle, annulee && styles.barre]}>{ligne.titreProduit}</Text>
          <Text style={ui.cardMeta}>
            Taille {ligne.taille ?? '—'} · {formatPrix(unitaireTtc)} l’unité
          </Text>
        </View>
        <Text style={[styles.total, annulee && styles.barre]}>{formatPrix(ligne.totalTtc)}</Text>
      </View>

      {ligne.flocage ? (
        <View style={ui.flocageBadge}>
          <Text style={ui.flocageBadgeText}>
            FLOCAGE · {ligne.flocage.texte} {ligne.flocage.numero} · +
            {formatPrix(prixTtc(Number(ligne.flocage.prix), Number(ligne.tauxTva)))}
          </Text>
        </View>
      ) : null}

      {annulee ? <Text style={[ui.dangerText, styles.mention]}>Article annulé et remboursé</Text> : null}

      {children}
    </View>
  );
}

/** Sous-total, livraison et total TTC. */
export function Totaux({ commande }: { commande: Commande }) {
  const articlesTtc = Number(commande.totalTtc) - Number(commande.fraisLivraison);
  return (
    <View style={ui.card}>
      <View style={ui.row}>
        <Text style={ui.totalLabel}>Articles</Text>
        <Text style={ui.totalValue}>{formatPrix(articlesTtc)}</Text>
      </View>
      <View style={[ui.row, styles.ligneTotal]}>
        <Text style={ui.totalLabel}>Livraison</Text>
        <Text style={ui.totalValue}>{formatPrix(commande.fraisLivraison)}</Text>
      </View>
      <View style={ui.divider} />
      <View style={ui.row}>
        <Text style={ui.grandTotalLabel}>Total TTC</Text>
        <Text style={ui.grandTotalValue}>{formatPrix(commande.totalTtc)}</Text>
      </View>
      <Text style={[ui.muted, styles.ligneTotal]}>Dont {formatPrix(Number(commande.prixHt))} HT d’articles</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  top: { alignItems: 'flex-start' },
  total: { color: '#17322c', fontSize: 18, fontWeight: '800' },
  annulee: { opacity: 0.65 },
  barre: { textDecorationLine: 'line-through' },
  mention: { marginTop: 8 },
  ligneTotal: { marginTop: 8 },
});
