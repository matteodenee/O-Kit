import Keycloak from 'keycloak-js';

const keycloakUrl = import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8090';
const realm = import.meta.env.VITE_KEYCLOAK_REALM ?? 'okit';

export const keycloak = new Keycloak({
  url: keycloakUrl.replace(/\/$/, ''),
  realm,
  clientId: 'okit-seller-web',
});

export function isExclusiveSeller() {
  return keycloak.hasRealmRole('SELLER') && !keycloak.hasRealmRole('BUYER');
}

export function applicationUrl() {
  return new URL(import.meta.env.BASE_URL, window.location.origin).toString();
}
