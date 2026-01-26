#!/bin/bash
# Debezium Outbox Connector 등록 스크립트
# Usage: ./register-connector.sh

set -e

DEBEZIUM_HOST=${DEBEZIUM_HOST:-localhost}
DEBEZIUM_PORT=${DEBEZIUM_PORT:-8083}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "⏳ Waiting for Debezium Connect to be ready..."
until curl -s "http://${DEBEZIUM_HOST}:${DEBEZIUM_PORT}/connectors" > /dev/null 2>&1; do
    echo "   Debezium not ready yet, retrying in 5s..."
    sleep 5
done

echo "✅ Debezium Connect is ready!"

echo "📦 Registering Outbox connector..."
curl -X POST \
    -H "Content-Type: application/json" \
    --data @"${SCRIPT_DIR}/register-outbox-connector.json" \
    "http://${DEBEZIUM_HOST}:${DEBEZIUM_PORT}/connectors"

echo ""
echo "✅ Connector registered!"
echo ""
echo "📊 Connector status:"
curl -s "http://${DEBEZIUM_HOST}:${DEBEZIUM_PORT}/connectors/ivm-outbox-connector/status" | jq .

echo ""
echo "🎉 Done! Events from 'outbox' table will be published to Kafka topics:"
echo "   - ivm.events.raw_data"
echo "   - ivm.events.slice"
