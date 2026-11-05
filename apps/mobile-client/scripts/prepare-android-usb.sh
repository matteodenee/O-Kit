#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIRECTORY=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
PROJECT_DIRECTORY=$(CDPATH= cd -- "$SCRIPT_DIRECTORY/.." && pwd)

read_local_env() {
  requested_key=$1
  local_env_file=$PROJECT_DIRECTORY/.env.local
  if [ ! -f "$local_env_file" ]; then
    return 0
  fi
  while IFS='=' read -r key value; do
    if [ "$key" = "$requested_key" ]; then
      printf '%s' "$value"
      return 0
    fi
  done < "$local_env_file"
  return 0
}

KEYCLOAK_URL=${EXPO_PUBLIC_KEYCLOAK_URL:-$(read_local_env EXPO_PUBLIC_KEYCLOAK_URL)}
KEYCLOAK_URL=${KEYCLOAK_URL:-http://localhost:8090}
API_URL=${EXPO_PUBLIC_API_URL:-$(read_local_env EXPO_PUBLIC_API_URL)}
API_URL=${API_URL:-http://localhost:8080}
EXPECTED_ISSUER=http://localhost:8090/realms/okit
ANDROID_PACKAGE=com.okit.mobile

for required_command in adb curl node; do
  if ! command -v "$required_command" >/dev/null 2>&1; then
    echo "Commande requise introuvable : $required_command" >&2
    exit 1
  fi
done

if [ "$KEYCLOAK_URL" != "http://localhost:8090" ] || [ "$API_URL" != "http://localhost:8080" ]; then
  cat >&2 <<EOF
Le mode USB exige les URL locales suivantes :
EXPO_PUBLIC_KEYCLOAK_URL=http://localhost:8090
EXPO_PUBLIC_API_URL=http://localhost:8080

Corrige apps/mobile-client/.env.local ou supprime ce fichier pour utiliser les valeurs par défaut.
EOF
  exit 1
fi

if [ "$(adb get-state 2>/dev/null || true)" != "device" ]; then
  echo "Aucun appareil Android autorisé n'est visible par ADB." >&2
  echo "Active le débogage USB, branche le téléphone et accepte son empreinte RSA." >&2
  exit 1
fi

if ! adb shell pm list packages "$ANDROID_PACKAGE" | grep -Fx "package:$ANDROID_PACKAGE" >/dev/null; then
  echo "Le development build $ANDROID_PACKAGE n'est pas installé." >&2
  echo "Installe-le une première fois avec : npm run android" >&2
  exit 1
fi

if ! adb shell cmd package resolve-activity --brief \
  -a android.intent.action.VIEW -d 'okit://auth' | grep -F "$ANDROID_PACKAGE/.MainActivity" >/dev/null; then
  echo "Le build installé ne gère pas le callback OIDC okit://auth." >&2
  echo "Reconstruis-le avec : npm run android" >&2
  exit 1
fi

discovery_document=$(curl --silent --show-error --fail \
  "$KEYCLOAK_URL/realms/okit/.well-known/openid-configuration")

DISCOVERY_DOCUMENT=$discovery_document node - "$EXPECTED_ISSUER" <<'NODE'
const expectedIssuer = process.argv[2];
const discovery = JSON.parse(process.env.DISCOVERY_DOCUMENT);
if (discovery.issuer !== expectedIssuer) {
  throw new Error(`Issuer OIDC inattendu : ${discovery.issuer}`);
}
if (!(discovery.code_challenge_methods_supported ?? []).includes('S256')) {
  throw new Error('Keycloak n\'annonce pas PKCE S256.');
}
NODE

curl --silent --show-error --fail "$API_URL/health" >/dev/null

adb reverse tcp:8090 tcp:8090 >/dev/null
adb reverse tcp:8080 tcp:8080 >/dev/null
adb reverse tcp:19006 tcp:19006 >/dev/null

echo "Tunnels Android actifs :"
adb reverse --list
echo
echo "Keycloak : $KEYCLOAK_URL (issuer $EXPECTED_ISSUER)"
echo "Gateway  : $API_URL"
echo "Metro    : http://localhost:19006"
