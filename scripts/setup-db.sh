#!/bin/bash
# DB 설정 + jOOQ 코드 생성 스크립트
# 
# 이 스크립트는:
# 1. PostgreSQL이 실행 중인지 확인
# 2. Flyway 마이그레이션 실행
# 3. jOOQ 코드 생성
#
# Usage: ./scripts/setup-db.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

echo "🔧 ivm-lite DB 설정 + jOOQ 코드 생성"
echo "===================================="
echo ""

# 환경 변수 (기본값)
DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-5432}
DB_NAME=${DB_NAME:-ivmlite}
DB_USER=${DB_USER:-ivm}

# 1. PostgreSQL 연결 확인
echo "1️⃣ PostgreSQL 연결 확인..."
until pg_isready -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" > /dev/null 2>&1; do
    echo "   PostgreSQL not ready, retrying in 3s..."
    echo "   (docker-compose up -d postgres 실행했는지 확인하세요)"
    sleep 3
done
echo "   ✅ PostgreSQL 연결 성공!"
echo ""

# 2. Flyway 마이그레이션
echo "2️⃣ Flyway 마이그레이션 실행..."
./gradlew flywayMigrate --info
echo "   ✅ 마이그레이션 완료!"
echo ""

# 3. jOOQ 코드 생성
echo "3️⃣ jOOQ 코드 생성..."
./gradlew jooqCodegen
echo "   ✅ 코드 생성 완료!"
echo ""

# 4. 결과 확인
echo "4️⃣ 생성된 코드 확인..."
GENERATED_DIR="build/generated-src/jooq/main/com/oliveyoung/ivmlite/generated/jooq"
if [ -d "$GENERATED_DIR" ]; then
    echo "   📁 $GENERATED_DIR"
    find "$GENERATED_DIR" -name "*.kt" | head -10
    echo "   ... (더 있을 수 있음)"
else
    echo "   ⚠️ 생성된 코드가 없습니다. 로그를 확인하세요."
fi
echo ""

echo "===================================="
echo "🎉 완료!"
echo ""
echo "📌 사용 방법:"
echo "   1. 생성된 코드는 자동으로 소스셋에 포함됩니다."
echo "   2. IDE에서 'Reload Gradle Project' 실행하세요."
echo "   3. import com.oliveyoung.ivmlite.generated.jooq.Tables.RAW_DATA"
echo ""
echo "📌 jOOQ 사용 예시:"
echo "   dsl.selectFrom(RAW_DATA)"
echo "       .where(RAW_DATA.TENANT_ID.eq(\"tenant-1\"))"
echo "       .fetch()"
echo ""
echo "   잘못된 필드명을 쓰면 → 컴파일 에러! (타입 안전)"
