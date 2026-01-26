#!/bin/bash
# ivm-lite 로컬 인프라 전체 설정
# Usage: ./setup-local.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "🚀 ivm-lite 로컬 인프라 설정 시작"
echo "=================================="
echo ""

# 1. Docker Compose 실행
echo "1️⃣ Docker Compose 시작..."
cd "$PROJECT_ROOT"
docker-compose up -d

echo ""
echo "⏳ 서비스들이 시작되기를 기다리는 중..."
sleep 10

# 2. DynamoDB 테이블 생성
echo ""
echo "2️⃣ DynamoDB Schema Registry 테이블 생성..."
chmod +x "$SCRIPT_DIR/dynamodb/create-tables.sh"
"$SCRIPT_DIR/dynamodb/create-tables.sh"

# 3. Debezium Connector 등록
echo ""
echo "3️⃣ Debezium Outbox Connector 등록..."
chmod +x "$SCRIPT_DIR/debezium/register-connector.sh"
"$SCRIPT_DIR/debezium/register-connector.sh"

echo ""
echo "=================================="
echo "🎉 로컬 인프라 설정 완료!"
echo ""
echo "📌 서비스 엔드포인트:"
echo "   - PostgreSQL:    localhost:5432 (ivm/ivm_local_dev/ivmlite)"
echo "   - DynamoDB:      localhost:8000"
echo "   - Kafka:         localhost:9094 (external)"
echo "   - Debezium:      localhost:8083"
echo "   - Kafka UI:      http://localhost:8080"
echo ""
echo "📌 Kafka Topics (Outbox 이벤트):"
echo "   - ivm.events.raw_data"
echo "   - ivm.events.slice"
echo ""
echo "📌 유용한 명령어:"
echo "   - docker-compose logs -f          # 전체 로그"
echo "   - docker-compose logs -f kafka    # Kafka 로그"
echo "   - docker-compose down             # 종료"
echo "   - docker-compose down -v          # 종료 + 볼륨 삭제"
