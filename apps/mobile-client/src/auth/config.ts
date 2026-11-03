import * as AuthSession from 'expo-auth-session';

function withoutTrailingSlash(value: string) {
  return value.replace(/\/+$/, '');
}

export const keycloakBaseUrl = withoutTrailingSlash(
  process.env.EXPO_PUBLIC_KEYCLOAK_URL ?? 'http://localhost:8090'
);

export const apiBaseUrl = withoutTrailingSlash(
  process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080'
);

export const keycloakIssuer = `${keycloakBaseUrl}/realms/okit`;
const oidcBaseUrl = `${keycloakIssuer}/protocol/openid-connect`;

export const oidcDiscovery: AuthSession.DiscoveryDocument = {
  authorizationEndpoint: `${oidcBaseUrl}/auth`,
  tokenEndpoint: `${oidcBaseUrl}/token`,
  userInfoEndpoint: `${oidcBaseUrl}/userinfo`,
  revocationEndpoint: `${oidcBaseUrl}/revoke`,
  endSessionEndpoint: `${oidcBaseUrl}/logout`,
};

export const registrationDiscovery: AuthSession.DiscoveryDocument = {
  ...oidcDiscovery,
  authorizationEndpoint: `${oidcBaseUrl}/registrations`,
};

export const clientId = 'okit-mobile';

export function createRedirectUri() {
  return AuthSession.makeRedirectUri({
    scheme: 'okit',
    path: 'auth',
    native: 'okit://auth',
  });
}
