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
    @echo "한 번에 실행:"
    @echo "  just admin-dev-all # 포트 정리 후 Backend + Frontend 동시 실행"
    @echo ""
    @echo "접속 주소:"
    @echo "  - Admin UI: http://localhost:3000 (Vite HMR)"
    @echo "  - Backend API: http://localhost:8081/api"
    @echo ""
    @echo "📌 분리 개발/배포 구조:"
    @echo "  - Frontend와 Backend 완전 독립"
    @echo "  - /api 요청은 Vite proxy → localhost:8081로 전달"

# Admin Backend + Frontend 동시 실행 (기존 포트 정리 후)
admin-dev-all:
    #!/usr/bin/env bash
    set -e
    echo "🛑 기존 포트(8081, 3000, 8080) 프로세스 종료..."
    just kill-ports
    echo ""
    echo "🚀 Starting Admin Backend (port 8081)..."
    ./gradlew --no-configuration-cache --continuous runAdminDev &
    BACKEND_PID=$!
    trap "kill $BACKEND_PID 2>/dev/null || true; exit" INT TERM
    sleep 2
    echo "🚀 Starting Admin UI (port 3000)..."
    cd admin-ui && pnpm run dev
    kill $BACKEND_PID 2>/dev/null || true

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

# Product E2E (product-schema-dx-proposal Phase 1.5)
product-e2e sample=".tmp/product/UA11279226.json":
    @echo "🛒 Running Product E2E..."
    ./gradlew productE2E -Dsample={{sample}}

# DX 도구 (product-schema-dx-proposal RFC 2.2, 5.1)
extract-paths sample=".tmp/product/UA11279226.json" output=".tmp/paths.yaml":
    @echo "📂 Extracting PathExpr from JSON..."
    ./gradlew extractJsonPaths -Dsample={{sample}} -Doutput={{output}}

paths-to-impact paths=".tmp/paths.yaml" output=".tmp/impact-map-draft.yaml":
    @echo "🗺️ Generating impactMap draft..."
    ./gradlew pathsToImpactMap -Dpaths={{paths}} -Doutput={{output}}

validate-raw sample=".tmp/product/UA11279226.json":
    @echo "✅ Validating RawData..."
    ./gradlew validateRawData -Dsample={{sample}}

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

# 문서 린트 (docs/RULES.md + markdownlint)
lint-docs:
    @echo "📄 Running documentation lint..."
    ./gradlew lintDocs

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

# ============================================
# Sink Plugin & LocalStack (RFC-017)
# ============================================

# Lambda 패키징 (S3 Sink)
package-lambda plugin:
    @echo "📦 Packaging Lambda: {{plugin}}..."
    ./gradlew :plugins:sink-{{plugin}}:shadowJar
    @echo "✅ Lambda JAR created: plugins/sink-{{plugin}}/build/libs/{{plugin}}-sink-lambda.jar"

# LocalStack 시작
local-infra-up:
    @echo "🚀 Starting LocalStack..."
    docker-compose -f infra/docker-compose.localstack.yml up -d
    @echo "✅ LocalStack running on http://localhost:4566"
    @echo "⏳ Waiting for LocalStack to be ready..."
    @sleep 5

# LocalStack 종료
local-infra-down:
    @echo "🛑 Stopping LocalStack..."
    docker-compose -f infra/docker-compose.localstack.yml down
    @echo "✅ LocalStack stopped"

# Terraform 적용 (로컬)
local-deploy:
    @echo "🏗️ Applying Terraform (LocalStack)..."
    cd infra/terraform/local && terraform init && terraform apply -auto-approve
    @echo "✅ Infrastructure deployed"

# 로컬 환경 전체 셋업
local-setup:
    @echo "🚀 Setting up local Sink environment..."
    @just local-infra-up
    @just package-lambda s3
    @just local-deploy
    @echo ""
    @echo "✅ Local environment ready!"
    @echo ""
    @echo "📋 Resources created:"
    @echo "  - SQS Queue: local-s3-sink-queue"
    @echo "  - S3 Bucket: local-ivm-lite-sink-data"
    @echo "  - Lambda: local-s3-sink"
    @echo ""
    @echo "🔍 Check status:"
    @echo "  just local-status"

# 로컬 환경 정리
local-cleanup:
    @echo "🧹 Cleaning up local environment..."
    cd infra/terraform/local && terraform destroy -auto-approve || true
    @just local-infra-down
    @echo "✅ Cleanup complete"

