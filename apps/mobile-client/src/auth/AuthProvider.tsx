import {
  createContext,
  PropsWithChildren,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import * as AuthSession from 'expo-auth-session';
import * as WebBrowser from 'expo-web-browser';

import {
  clientId,
  createRedirectUri,
  oidcDiscovery,
  registrationDiscovery,
} from './config';
import {
  clearStoredSession,
  loadStoredSession,
  saveStoredSession,
  StoredSession,
} from './sessionStorage';

WebBrowser.maybeCompleteAuthSession();

type KeycloakUser = {
  sub: string;
  email?: string;
  preferred_username?: string;
  given_name?: string;
  family_name?: string;
};

type KeycloakAccessToken = {
  realm_access?: {
    roles?: string[];
  };
};

type AuthContextValue = {
  accessToken: string | null;
  authenticatedFetch: AuthenticatedFetch;
  canAuthenticate: boolean;
  error: string | null;
  isBusy: boolean;
  isReady: boolean;
  login: () => Promise<void>;
  logout: () => Promise<void>;
  registerAccount: () => Promise<void>;
  user: KeycloakUser | null;
};

export type AuthenticatedFetch = typeof fetch;

const AuthContext = createContext<AuthContextValue | null>(null);

function toStoredSession(
  tokens: AuthSession.TokenResponse,
  previousSession?: StoredSession
): StoredSession {
  return {
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken ?? previousSession?.refreshToken,
    idToken: tokens.idToken ?? previousSession?.idToken,
    issuedAt: tokens.issuedAt,
    expiresIn: tokens.expiresIn,
  };
}

function readRealmRoles(accessToken: string): string[] {
  try {
    const encodedPayload = accessToken.split('.')[1];
    if (!encodedPayload) {
      return [];
    }
    const normalized = encodedPayload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
    const payload = JSON.parse(atob(padded)) as KeycloakAccessToken;
    return payload.realm_access?.roles ?? [];
  } catch {
    return [];
  }
}

function requireExclusiveBuyer(accessToken: string) {
  const roles = readRealmRoles(accessToken);
  if (!roles.includes('BUYER') || roles.includes('SELLER')) {
    throw new Error("Ce compte n'est pas autorisé à utiliser l'application acheteur.");
  }
}

function refreshMarginSeconds(expiresIn?: number) {
  return expiresIn ? Math.min(60, Math.max(1, Math.floor(expiresIn / 2))) : 60;
}

async function loadUser(accessToken: string): Promise<KeycloakUser> {
  const response = await fetch(oidcDiscovery.userInfoEndpoint!, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (!response.ok) {
    throw new Error(`Impossible de lire l'identité (${response.status}).`);
  }

  return response.json() as Promise<KeycloakUser>;
}

export function AuthProvider({ children }: PropsWithChildren) {
  const redirectUri = useMemo(() => createRedirectUri(), []);
  const handledCodes = useRef(new Set<string>());
  const sessionRef = useRef<StoredSession | null>(null);
  const sessionRevision = useRef(0);
  const refreshPromise = useRef<Promise<StoredSession> | null>(null);
  const [session, setSession] = useState<StoredSession | null>(null);
  const [user, setUser] = useState<KeycloakUser | null>(null);
  const [isReady, setIsReady] = useState(false);
  const [isBusy, setIsBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const authRequestConfig: AuthSession.AuthRequestConfig = useMemo(
    () => ({
      clientId,
      redirectUri,
      scopes: ['openid', 'profile', 'email'],
      responseType: AuthSession.ResponseType.Code,
      usePKCE: true,
      codeChallengeMethod: AuthSession.CodeChallengeMethod.S256,
    }),
    [redirectUri]
  );

  const [loginRequest, , promptLogin] = AuthSession.useAuthRequest(
    authRequestConfig,
    oidcDiscovery
  );
  const [registerRequest, , promptRegistration] =
    AuthSession.useAuthRequest(authRequestConfig, registrationDiscovery);

  const persistSession = useCallback(async (storedSession: StoredSession) => {
    sessionRef.current = storedSession;
    setSession(storedSession);
    await saveStoredSession(storedSession);
  }, []);

  const clearSession = useCallback(async (message: string | null = null) => {
    sessionRevision.current += 1;
    sessionRef.current = null;
    setSession(null);
    setUser(null);
    setError(message);
    await clearStoredSession();
  }, []);

  const refreshStoredSession = useCallback(
    async (storedSession: StoredSession, force = false) => {
      const margin = refreshMarginSeconds(storedSession.expiresIn);
      if (!force && AuthSession.TokenResponse.isTokenFresh(storedSession, -margin)) {
        return storedSession;
      }
      if (!storedSession.refreshToken) {
        throw new Error('Ta session a expiré. Reconnecte-toi.');
      }
      if (refreshPromise.current) {
        return refreshPromise.current;
      }

      const revision = sessionRevision.current;
      const pendingRefresh = AuthSession.refreshAsync(
        {
          clientId,
          refreshToken: storedSession.refreshToken,
        },
        oidcDiscovery
      )
        .then(async (tokens) => {
          if (revision !== sessionRevision.current) {
            throw new Error('La session a changé pendant son renouvellement.');
          }
          const refreshedSession = toStoredSession(tokens, storedSession);
          requireExclusiveBuyer(refreshedSession.accessToken);
          await persistSession(refreshedSession);
          return refreshedSession;
        })
        .finally(() => {
          refreshPromise.current = null;
        });

      refreshPromise.current = pendingRefresh;
      return pendingRefresh;
    },
    [persistSession]
  );

  const establishSession = useCallback(
    async (storedSession: StoredSession) => {
      const currentSession = await refreshStoredSession(storedSession);
      requireExclusiveBuyer(currentSession.accessToken);
      const identity = await loadUser(currentSession.accessToken);
      await persistSession(currentSession);
      setUser(identity);
    },
    [persistSession, refreshStoredSession]
  );

  useEffect(() => {
    let active = true;

    loadStoredSession()
      .then(async (storedSession) => {
        if (storedSession && active) {
          await establishSession(storedSession);
        }
      })
      .catch(async (restoreError: unknown) => {
        await clearSession();
        if (active && restoreError instanceof Error) {
          setError(restoreError.message);
        }
      })
      .finally(() => {
        if (active) {
          setIsReady(true);
        }
      });

    return () => {
      active = false;
    };
  }, [clearSession, establishSession]);

  const getCurrentSession = useCallback(
    async (forceRefresh = false) => {
      const currentSession = sessionRef.current;
      if (!currentSession) {
        return null;
      }

      try {
        return await refreshStoredSession(currentSession, forceRefresh);
      } catch {
        if (sessionRef.current) {
          await clearSession('Ta session a expiré. Reconnecte-toi.');
        }
        return null;
      }
    },
    [clearSession, refreshStoredSession]
  );

  const authenticatedFetch = useCallback<AuthenticatedFetch>(
    async (input, init) => {
      const execute = (currentSession: StoredSession) => {
        const headers = new Headers(init?.headers);
        headers.set('Authorization', `Bearer ${currentSession.accessToken}`);
        return fetch(input, { ...init, headers });
      };

      const currentSession = await getCurrentSession();
      if (!currentSession) {
        throw new Error('Une connexion est nécessaire.');
      }

      const firstResponse = await execute(currentSession);
      if (firstResponse.status !== 401) {
        return firstResponse;
      }

      const refreshedSession = await getCurrentSession(true);
      if (!refreshedSession) {
        return firstResponse;
      }

      const secondResponse = await execute(refreshedSession);
      if (secondResponse.status === 401) {
        await clearSession('Ta session a expiré. Reconnecte-toi.');
      }
      return secondResponse;
    },
    [clearSession, getCurrentSession]
  );

  useEffect(() => {
    if (!session?.expiresIn) {
      return;
    }

    const margin = refreshMarginSeconds(session.expiresIn);
    const refreshAt = (session.issuedAt + session.expiresIn - margin) * 1000;
    const delay = Math.max(0, refreshAt - Date.now());
    const timer = setTimeout(() => {
      void getCurrentSession(true);
    }, delay);

    return () => clearTimeout(timer);
  }, [getCurrentSession, session?.expiresIn, session?.issuedAt]);

  const completeAuthentication = useCallback(
    async (
      response: AuthSession.AuthSessionResult | null,
      request: AuthSession.AuthRequest | null
    ) => {
      if (!response || !request) {
        return;
      }

      if (response.type === 'cancel' || response.type === 'dismiss') {
        setIsBusy(false);
        return;
      }

      if (response.type === 'error') {
        setError(response.error?.description ?? 'Authentification refusée par Keycloak.');
        setIsBusy(false);
        return;
      }

      if (response.type !== 'success') {
        return;
      }

      const code = response.params.code;
      if (!code || handledCodes.current.has(code)) {
        return;
      }
      handledCodes.current.add(code);

      try {
        setIsBusy(true);
        setError(null);
        if (!request.codeVerifier) {
          throw new Error('La vérification PKCE est introuvable.');
        }

        const tokens = await AuthSession.exchangeCodeAsync(
          {
            clientId,
            code,
            redirectUri,
            extraParams: { code_verifier: request.codeVerifier },
          },
          oidcDiscovery
        );
        await establishSession(toStoredSession(tokens));
      } catch (authenticationError) {
        setError(
          authenticationError instanceof Error
            ? authenticationError.message
            : 'Connexion impossible.'
        );
      } finally {
        setIsBusy(false);
      }
    },
    [establishSession, redirectUri]
  );

  const login = useCallback(async () => {
    setError(null);
    setIsBusy(true);
    try {
      const response = await promptLogin();
      await completeAuthentication(response, loginRequest);
    } catch {
      setError("Le service d'authentification n'est pas disponible.");
      setIsBusy(false);
    }
  }, [completeAuthentication, loginRequest, promptLogin]);

  const registerAccount = useCallback(async () => {
    setError(null);
    setIsBusy(true);
    try {
      const response = await promptRegistration();
      await completeAuthentication(response, registerRequest);
    } catch {
      setError("Le service d'inscription n'est pas disponible.");
      setIsBusy(false);
    }
  }, [completeAuthentication, promptRegistration, registerRequest]);

  const logout = useCallback(async () => {
    const refreshToken = session?.refreshToken;
    await clearSession();

    if (refreshToken && oidcDiscovery.endSessionEndpoint) {
      try {
        await fetch(oidcDiscovery.endSessionEndpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: new URLSearchParams({ client_id: clientId, refresh_token: refreshToken }).toString(),
        });
      } catch {
        // La session locale est déjà supprimée ; Keycloak pourra expirer la session distante.
      }
    }
  }, [clearSession, session?.refreshToken]);

  const value = useMemo<AuthContextValue>(
    () => ({
      accessToken: session?.accessToken ?? null,
      authenticatedFetch,
      canAuthenticate: Boolean(loginRequest && registerRequest),
      error,
      isBusy,
      isReady,
      login,
      logout,
      registerAccount,
      user,
    }),
    [
      authenticatedFetch,
      error,
      isBusy,
      isReady,
      login,
      loginRequest,
      logout,
      registerAccount,
      registerRequest,
      session?.accessToken,
      user,
    ]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth doit être utilisé dans AuthProvider.');
  }
  return context;
}
