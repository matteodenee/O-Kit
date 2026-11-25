#!/usr/bin/env sh
set -eu

: "${MCP_TOKEN:?Définir MCP_TOKEN avec un JWT Keycloak BUYER valide}"

mcp_url=${MCP_URL:-http://127.0.0.1:8080/mcp}
demo_dir=$(mktemp -d)
trap 'rm -rf -- "$demo_dir"' EXIT

common_headers() {
  printf '%s\n' \
    "Authorization: Bearer $MCP_TOKEN" \
    "Content-Type: application/json" \
    "Accept: application/json, text/event-stream" \
    "MCP-Protocol-Version: 2025-03-26"
}

authorization=$(common_headers | sed -n '1p')
content_type=$(common_headers | sed -n '2p')
accept=$(common_headers | sed -n '3p')
protocol=$(common_headers | sed -n '4p')

decode_mcp_response() {
  payload=$(cat)
  case "$payload" in
    data:*|*data:*)
      printf '%s\n' "$payload" | sed -n 's/^data:[[:space:]]*//p'
      ;;
    *)
      printf '%s\n' "$payload"
      ;;
  esac
}

curl --fail-with-body --silent --show-error \
  -D "$demo_dir/headers" -o "$demo_dir/initialize.json" \
  -X POST "$mcp_url" \
  -H "$authorization" -H "$content_type" -H "$accept" -H "$protocol" \
  --data '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"okit-demo","version":"1.0"}}}'

session_id=$(awk 'tolower($1) == "mcp-session-id:" {gsub("\r", "", $2); print $2; exit}' "$demo_dir/headers")
if [ -z "$session_id" ]; then
  echo "Le serveur n'a pas retourné de session MCP" >&2
  exit 1
fi

curl --fail-with-body --silent --show-error -o /dev/null \
  -X POST "$mcp_url" \
  -H "$authorization" -H "$content_type" -H "$accept" -H "$protocol" \
  -H "Mcp-Session-Id: $session_id" \
  --data '{"jsonrpc":"2.0","method":"notifications/initialized","params":{}}'

echo "=== tools/list ==="
curl --fail-with-body --silent --show-error \
  -X POST "$mcp_url" \
  -H "$authorization" -H "$content_type" -H "$accept" -H "$protocol" \
  -H "Mcp-Session-Id: $session_id" \
  --data '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}' \
  | decode_mcp_response | jq .

echo "=== tools/call search_products ==="
curl --fail-with-body --silent --show-error \
  -X POST "$mcp_url" \
  -H "$authorization" -H "$content_type" -H "$accept" -H "$protocol" \
  -H "Mcp-Session-Id: $session_id" \
  --data '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"search_products","arguments":{"query":"maillot","maxPriceTtc":120}}}' \
  | decode_mcp_response | jq .

if [ -n "${ORDER_ID:-}" ]; then
  echo "=== tools/call get_order_status ==="
  curl --fail-with-body --silent --show-error \
    -X POST "$mcp_url" \
    -H "$authorization" -H "$content_type" -H "$accept" -H "$protocol" \
    -H "Mcp-Session-Id: $session_id" \
    --data "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"get_order_status\",\"arguments\":{\"orderId\":$ORDER_ID}}}" \
    | decode_mcp_response | jq .
fi

curl --silent --show-error -o /dev/null \
  -X DELETE "$mcp_url" \
  -H "$authorization" -H "$accept" -H "$protocol" \
  -H "Mcp-Session-Id: $session_id" || true
