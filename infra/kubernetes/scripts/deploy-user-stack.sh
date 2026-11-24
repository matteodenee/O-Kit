#!/bin/sh

set -eu

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <immutable-git-sha-tag>" >&2
  exit 1
fi

IMAGE_TAG=$1
NAMESPACE=${NAMESPACE:-polytech-dams5-07}
REGISTRY=${REGISTRY:-registry.gitlab.com/iwa-group2/o-kit}
SCRIPT_DIRECTORY=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPOSITORY_ROOT=$(CDPATH= cd -- "$SCRIPT_DIRECTORY/../../.." && pwd)
RENDER_DIRECTORY=$(mktemp -d)

cleanup() {
  rm -r "$RENDER_DIRECTORY"
}
trap cleanup EXIT

require_secret_key() {
  secret_name=$1
  secret_key=$2
  value=$(kubectl -n "$NAMESPACE" get secret "$secret_name" \
    -o "go-template={{ index .data \"$secret_key\" }}")
  if [ -z "$value" ]; then
    echo "Secret $secret_name is missing key $secret_key." >&2
    exit 1
  fi
}

render_component() {
  source_path=$1
  image_name=$2
  output_path=$3
  kubectl kustomize "$source_path" | \
    sed "s|registry.example.invalid/okit/$image_name:replace-me|$REGISTRY/$image_name:$IMAGE_TAG|g" \
    >"$output_path"
  if grep -q 'replace-me\|registry.example.invalid' "$output_path"; then
    echo "Unresolved image placeholder in $output_path." >&2
    exit 1
  fi
}

validate_manifest() {
  manifest=$1
  kubectl apply --dry-run=server -f "$manifest" >/dev/null
  diff_status=0
  kubectl diff -f "$manifest" || diff_status=$?
  if [ "$diff_status" -gt 1 ]; then
    echo "kubectl diff failed for $manifest with status $diff_status." >&2
    exit "$diff_status"
  fi
}

require_secret_key gitlab-registry .dockerconfigjson
require_secret_key keycloak-secrets database-password
require_secret_key keycloak-secrets bootstrap-admin-username
require_secret_key keycloak-secrets bootstrap-admin-password
require_secret_key keycloak-secrets ms-commande-client-secret
require_secret_key user-service-secrets database-password

cd "$REPOSITORY_ROOT"

render_component infra/keycloak keycloak "$RENDER_DIRECTORY/keycloak.yaml"
render_component k8s/services/ms-utilisateur user-service "$RENDER_DIRECTORY/user-service.yaml"
render_component infra/kubernetes/gateway api-gateway "$RENDER_DIRECTORY/api-gateway.yaml"

validate_manifest "$RENDER_DIRECTORY/keycloak.yaml"
validate_manifest "$RENDER_DIRECTORY/user-service.yaml"
validate_manifest "$RENDER_DIRECTORY/api-gateway.yaml"

kubectl apply -f "$RENDER_DIRECTORY/keycloak.yaml"
kubectl -n "$NAMESPACE" rollout status statefulset/keycloak-postgres --timeout=5m
kubectl -n "$NAMESPACE" rollout status deployment/keycloak --timeout=8m
kubectl -n "$NAMESPACE" exec deployment/keycloak -- \
  /opt/keycloak/bin/reconcile-kubernetes-realm.sh

NAMESPACE=$NAMESPACE "$SCRIPT_DIRECTORY/provision-user-database.sh"

kubectl apply -f "$RENDER_DIRECTORY/user-service.yaml"
kubectl -n "$NAMESPACE" rollout status deployment/user-service --timeout=5m

kubectl apply -f "$RENDER_DIRECTORY/api-gateway.yaml"
kubectl -n "$NAMESPACE" rollout status deployment/api-gateway --timeout=5m

echo "User stack deployed with immutable tag $IMAGE_TAG."
