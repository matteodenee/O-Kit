import { useEffect, useMemo, useState } from 'react';
import { Link } from 'expo-router';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  useWindowDimensions,
  View,
} from 'react-native';

import {
  getCurrentUserProfile,
  saveCurrentUserProfile,
  UserProfile,
  UserProfileInput,
} from '../api/userProfile';
import { useAuth } from '../auth/AuthProvider';

const emptyProfile: UserProfileInput = {
  firstName: '',
  lastName: '',
  phoneNumber: '',
  birthDate: null,
};

type ProfileState = 'idle' | 'loading' | 'missing' | 'saved' | 'error';

export default function HomeScreen() {
  const { width } = useWindowDimensions();
  const {
    accessToken,
    authenticatedFetch,
    canAuthenticate,
    error: authError,
    isBusy,
    isReady,
    login,
    logout,
    registerAccount,
    user,
  } = useAuth();

  const isWide = width >= 900;

  if (!isReady) {
    return (
      <View style={styles.loadingPage}>
        <ActivityIndicator color="#c95f2b" size="large" />
        <Text style={styles.muted}>Préparation de ton espace…</Text>
      </View>
    );
  }

  return (
    <ScrollView contentContainerStyle={styles.page}>
      <View style={[styles.shell, isWide && styles.shellWide]}>
        <View style={[styles.hero, isWide && styles.heroWide]}>
          <View style={styles.brandRow}>
            <View style={styles.brandMark}>
              <Text style={styles.brandMarkText}>O’</Text>
            </View>
            <Text style={styles.brand}>O’kit</Text>
          </View>

          <View style={styles.heroCopy}>
            <Text style={styles.eyebrow}>LE VESTIAIRE DES PASSIONNÉS</Text>
            <Text style={styles.heroTitle}>Ton maillot. Ton histoire.</Text>
            <Text style={styles.heroText}>
              Connecte-toi pour retrouver ton profil, préparer tes favoris et vivre
              O’kit sur le web comme sur mobile.
            </Text>
          </View>

          <View style={styles.featureRow}>
            <Feature number="01" label="Identité protégée par Keycloak" />
            <Feature number="02" label="Profil stocké dans ton microservice" />
          </View>
        </View>

        <View style={[styles.panel, isWide && styles.panelWide]}>
          {!accessToken ? (
            <View style={styles.panelContent}>
              <Text style={styles.panelEyebrow}>ESPACE CLIENT</Text>
              <Text style={styles.panelTitle}>Bienvenue dans l’équipe.</Text>
              <Text style={styles.panelText}>
                Utilise Keycloak pour te connecter ou créer ton compte. O’kit ne voit
                jamais ton mot de passe.
              </Text>

              <Pressable
                accessibilityRole="button"
                disabled={!canAuthenticate || isBusy}
                onPress={() => void login()}
                style={({ pressed }) => [
                  styles.primaryButton,
                  pressed && styles.buttonPressed,
                  (!canAuthenticate || isBusy) && styles.buttonDisabled,
                ]}
              >
                <Text style={styles.primaryButtonText}>
                  {isBusy ? 'Connexion en cours…' : 'Se connecter'}
                </Text>
              </Pressable>

              <Pressable
                accessibilityRole="button"
                disabled={!canAuthenticate || isBusy}
                onPress={() => void registerAccount()}
                style={({ pressed }) => [
                  styles.secondaryButton,
                  pressed && styles.buttonPressed,
                  (!canAuthenticate || isBusy) && styles.buttonDisabled,
                ]}
              >
                <Text style={styles.secondaryButtonText}>Créer un compte</Text>
              </Pressable>

              {authError ? <Notice tone="error" message={authError} /> : null}

              <View style={styles.securityNote}>
                <Text style={styles.securityIcon}>✓</Text>
                <Text style={styles.securityText}>
                  Connexion sécurisée par Authorization Code et PKCE.
                </Text>
              </View>
            </View>
          ) : (
            <AuthenticatedProfile
              authenticatedFetch={authenticatedFetch}
              logout={logout}
              user={user}
            />
          )}
        </View>
      </View>
    </ScrollView>
  );
}

type AuthenticatedProfileProps = {
  authenticatedFetch: ReturnType<typeof useAuth>['authenticatedFetch'];
  logout: () => Promise<void>;
  user: ReturnType<typeof useAuth>['user'];
};

