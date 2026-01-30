# IVM-Lite Justfile
# 간편한 개발 명령어 모음
# 사용법: just <recipe-name>

# 기본 레시피 (도움말)
default:
    @just --list

# ============================================
# 개발 모드 (Hot Reload)
# ============================================

# Admin Backend 개발 모드 (Hot Reload)
admin-dev:
    @echo "🚀 Starting Admin Backend in dev mode (Hot Reload)..."
    ./gradlew --no-configuration-cache --continuous runAdminDev

# Admin Frontend 개발 모드 (Hot Reload)
admin-ui-dev:
    @echo "🚀 Starting Admin UI in dev mode (Hot Reload)..."
    cd admin-ui && pnpm run dev

# 전체 개발 환경 실행 (두 터미널 필요)
dev:
    @echo "🚀 Starting full development environment..."
    @echo ""
    @echo "터미널 1에서 실행:"
    @echo "  just admin-dev      # Kotlin Backend (Hot Reload)"
    @echo ""
    @echo "터미널 2에서 실행:"
    @echo "  just admin-ui-dev   # React Frontend (HMR)"
    @echo ""
    @echo "접속 주소:"
    @echo "  - Admin UI: http://localhost:3000 (Vite HMR)"
    @echo "  - Backend API: http://localhost:8081/api"
    @echo ""
    @echo "📌 분리 개발/배포 구조:"
    @echo "  - Frontend와 Backend 완전 독립"
    @echo "  - /api 요청은 Vite proxy → localhost:8081로 전달"

# ============================================
# 일반 실행
# ============================================

# Admin Backend 실행
admin:
    @echo "🚀 Starting Admin Backend..."
    ./gradlew runAdminDev

# Admin Backend 실행 (별칭)
runAdmin:
    @echo "🚀 Starting Admin Backend..."
    ./gradlew runAdminDev

# Admin Backend 빠른 실행
admin-fast:
    @echo "🚀 Starting Admin Backend (fast)..."
    ./gradlew fastAdmin

# Runtime API 실행
runtime:
    @echo "🚀 Starting Runtime API..."
    ./gradlew run

# Runtime API 개발 모드
runtime-dev:
    @echo "🚀 Starting Runtime API in dev mode..."
    ./gradlew --no-configuration-cache --continuous runApiDev

# ============================================
# 빌드
# ============================================

# 빠른 빌드 (테스트 스킵)
build:
    @echo "🔨 Building (tests skipped)..."
    ./gradlew fastBuild

# 전체 빌드
build-all:
    @echo "🔨 Building all..."
    ./gradlew build

# Frontend 빌드
build-ui:
    @echo "🔨 Building Admin UI..."
    cd admin-ui && pnpm run build

# 클린 빌드
clean-build:
    @echo "🧹 Cleaning and building..."
    ./gradlew clean build --no-build-cache

# ============================================
# 테스트
# ============================================

# 단위 테스트
test:
    @echo "🧪 Running unit tests..."
    ./gradlew unitTest

# 통합 테스트
test-integration:
    @echo "🧪 Running integration tests..."
    ./gradlew integrationTest

# 전체 테스트
test-all:
    @echo "🧪 Running all tests..."
    ./gradlew test

# 특정 패키지 테스트
test-pkg PKG:
    @echo "🧪 Running tests for package: {{PKG}}..."
    ./gradlew testPackage -Dpkg={{PKG}}

# ============================================
# 검사 & 린트
# ============================================

# 전체 검사 (테스트 + 린트)
check:
    @echo "🔍 Running all checks..."
    ./gradlew checkAll

# Kotlin 린트
lint:
    @echo "🔍 Running Kotlin lint..."
    ./gradlew lint

# Frontend 린트
lint-ui:
    @echo "🔍 Running Frontend lint..."
    cd admin-ui && pnpm run lint

# Frontend 타입 체크
typecheck-ui:
    @echo "🔍 Running Frontend typecheck..."
    cd admin-ui && pnpm run typecheck

# ============================================
# 유틸리티
# ============================================

# 포트 확인
ports:
    @echo "🔍 Checking ports..."
    @echo "Port 8081 (Admin):"
    @lsof -ti:8081 || echo "  ✅ Available"
    @echo "Port 8080 (Runtime):"
    @lsof -ti:8080 || echo "  ✅ Available"
    @echo "Port 3000 (Frontend):"
    @lsof -ti:3000 || echo "  ✅ Available"

# 포트 종료
kill-ports:
    @echo "🛑 Killing processes on ports..."
    @lsof -ti:8081 | xargs kill -9 2>/dev/null || echo "Port 8081: No process found"
    @lsof -ti:8080 | xargs kill -9 2>/dev/null || echo "Port 8080: No process found"
    @lsof -ti:3000 | xargs kill -9 2>/dev/null || echo "Port 3000: No process found"
    @echo "✅ Done"

# 클린
clean:
    @echo "🧹 Cleaning..."
    ./gradlew clean

# ============================================
# DB 관련
# ============================================

# jOOQ 코드 생성 (DB 연결 필요)
jooq:
    @echo "📦 Generating jOOQ code..."
    @echo "⚠️  Make sure .env is loaded: source .env"
    ./gradlew jooqCodegen

# Flyway 마이그레이션
migrate:
    @echo "📦 Running Flyway migrations..."
    @echo "⚠️  Make sure .env is loaded: source .env"
    ./gradlew flywayMigrate

# jOOQ 재생성 (마이그레이션 후)
regenerate-jooq:
    @echo "📦 Regenerating jOOQ after migration..."
    @echo "⚠️  Make sure .env is loaded: source .env"
    ./gradlew regenerateJooq
