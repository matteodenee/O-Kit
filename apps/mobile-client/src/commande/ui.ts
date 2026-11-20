import { StyleSheet } from 'react-native';

import type { StatutCommande } from '../api/commande';

/** Palette O'kit, reprise des écrans Catalogue et Adresses. */
export const couleurs = {
  fond: '#f3eee4',
  carte: '#fffaf1',
  bordure: '#ded3c2',
  encre: '#17322c',
  texte: '#615f59',
  attenue: '#79736a',
  accent: '#c95f2b',
  accentTexte: '#fffaf1',
  eyebrow: '#b75328',
  vert: '#1f6655',
  vertFond: '#dcebe5',
  danger: '#a23d2b',
  dangerFond: '#fde9e5',
};

export function formatPrix(montant: number) {
  return `${Number(montant).toFixed(2).replace('.', ',')} €`;
}

export function prixTtc(prixHt: number, tauxTva: number) {
  return Math.round(prixHt * (1 + tauxTva / 100) * 100) / 100;
}

export function formatDate(iso: string | null) {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
}

export const libelleStatut: Record<StatutCommande, string> = {
  PANIER: 'Panier',
  CONFIRMEE: 'Confirmée',
  EN_PREPARATION: 'En préparation',
  EXPEDIEE: 'Expédiée',
  LIVREE: 'Livrée',
  ANNULEE: 'Annulée',
};

export function styleStatut(statut: StatutCommande) {
  if (statut === 'ANNULEE') return { backgroundColor: couleurs.dangerFond, color: couleurs.danger };
  if (statut === 'LIVREE' || statut === 'EXPEDIEE') return { backgroundColor: couleurs.vertFond, color: '#1b614f' };
  return { backgroundColor: '#f6e3d6', color: couleurs.eyebrow };
}

export function estAnnulable(statut: StatutCommande) {
  return statut === 'CONFIRMEE' || statut === 'EN_PREPARATION';
}

