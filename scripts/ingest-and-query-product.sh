#!/bin/bash
# 샘플 Product Ingest + View Query 검증 스크립트
# Usage: ./scripts/ingest-and-query-product.sh [SAMPLE_FILE]
#   SAMPLE_FILE: .tmp/product/UA10476976.json (기본값)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
API_BASE="${API_BASE:-http://localhost:8080}"

SAMPLE_FILE="${1:-$PROJECT_ROOT/.tmp/product/UA10476976.json}"
ENTITY_ID="${ENTITY_ID:-$(basename "$SAMPLE_FILE" .json)}"
ENTITY_KEY="product:$ENTITY_ID"
TENANT_ID="${TENANT_ID:-oliveyoung}"
SCHEMA_ID="ruleset.product.oliveyoung.v1"
SCHEMA_VERSION="1.0.0"

if [[ ! -f "$SAMPLE_FILE" ]]; then
    echo "❌ 샘플 파일 없음: $SAMPLE_FILE"
    echo "   사용법: ./scripts/ingest-and-query-product.sh [.tmp/product/UA10476976.json]"
    exit 1
fi

echo "=========================================="
echo "Product Ingest + View Query 검증"
echo "=========================================="
echo "API: $API_BASE"
echo "샘플: $SAMPLE_FILE"
echo "entityKey: $ENTITY_KEY"
echo ""

# 1. Ingest
echo "📥 1. Ingest 실행..."
INGEST_JSON=$(jq -n \
    --arg tenantId "$TENANT_ID" \
    --arg entityKey "$ENTITY_KEY" \
    --arg schemaId "$SCHEMA_ID" \
    --arg schemaVersion "$SCHEMA_VERSION" \
    --slurpfile payload "$SAMPLE_FILE" \
    '{tenantId: $tenantId, entityKey: $entityKey, version: 0, schemaId: $schemaId, schemaVersion: $schemaVersion, payload: $payload[0]}')

INGEST_RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_BASE/api/v1/ingest" \
    -H "Content-Type: application/json" \
    -d "$INGEST_JSON")

HTTP_CODE=$(echo "$INGEST_RESP" | tail -n1)
INGEST_BODY=$(echo "$INGEST_RESP" | sed '$d')

if [[ "$HTTP_CODE" != "200" ]]; then
    echo "❌ Ingest 실패 (HTTP $HTTP_CODE)"
    echo "$INGEST_BODY" | jq . 2>/dev/null || echo "$INGEST_BODY"
    exit 1
fi

VERSION=$(echo "$INGEST_BODY" | jq -r '.version')
echo "   ✅ Ingest 성공 (version=$VERSION)"
echo "$INGEST_BODY" | jq '{success, entityKey, version, sliceCount, viewCount, durationMs}'
echo ""

# 2. View Query (view.product.search.v1)
echo "📤 2. View Query (view.product.search.v1)..."
QUERY_JSON=$(jq -n \
    --arg tenantId "$TENANT_ID" \
    --arg entityKey "$ENTITY_KEY" \
    --argjson version "$VERSION" \
    '{tenantId: $tenantId, viewId: "view.product.search.v1", entityKey: $entityKey, version: $version}')

QUERY_RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_BASE/api/v1/query" \
    -H "Content-Type: application/json" \
    -d "$QUERY_JSON")

QUERY_HTTP=$(echo "$QUERY_RESP" | tail -n1)
QUERY_BODY=$(echo "$QUERY_RESP" | sed '$d')

if [[ "$QUERY_HTTP" != "200" ]]; then
    echo "❌ View Query 실패 (HTTP $QUERY_HTTP)"
    echo "$QUERY_BODY" | jq . 2>/dev/null || echo "$QUERY_BODY"
    exit 1
fi

echo "   ✅ View Query 성공"
echo "$QUERY_BODY" | jq '.data | keys' 2>/dev/null || echo "$QUERY_BODY"
echo ""

# 3. View Query (view.product.pdp.v1)
echo "📤 3. View Query (view.product.pdp.v1)..."
QUERY2_JSON=$(jq -n \
    --arg tenantId "$TENANT_ID" \
    --arg entityKey "$ENTITY_KEY" \
    --argjson version "$VERSION" \
    '{tenantId: $tenantId, viewId: "view.product.pdp.v1", entityKey: $entityKey, version: $version}')

QUERY2_RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_BASE/api/v1/query" \
    -H "Content-Type: application/json" \
    -d "$QUERY2_JSON")

QUERY2_HTTP=$(echo "$QUERY2_RESP" | tail -n1)
QUERY2_BODY=$(echo "$QUERY2_RESP" | sed '$d')

if [[ "$QUERY2_HTTP" != "200" ]]; then
    echo "❌ View Query (PDP) 실패 (HTTP $QUERY2_HTTP)"
    echo "$QUERY2_BODY" | jq . 2>/dev/null || echo "$QUERY2_BODY"
    exit 1
fi

echo "   ✅ View Query (PDP) 성공"
echo "$QUERY2_BODY" | jq '.data | keys' 2>/dev/null || echo "$QUERY2_BODY"
echo ""

echo "=========================================="
echo "✅ Ingest + View Query 검증 완료"
echo "=========================================="
