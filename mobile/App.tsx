import { useEffect, useRef, useState } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';
import * as AuthSession from 'expo-auth-session';
import * as WebBrowser from 'expo-web-browser';

WebBrowser.maybeCompleteAuthSession();

const clientId = 'okit-web-dev';
const redirectUri = 'http://localhost:8081/';

const oidcUrl =
  'http://localhost:8080/realms/okit/protocol/openid-connect';

const discovery = {
  authorizationEndpoint: `${oidcUrl}/auth`,
  tokenEndpoint: `${oidcUrl}/token`,
};

export default function App() {
  const [username, setUsername] = useState<string | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const handledCode = useRef<string | null>(null);

  const [request, response, promptAsync] = AuthSession.useAuthRequest(
    {
      clientId,
      redirectUri,
      scopes: ['openid', 'profile', 'email'],
      responseType: AuthSession.ResponseType.Code,
      usePKCE: true,
      codeChallengeMethod: AuthSession.CodeChallengeMethod.S256,
    },
    discovery
  );

  useEffect(() => {
    if (response?.type === 'error') {
      setError('La connexion a été refusée par Keycloak.');
      return;
    }

    if (response?.type !== 'success' || !request) return;

    const code = response.params.code;

    // Évite d'échanger deux fois le même code pendant le développement.
    if (!code || handledCode.current === code) return;
    handledCode.current = code;

    async function terminerConnexion() {
      try {
        setLoading(true);
        setError(null);

        if (!request?.codeVerifier) {
          throw new Error('Vérification PKCE introuvable.');
        }

        const tokens = await AuthSession.exchangeCodeAsync(
          {
            clientId,
            code,
            redirectUri,
            extraParams: {
              code_verifier: request.codeVerifier,
            },
          },
          discovery
        );

        const profilResponse = await fetch(`${oidcUrl}/userinfo`, {
          headers: {
            Authorization: `Bearer ${tokens.accessToken}`,
          },
        });

        if (!profilResponse.ok) {
          throw new Error(
            `Impossible de lire le profil (${profilResponse.status}).`
          );
        }

        const profil = await profilResponse.json();

        setAccessToken(tokens.accessToken);
        setUsername(
          profil.preferred_username ?? profil.email ?? profil.sub
        );
      } catch (err) {
        setError(
          err instanceof Error ? err.message : 'Connexion impossible.'
        );
      } finally {
        setLoading(false);
      }
    }

    void terminerConnexion();
  }, [response, request]);

  return (
    <View style={styles.container}>
      <Text style={styles.title}>O’kit</Text>

      {accessToken ? (
        <Text>Connecté : {username}</Text>
      ) : (
        <Button
          title={loading ? 'Connexion en cours...' : 'Se connecter'}
          disabled={!request || loading}
          onPress={() => {
            setError(null);
            void promptAsync();
          }}
        />
      )}

      {error && <Text style={styles.error}>{error}</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    gap: 24,
    padding: 24,
  },
  title: {
    fontSize: 32,
    fontWeight: 'bold',
  },
  error: {
    color: 'crimson',
    textAlign: 'center',
  },
});