# 로컬 환경 상태 확인
local-status:
    @echo "📊 LocalStack Status:"
    @echo ""
    @docker ps | grep localstack || echo "❌ LocalStack not running"
    @echo ""
    @echo "🔍 AWS Resources (via LocalStack):"
    @echo ""
    @echo "SQS Queues:"
    @aws --endpoint-url=http://localhost:4566 sqs list-queues || echo "  ⚠️  No queues found"
    @echo ""
    @echo "S3 Buckets:"
    @aws --endpoint-url=http://localhost:4566 s3 ls || echo "  ⚠️  No buckets found"
    @echo ""
    @echo "Lambda Functions:"
    @aws --endpoint-url=http://localhost:4566 lambda list-functions --query 'Functions[].FunctionName' || echo "  ⚠️  No functions found"

# S3 확인 (로컬)
local-s3-ls:
    @echo "📦 S3 Bucket Contents (local-ivm-lite-sink-data):"
    @aws --endpoint-url=http://localhost:4566 s3 ls s3://local-ivm-lite-sink-data --recursive || echo "  Empty or not found"

# SQS 메시지 전송 테스트
local-test-sqs:
    @echo "📨 Sending test message to SQS..."
    @aws --endpoint-url=http://localhost:4566 sqs send-message \
        --queue-url http://localhost:4566/000000000000/local-s3-sink-queue \
        --message-body '{"envelopeVersion":1,"target":"s3-sink","producedAtEpochMs":1707728000000,"payloadVersion":1,"entityType":"product","sliceType":"core","viewName":"view-product-core","viewData":{"id":"P001","name":"Test Product"},"metadata":{}}'
    @echo "✅ Message sent"


# ========================================
# Preview Environment (Real AWS)
# ========================================

# Preview 환경 프로비저닝
preview-setup:
    @echo "🚀 Setting up Preview environment..."
    @cd infra/terraform/preview && terraform init && terraform apply

# Preview 환경 상태 확인
preview-status:
    @echo "📊 Preview environment status:"
    @cd infra/terraform/preview && terraform output

# Preview SQS 테스트 메시지 발송
preview-test-sqs:
    #!/usr/bin/env bash
    QUEUE_URL=$(cd infra/terraform/preview && terraform output -raw s3_sink_queue_url 2>/dev/null || echo "")
    if [ -z "$QUEUE_URL" ]; then
        echo "❌ Queue URL not found. Run 'just preview-setup' first."
        exit 1
    fi
    echo "📨 Sending test message to: $QUEUE_URL"
    aws sqs send-message \
        --queue-url "$QUEUE_URL" \
        --message-body '{"envelopeVersion":1,"target":"s3","producedAtEpochMs":1707728000000,"payloadVersion":1,"entityType":"product","sliceType":"CORE","viewName":"PRODUCT_DETAIL","viewData":{"id":"P001","name":"Test Product"},"metadata":{}}'
    echo "✅ Message sent"

# Preview Lambda 로그 확인
preview-logs:
    @aws logs tail /aws/lambda/preview-s3-sink --follow

# Preview S3 파일 목록
preview-s3-ls:
    #!/usr/bin/env bash
    BUCKET=$(cd infra/terraform/preview && terraform output -raw s3_bucket_name 2>/dev/null || echo "")
    if [ -z "$BUCKET" ]; then
        echo "❌ Bucket not found. Run 'just preview-setup' first."
        exit 1
    fi
    echo "📦 S3 files in bucket: $BUCKET"
    aws s3 ls s3://$BUCKET/views/ --recursive

# Preview Lambda 업데이트 (JAR 재배포)
preview-update-lambda:
    @echo "🔄 Building Lambda JAR..."
    @./gradlew :plugins:sink-s3:shadowJar
    @echo "📤 Updating Lambda function..."
    @aws lambda update-function-code \
        --function-name preview-s3-sink \
        --zip-file fileb://plugins/sink-s3/build/libs/s3-sink-lambda.jar
    @echo "✅ Lambda updated"

# Preview 환경 삭제
preview-cleanup:
    @echo "🗑️  Destroying Preview environment..."
    @cd infra/terraform/preview && terraform destroy

# Lambda 배포 (shadowJar 빌드)
lambda-build:
    @echo "🚀 Building Lambda JAR..."
    ./gradlew shadowJar
    @ls -lh build/libs/ivm-ingest-lambda-1.0.0.jar

# Lambda 배포 (AWS CLI)
lambda-deploy: lambda-build
    @echo "📦 Deploying to AWS Lambda..."
    aws lambda update-function-code \
      --function-name ivm-ingest-api \
      --zip-file fileb://build/libs/ivm-ingest-lambda-1.0.0.jar \
      --region ap-northeast-2

# Lambda 로그 확인
lambda-logs:
    @echo "📋 Tailing Lambda logs..."
    aws logs tail /aws/lambda/ivm-ingest-api --follow --region ap-northeast-2
