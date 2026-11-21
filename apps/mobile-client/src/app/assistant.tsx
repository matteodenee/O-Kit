import { Redirect } from 'expo-router';
import { Fragment, useRef, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';

import { envoyerQuestion, MESSAGE_MAX, MessageChat } from '../api/chat';
import { useAuth } from '../auth/AuthProvider';
import { BoutonLien } from '../commande/BoutonLien';
import { couleurs, ui } from '../commande/ui';

const SUGGESTIONS = [
  'Où en est ma dernière commande ?',
  'Recommande-moi un maillot à moins de 100 €',
  'Comment retourner un article ?',
];

const ACCUEIL =
  'Bonjour ! Je peux t’aider à suivre tes commandes, répondre à tes questions sur les retours, ' +
  'te renseigner sur un produit ou te recommander un maillot.';

/** Affiche le texte de l'assistant : retours à la ligne, puces « - » et passages en **gras**. */
function TexteAssistant({ texte }: { texte: string }) {
  return (
    <>
      {texte.split('\n').map((ligne, index) => {
        const puce = /^\s*[-*•]\s+/u.test(ligne);
        const contenu = ligne.replace(/^\s*[-*•]\s+/u, '');
        const morceaux = contenu.split(/\*\*(.+?)\*\*/gu);
        return (
          <Text key={index} style={[styles.texteBulle, puce && styles.puce]}>
            {puce ? '•  ' : null}
            {morceaux.map((morceau, i) =>
              i % 2 === 1 ? (
                <Text key={i} style={styles.gras}>
                  {morceau}
                </Text>
              ) : (
                <Fragment key={i}>{morceau}</Fragment>
              )
            )}
          </Text>
        );
      })}
    </>
  );
}

export default function AssistantScreen() {
  const { accessToken, authenticatedFetch, isReady } = useAuth();
  const [messages, setMessages] = useState<MessageChat[]>([]);
  const [saisie, setSaisie] = useState('');
  const [enCours, setEnCours] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const defilement = useRef<ScrollView>(null);

  async function envoyer(texte: string) {
    const question = texte.trim();
    if (!question || enCours) return;
    const historique = messages;
    setMessages([...historique, { role: 'UTILISATEUR', contenu: question }]);
    setSaisie('');
    setErreur(null);
    setEnCours(true);
    try {
      const reponse = await envoyerQuestion(authenticatedFetch, question, historique);
      setMessages((courants) => [...courants, { role: 'ASSISTANT', contenu: reponse }]);
    } catch (sendError) {
      // La question sans réponse est retirée et remise dans le champ, pour pouvoir la renvoyer.
      setMessages(historique);
      setSaisie(question);
      setErreur(sendError instanceof Error ? sendError.message : 'L’assistant n’a pas pu répondre.');
    } finally {
      setEnCours(false);
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

  const peutEnvoyer = saisie.trim().length > 0 && !enCours;

  return (
    <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.ecran}>
      <ScrollView
        contentContainerStyle={ui.page}
        keyboardShouldPersistTaps="handled"
        onContentSizeChange={() => defilement.current?.scrollToEnd({ animated: true })}
        ref={defilement}
      >
        <View style={ui.container}>
          <View style={ui.header}>
            <View style={ui.headerCopy}>
              <Text style={ui.eyebrow}>AIDE</Text>
              <Text style={ui.title}>Assistant O’Kit</Text>
              <Text style={ui.subtitle}>Commandes, retours, produits : pose ta question.</Text>
            </View>
            <View style={ui.headerActions}>
              <BoutonLien href="/catalogue" label="Catalogue" />
              <BoutonLien href="/commandes" label="Mes commandes" />
            </View>
          </View>

          <View style={styles.fil}>
            <View style={[styles.bulle, styles.bulleAssistant]}>
              <Text style={styles.texteBulle}>{ACCUEIL}</Text>
            </View>

            {messages.map((message, index) =>
              message.role === 'UTILISATEUR' ? (
                <View key={index} style={[styles.bulle, styles.bulleUtilisateur]}>
                  <Text style={[styles.texteBulle, styles.texteUtilisateur]}>{message.contenu}</Text>
                </View>
              ) : (
                <View key={index} style={[styles.bulle, styles.bulleAssistant]}>
                  <TexteAssistant texte={message.contenu} />
                </View>
              )
            )}

            {enCours ? (
              <View accessibilityLabel="L’assistant rédige sa réponse" style={[styles.bulle, styles.bulleAssistant, styles.attente]}>
                <ActivityIndicator color={couleurs.accent} size="small" />
                <Text style={ui.muted}>L’assistant réfléchit…</Text>
              </View>
            ) : null}
          </View>

          {messages.length === 0 && !enCours ? (
            <View style={styles.suggestions}>
              {SUGGESTIONS.map((suggestion) => (
                <Pressable
                  accessibilityRole="button"
                  key={suggestion}
                  onPress={() => void envoyer(suggestion)}
                  style={({ pressed }) => [styles.suggestion, pressed && ui.pressed]}
                >
                  <Text style={styles.texteSuggestion}>{suggestion}</Text>
                </Pressable>
              ))}
            </View>
          ) : null}

          {erreur ? (
            <View style={[ui.notice, ui.noticeError, styles.erreur]}>
              <Text style={ui.noticeErrorText}>{erreur}</Text>
            </View>
          ) : null}
        </View>
      </ScrollView>

      <View style={styles.barreSaisie}>
        <View style={[ui.container, styles.ligneSaisie]}>
          <TextInput
            accessibilityLabel="Ta question"
            editable={!enCours}
            maxLength={MESSAGE_MAX}
            multiline
            onChangeText={setSaisie}
            onSubmitEditing={() => void envoyer(saisie)}
            placeholder="Écris ta question…"
            placeholderTextColor={couleurs.attenue}
            returnKeyType="send"
            submitBehavior="submit"
            style={[ui.input, styles.champ]}
            value={saisie}
          />
          <Pressable
            accessibilityLabel="Envoyer"
            accessibilityRole="button"
            disabled={!peutEnvoyer}
            onPress={() => void envoyer(saisie)}
            style={({ pressed }) => [ui.primaryAction, !peutEnvoyer && ui.disabled, pressed && ui.pressed]}
          >
            <Text style={ui.primaryActionText}>Envoyer</Text>
          </Pressable>
        </View>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  ecran: { backgroundColor: couleurs.fond, flex: 1 },
  fil: { gap: 12 },
  bulle: { borderRadius: 18, maxWidth: '85%', paddingHorizontal: 16, paddingVertical: 12 },
  bulleAssistant: {
    alignSelf: 'flex-start',
    backgroundColor: couleurs.carte,
    borderBottomLeftRadius: 4,
    borderColor: couleurs.bordure,
    borderWidth: 1,
  },
  bulleUtilisateur: { alignSelf: 'flex-end', backgroundColor: couleurs.encre, borderBottomRightRadius: 4 },
  texteBulle: { color: couleurs.encre, fontSize: 15, lineHeight: 22 },
  texteUtilisateur: { color: couleurs.accentTexte },
  gras: { fontWeight: '800' },
  puce: { paddingLeft: 4 },
  attente: { alignItems: 'center', flexDirection: 'row', gap: 10 },
  suggestions: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginTop: 18 },
  suggestion: {
    backgroundColor: '#f6e3d6',
    borderColor: '#e8c9b4',
    borderRadius: 999,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  texteSuggestion: { color: couleurs.eyebrow, fontSize: 13, fontWeight: '800' },
  erreur: { marginBottom: 0, marginTop: 16 },
  barreSaisie: {
    backgroundColor: couleurs.carte,
    borderTopColor: couleurs.bordure,
    borderTopWidth: 1,
    paddingHorizontal: 20,
    paddingVertical: 12,
  },
  ligneSaisie: { alignItems: 'flex-end', flexDirection: 'row', gap: 10 },
  champ: { flex: 1, maxHeight: 120, paddingVertical: 13 },
});
