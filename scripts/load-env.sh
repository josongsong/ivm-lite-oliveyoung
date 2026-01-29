#!/bin/bash
# SOTA급 환경 변수 로더
# .env 파일을 안전하게 로드하고 검증합니다

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$PROJECT_ROOT/.env"

# 색상 출력
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

log_success() {
    echo -e "${GREEN}✓${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}⚠${NC} $1"
}

log_error() {
    echo -e "${RED}✗${NC} $1" >&2
}

# .env 파일 존재 확인
if [[ ! -f "$ENV_FILE" ]]; then
    log_warn ".env 파일이 없습니다. 생성합니다..."
    cat > "$ENV_FILE" << 'EOF'
# ============================================
# IVM Lite Remote-only 환경 변수
# ============================================
# 이 파일은 .gitignore에 포함되어 Git에 커밋되지 않습니다.
# 프로덕션 환경에서는 환경 변수나 IAM 역할/SSO를 사용하세요.

# AWS (필요 시: 로컬에서 직접 AWS 호출할 때)
export AWS_ACCESS_KEY_ID=YOUR_AWS_ACCESS_KEY_ID
export AWS_SECRET_ACCESS_KEY=YOUR_AWS_SECRET_ACCESS_KEY
export AWS_REGION=ap-northeast-2

# DynamoDB (remote-only)
# endpoint override는 기본 비움 (=AWS 기본 엔드포인트)
export DYNAMODB_TABLE=YOUR_DYNAMODB_TABLE_NAME
# export DYNAMODB_ENDPOINT=  # (optional) override endpoint

# PostgreSQL (remote-only)
export DB_URL=jdbc:postgresql://YOUR_RDS_HOST:5432/YOUR_DB?sslmode=require
export DB_USER=YOUR_DB_USER
export DB_PASSWORD=YOUR_DB_PASSWORD

# Kafka 설정 (선택사항)
# export KAFKA_BOOTSTRAP_SERVERS=localhost:9094
EOF
    log_success ".env 파일 생성 완료"
fi

# .env 파일 로드
log_info ".env 파일 로드 중: $ENV_FILE"
set -a  # 자동 export
source "$ENV_FILE"
set +a

# 필수 환경 변수 검증
validate_env() {
    local missing_vars=()
    
    if [[ -z "${AWS_ACCESS_KEY_ID:-}" ]]; then
        missing_vars+=("AWS_ACCESS_KEY_ID")
    fi
    
    if [[ -z "${AWS_SECRET_ACCESS_KEY:-}" ]]; then
        missing_vars+=("AWS_SECRET_ACCESS_KEY")
    fi
    
    if [[ -z "${AWS_REGION:-}" ]]; then
        missing_vars+=("AWS_REGION")
    fi

    if [[ -z "${DYNAMODB_TABLE:-}" ]]; then
        missing_vars+=("DYNAMODB_TABLE")
    fi

    if [[ -z "${DB_URL:-}" ]]; then
        missing_vars+=("DB_URL")
    fi
    if [[ -z "${DB_USER:-}" ]]; then
        missing_vars+=("DB_USER")
    fi
    if [[ -z "${DB_PASSWORD:-}" ]]; then
        missing_vars+=("DB_PASSWORD")
    fi
    
    if [[ ${#missing_vars[@]} -gt 0 ]]; then
        log_error "필수 환경 변수가 설정되지 않았습니다:"
        for var in "${missing_vars[@]}"; do
            echo "  - $var"
        done
        return 1
    fi
    
    # AWS Access Key 형식 검증 (AKIA로 시작)
    if [[ ! "${AWS_ACCESS_KEY_ID}" =~ ^AKIA[0-9A-Z]{16}$ ]]; then
        log_warn "AWS_ACCESS_KEY_ID 형식이 올바르지 않을 수 있습니다 (AKIA로 시작해야 함)"
    fi
    
    # Secret Key 길이 검증 (최소 40자)
    if [[ ${#AWS_SECRET_ACCESS_KEY} -lt 40 ]]; then
        log_warn "AWS_SECRET_ACCESS_KEY 길이가 짧습니다 (보안 위험 가능)"
    fi
    
    log_success "환경 변수 검증 완료"
    return 0
}

# 환경 변수 검증
if ! validate_env; then
    log_error "환경 변수 검증 실패"
    exit 1
fi

# 민감한 정보 마스킹하여 출력
mask_secret() {
    local secret="$1"
    if [[ ${#secret} -gt 8 ]]; then
        echo "${secret:0:4}****${secret: -4}"
    else
        echo "****"
    fi
}

log_info "환경 변수 설정 확인:"
echo "  AWS_ACCESS_KEY_ID: ${AWS_ACCESS_KEY_ID:0:8}****"
echo "  AWS_SECRET_ACCESS_KEY: $(mask_secret "$AWS_SECRET_ACCESS_KEY")"
echo "  AWS_REGION: ${AWS_REGION:-not set}"
echo "  DYNAMODB_ENDPOINT: ${DYNAMODB_ENDPOINT:-not set (AWS default)}"
echo "  DYNAMODB_TABLE: ${DYNAMODB_TABLE:-not set}"
echo "  DB_URL: ${DB_URL:-not set}"
echo "  DB_USER: ${DB_USER:-not set}"
echo "  DB_PASSWORD: $(mask_secret "${DB_PASSWORD:-}")"

log_success "환경 변수 로드 완료"