/** Styles partagés par les écrans panier / commande (même DA que addresses.tsx). */
export const ui = StyleSheet.create({
  loadingPage: { alignItems: 'center', backgroundColor: couleurs.fond, flex: 1, justifyContent: 'center' },
  page: { backgroundColor: couleurs.fond, flexGrow: 1, padding: 20 },
  container: { alignSelf: 'center', maxWidth: 1080, width: '100%' },
  header: { alignItems: 'flex-start', flexDirection: 'row', flexWrap: 'wrap', gap: 20, justifyContent: 'space-between', marginBottom: 26 },
  headerCopy: { maxWidth: 680 },
  eyebrow: { color: couleurs.eyebrow, fontSize: 11, fontWeight: '900', letterSpacing: 1.8, marginBottom: 8 },
  title: { color: couleurs.encre, fontSize: 40, fontWeight: '800', letterSpacing: -1.4 },
  subtitle: { color: couleurs.texte, fontSize: 15, lineHeight: 23, marginTop: 10 },
  headerActions: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  backButton: { borderColor: '#b7aa98', borderRadius: 12, borderWidth: 1, paddingHorizontal: 16, paddingVertical: 12 },
  backButtonText: { color: couleurs.encre, fontSize: 13, fontWeight: '800' },
  accentButton: { backgroundColor: couleurs.accent, borderRadius: 12, paddingHorizontal: 16, paddingVertical: 12 },
  accentButtonText: { color: couleurs.accentTexte, fontSize: 13, fontWeight: '800' },
  pressed: { opacity: 0.8, transform: [{ scale: 0.99 }] },
  disabled: { opacity: 0.5 },
  loadingBlock: { alignItems: 'center', flexDirection: 'row', gap: 10, paddingVertical: 24 },
  muted: { color: couleurs.attenue, fontSize: 14 },
  emptyCard: { backgroundColor: couleurs.carte, borderColor: couleurs.bordure, borderRadius: 18, borderStyle: 'dashed', borderWidth: 1, padding: 28, gap: 14 },
  emptyTitle: { color: couleurs.encre, fontSize: 20, fontWeight: '800' },
  emptyText: { color: '#6e6960', fontSize: 14, lineHeight: 21 },
  card: { backgroundColor: couleurs.carte, borderColor: couleurs.bordure, borderRadius: 18, borderWidth: 1, padding: 22 },
  cardTitle: { color: couleurs.encre, fontSize: 20, fontWeight: '800' },
  cardMeta: { color: couleurs.texte, fontSize: 14, lineHeight: 20 },
  sectionTitle: { color: couleurs.encre, fontSize: 18, fontWeight: '800', marginBottom: 12 },
  badge: { borderRadius: 999, fontSize: 10, fontWeight: '900', letterSpacing: 1, overflow: 'hidden', paddingHorizontal: 10, paddingVertical: 6 },
  flocageBadge: { alignSelf: 'flex-start', backgroundColor: couleurs.encre, borderRadius: 8, marginTop: 8, paddingHorizontal: 10, paddingVertical: 6 },
  flocageBadgeText: { color: couleurs.accentTexte, fontSize: 12, fontWeight: '800', letterSpacing: 1 },
  row: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between', gap: 12 },
  divider: { borderTopColor: '#e3d9ca', borderTopWidth: 1, marginVertical: 14 },
  totalLabel: { color: couleurs.texte, fontSize: 14 },
  totalValue: { color: couleurs.encre, fontSize: 14, fontWeight: '700' },
  grandTotalLabel: { color: couleurs.encre, fontSize: 18, fontWeight: '800' },
  grandTotalValue: { color: couleurs.encre, fontSize: 22, fontWeight: '800' },
  primaryAction: { alignItems: 'center', backgroundColor: couleurs.accent, borderRadius: 13, justifyContent: 'center', minHeight: 50, paddingHorizontal: 20 },
  primaryActionText: { color: couleurs.accentTexte, fontSize: 15, fontWeight: '800' },
  secondaryAction: { alignItems: 'center', borderColor: '#c9bfae', borderRadius: 12, borderWidth: 1, justifyContent: 'center', minHeight: 48, paddingHorizontal: 20 },
  secondaryActionText: { color: couleurs.encre, fontSize: 14, fontWeight: '800' },
  actionText: { color: couleurs.vert, fontSize: 13, fontWeight: '800' },
  dangerText: { color: couleurs.danger, fontSize: 13, fontWeight: '800' },
  confirmBox: { backgroundColor: '#fff0eb', borderRadius: 12, marginTop: 14, padding: 14 },
  confirmText: { color: '#723327', fontSize: 13, fontWeight: '700' },
  inlineActions: { flexDirection: 'row', gap: 10, marginTop: 12 },
  secondarySmall: { borderColor: '#bdaea0', borderRadius: 9, borderWidth: 1, paddingHorizontal: 13, paddingVertical: 9 },
  secondarySmallText: { color: '#4f4a43', fontSize: 12, fontWeight: '800' },
  dangerSmall: { backgroundColor: couleurs.danger, borderRadius: 9, paddingHorizontal: 13, paddingVertical: 9 },
  dangerSmallText: { color: '#fff', fontSize: 12, fontWeight: '800' },
  notice: { borderRadius: 12, marginBottom: 18, padding: 14 },
  noticeError: { backgroundColor: couleurs.dangerFond },
  noticeSuccess: { backgroundColor: '#e1f1e9' },
  noticeErrorText: { color: '#8b3527', fontSize: 13, lineHeight: 19 },
  noticeSuccessText: { color: couleurs.vert, fontSize: 13, lineHeight: 19 },
  fieldLabel: { color: '#344b45', fontSize: 12, fontWeight: '800' },
  input: { backgroundColor: '#f5efe4', borderColor: couleurs.bordure, borderRadius: 12, borderWidth: 1, color: couleurs.encre, fontSize: 15, minHeight: 49, paddingHorizontal: 15 },
  stepper: { alignItems: 'center', flexDirection: 'row', gap: 4 },
  stepperButton: { alignItems: 'center', borderColor: '#c9bfae', borderRadius: 10, borderWidth: 1, height: 36, justifyContent: 'center', width: 36 },
  stepperButtonText: { color: couleurs.encre, fontSize: 18, fontWeight: '800' },
  stepperValue: { color: couleurs.encre, fontSize: 16, fontWeight: '800', minWidth: 30, textAlign: 'center' },
});