function AuthenticatedProfile({ authenticatedFetch, logout, user }: AuthenticatedProfileProps) {
  const [form, setForm] = useState<UserProfileInput>({
    ...emptyProfile,
    firstName: user?.given_name ?? '',
    lastName: user?.family_name ?? '',
  });
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [profileState, setProfileState] = useState<ProfileState>('loading');
  const [profileMessage, setProfileMessage] = useState<string | null>(null);

  const displayName = useMemo(
    () =>
      [profile?.firstName ?? user?.given_name, profile?.lastName ?? user?.family_name]
        .filter(Boolean)
        .join(' ') ||
      user?.preferred_username ||
      user?.email ||
      'Supporter O’kit',
    [profile, user]
  );

  useEffect(() => {
    let active = true;

    getCurrentUserProfile(authenticatedFetch)
      .then((loadedProfile) => {
        if (!active) return;

        if (loadedProfile) {
          setProfile(loadedProfile);
          setForm({
            firstName: loadedProfile.firstName,
            lastName: loadedProfile.lastName,
            phoneNumber: loadedProfile.phoneNumber,
            birthDate: loadedProfile.birthDate,
          });
          setProfileState('saved');
          return;
        }

        setProfileState('missing');
        setProfileMessage('Complète ton profil pour finaliser ton compte O’kit.');
      })
      .catch(async (profileError: unknown) => {
        if (!active) return;
        setProfileState('error');
        setProfileMessage(
          profileError instanceof Error
            ? profileError.message
            : 'Le service utilisateur est indisponible.'
        );
      });

    return () => {
      active = false;
    };
  }, [authenticatedFetch]);

  function updateField<Key extends keyof UserProfileInput>(
    field: Key,
    value: UserProfileInput[Key]
  ) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  async function saveProfile() {
    if (!form.firstName.trim() || !form.lastName.trim() || !form.phoneNumber.trim()) {
      setProfileState('error');
      setProfileMessage('Prénom, nom et téléphone sont obligatoires.');
      return;
    }

    if (form.birthDate && !/^\d{4}-\d{2}-\d{2}$/.test(form.birthDate)) {
      setProfileState('error');
      setProfileMessage('La date de naissance doit respecter le format AAAA-MM-JJ.');
      return;
    }

    setProfileState('loading');
    setProfileMessage(null);
    try {
      const savedProfile = await saveCurrentUserProfile(authenticatedFetch, {
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        phoneNumber: form.phoneNumber.trim(),
        birthDate: form.birthDate || null,
      });
      setProfile(savedProfile);
      setForm({
        firstName: savedProfile.firstName,
        lastName: savedProfile.lastName,
        phoneNumber: savedProfile.phoneNumber,
        birthDate: savedProfile.birthDate,
      });
      setProfileState('saved');
      setProfileMessage('Ton profil est bien enregistré dans le service utilisateur.');
    } catch (profileError) {
      setProfileState('error');
      setProfileMessage(
        profileError instanceof Error
          ? profileError.message
          : "L'enregistrement est impossible."
      );
    }
  }

  return (
    <View style={styles.panelContent}>
      <View style={styles.profileHeader}>
        <View>
          <Text style={styles.panelEyebrow}>PROFIL CLIENT</Text>
          <Text style={styles.profileName}>{displayName}</Text>
          <Text style={styles.profileEmail}>{profile?.email ?? user?.email}</Text>
        </View>
        <Pressable accessibilityRole="button" onPress={() => void logout()}>
          <Text style={styles.logoutText}>Déconnexion</Text>
        </Pressable>
      </View>

      {profileState === 'loading' ? (
        <View style={styles.inlineLoading}>
          <ActivityIndicator color="#c95f2b" />
          <Text style={styles.muted}>Synchronisation du profil…</Text>
        </View>
      ) : null}

      <View style={styles.formGrid}>
        <Field
          label="Prénom"
          onChangeText={(value) => updateField('firstName', value)}
          value={form.firstName}
        />
        <Field
          label="Nom"
          onChangeText={(value) => updateField('lastName', value)}
          value={form.lastName}
        />
        <Field
          keyboardType="phone-pad"
          label="Téléphone"
          onChangeText={(value) => updateField('phoneNumber', value)}
          placeholder="+33 6 12 34 56 78"
          value={form.phoneNumber}
        />
        <Field
          label="Date de naissance"
          onChangeText={(value) => updateField('birthDate', value || null)}
          placeholder="AAAA-MM-JJ"
          value={form.birthDate ?? ''}
        />
      </View>

      {profileMessage ? (
        <Notice
          tone={profileState === 'error' ? 'error' : 'success'}
          message={profileMessage}
        />
      ) : null}

      <Pressable
        accessibilityRole="button"
        disabled={profileState === 'loading'}
        onPress={() => void saveProfile()}
        style={({ pressed }) => [
          styles.primaryButton,
          pressed && styles.buttonPressed,
          profileState === 'loading' && styles.buttonDisabled,
        ]}
      >
        <Text style={styles.primaryButtonText}>Enregistrer mon profil</Text>
      </Pressable>

      {profile ? (
        <Link asChild href="/addresses">
          <Pressable
            accessibilityRole="button"
            style={({ pressed }) => [styles.secondaryButton, pressed && styles.buttonPressed]}
          >
            <Text style={styles.secondaryButtonText}>Gérer mes adresses</Text>
          </Pressable>
        </Link>
      ) : null}

      <Text style={styles.dataNote}>
        Ton e-mail et ton mot de passe restent gérés par Keycloak. Le service utilisateur
        conserve uniquement les données utiles à ton profil O’kit.
      </Text>
    </View>
  );
}

function Feature({ number, label }: { number: string; label: string }) {
  return (
    <View style={styles.feature}>
      <Text style={styles.featureNumber}>{number}</Text>
      <Text style={styles.featureLabel}>{label}</Text>
    </View>
  );
}

function Notice({ message, tone }: { message: string; tone: 'error' | 'success' }) {
  return (
    <View style={[styles.notice, tone === 'error' ? styles.noticeError : styles.noticeSuccess]}>
      <Text style={tone === 'error' ? styles.noticeErrorText : styles.noticeSuccessText}>
        {message}
      </Text>
    </View>
  );
}

type FieldProps = {
  keyboardType?: 'default' | 'phone-pad';
  label: string;
  onChangeText: (value: string) => void;
  placeholder?: string;
  value: string;
};

function Field({ keyboardType = 'default', label, onChangeText, placeholder, value }: FieldProps) {
  return (
    <View style={styles.field}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <TextInput
        autoCapitalize={keyboardType === 'default' ? 'words' : 'none'}
        keyboardType={keyboardType}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor="#91897d"
        style={styles.input}
        value={value}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  page: {
    backgroundColor: '#f3eee4',
    flexGrow: 1,
    justifyContent: 'center',
    minHeight: '100%',
    padding: 20,
  },
  shell: {
    alignSelf: 'center',
    backgroundColor: '#132c27',
    borderRadius: 28,
    maxWidth: 1180,
    overflow: 'hidden',
    width: '100%',
  },
  shellWide: {
    flexDirection: 'row',
    minHeight: 700,
  },
  hero: {
    justifyContent: 'space-between',
    minHeight: 420,
    padding: 32,
  },
  heroWide: {
    flex: 1.08,
    padding: 56,
  },
  brandRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 12,
  },
  brandMark: {
    alignItems: 'center',
    backgroundColor: '#d56b35',
    borderRadius: 12,
    height: 42,
    justifyContent: 'center',
    transform: [{ rotate: '-5deg' }],
    width: 42,
  },
  brandMarkText: {
    color: '#fff8ed',
    fontSize: 21,
    fontWeight: '900',
  },
  brand: {
    color: '#fff8ed',
    fontSize: 25,
    fontWeight: '800',
    letterSpacing: -1,
  },
  heroCopy: {
    marginVertical: 48,
    maxWidth: 520,
  },
  eyebrow: {
    color: '#e08a5e',
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: 2.2,
    marginBottom: 18,
  },
  heroTitle: {
    color: '#fff8ed',
    fontSize: 52,
    fontWeight: '800',
    letterSpacing: -2.4,
    lineHeight: 55,
    maxWidth: 470,
  },
  heroText: {
    color: '#b7c8c1',
    fontSize: 17,
    lineHeight: 27,
    marginTop: 24,
    maxWidth: 480,
  },
  featureRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 26,
  },
  feature: {
    borderTopColor: '#36534d',
    borderTopWidth: 1,
    maxWidth: 210,
    paddingTop: 14,
  },
  featureNumber: {
    color: '#e08a5e',
    fontSize: 11,
    fontWeight: '800',
    marginBottom: 6,
  },
  featureLabel: {
    color: '#dce7e2',
    fontSize: 13,
    lineHeight: 18,
  },
  panel: {
    backgroundColor: '#fffaf1',
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    justifyContent: 'center',
    minHeight: 540,
  },
  panelWide: {
    borderBottomLeftRadius: 28,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 0,
    flex: 0.92,
  },
  panelContent: {
    alignSelf: 'center',
    maxWidth: 470,
    padding: 34,
    width: '100%',
  },
  panelEyebrow: {
    color: '#b75328',
    fontSize: 11,
    fontWeight: '900',
    letterSpacing: 1.8,
    marginBottom: 10,
  },
  panelTitle: {
    color: '#17322c',
    fontSize: 36,
    fontWeight: '800',
    letterSpacing: -1.4,
    lineHeight: 40,
  },
  panelText: {
    color: '#615f59',
    fontSize: 15,
    lineHeight: 23,
    marginBottom: 30,
    marginTop: 14,
  },
  primaryButton: {
    alignItems: 'center',
    backgroundColor: '#c95f2b',
    borderRadius: 14,
    justifyContent: 'center',
    marginTop: 14,
    minHeight: 54,
    paddingHorizontal: 22,
  },
  primaryButtonText: {
    color: '#fffaf1',
    fontSize: 16,
    fontWeight: '800',
  },
  secondaryButton: {
    alignItems: 'center',
    borderColor: '#c9bfae',
    borderRadius: 14,
    borderWidth: 1,
    justifyContent: 'center',
    marginTop: 12,
    minHeight: 54,
    paddingHorizontal: 22,
  },
  secondaryButtonText: {
    color: '#17322c',
    fontSize: 16,
    fontWeight: '800',
  },
  buttonPressed: {
    opacity: 0.8,
    transform: [{ scale: 0.99 }],
  },
  buttonDisabled: {
    opacity: 0.5,
  },
  securityNote: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 10,
    marginTop: 26,
  },
  securityIcon: {
    color: '#24705e',
    fontSize: 16,
    fontWeight: '900',
  },
  securityText: {
    color: '#79736a',
    flex: 1,
    fontSize: 12,
    lineHeight: 17,
  },
  profileHeader: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 22,
  },
  profileName: {
    color: '#17322c',
    fontSize: 27,
    fontWeight: '800',
    letterSpacing: -0.8,
  },
  profileEmail: {
    color: '#79736a',
    fontSize: 13,
    marginTop: 4,
  },
  logoutText: {
    color: '#b75328',
    fontSize: 13,
    fontWeight: '800',
    marginTop: 3,
  },
  inlineLoading: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 10,
    marginBottom: 16,
  },
  muted: {
    color: '#79736a',
    fontSize: 14,
  },
  formGrid: {
    gap: 15,
  },
  field: {
    gap: 7,
  },
  fieldLabel: {
    color: '#344b45',
    fontSize: 12,
    fontWeight: '800',
  },
  input: {
    backgroundColor: '#f5efe4',
    borderColor: '#ded3c2',
    borderRadius: 12,
    borderWidth: 1,
    color: '#17322c',
    fontSize: 15,
    minHeight: 49,
    paddingHorizontal: 15,
  },
  notice: {
    borderRadius: 11,
    marginTop: 16,
    padding: 13,
  },
  noticeError: {
    backgroundColor: '#fce7df',
  },
  noticeSuccess: {
    backgroundColor: '#dfeee8',
  },
  noticeErrorText: {
    color: '#9f3f20',
    fontSize: 13,
    lineHeight: 18,
  },
  noticeSuccessText: {
    color: '#236151',
    fontSize: 13,
    lineHeight: 18,
  },
  dataNote: {
    color: '#79736a',
    fontSize: 11,
    lineHeight: 17,
    marginTop: 18,
    textAlign: 'center',
  },
  loadingPage: {
    alignItems: 'center',
    backgroundColor: '#f3eee4',
    flex: 1,
    gap: 16,
    justifyContent: 'center',
  },
});
