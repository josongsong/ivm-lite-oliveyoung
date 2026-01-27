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
# IVM Lite AWS DynamoDB 자격 증명
# ============================================
# 이 파일은 .gitignore에 포함되어 Git에 커밋되지 않습니다.
# 프로덕션 환경에서는 환경 변수나 IAM 역할을 사용하세요.

# AWS 자격 증명 (필수)
export AWS_ACCESS_KEY_ID=YOUR_AWS_ACCESS_KEY_ID
export AWS_SECRET_ACCESS_KEY=YOUR_AWS_SECRET_ACCESS_KEY
export AWS_REGION=ap-northeast-2

# DynamoDB 설정 (선택사항)
# export DYNAMODB_ENDPOINT=http://localhost:8000  # 로컬 DynamoDB 사용 시
# export DYNAMODB_TABLE=ivm-lite-schema-registry-local

# 데이터베이스 설정 (선택사항)
# export DB_URL=jdbc:postgresql://localhost:5432/ivmlite
# export DB_USER=ivm
# export DB_PASSWORD=ivm_local_dev

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
echo "  DYNAMODB_ENDPOINT: ${DYNAMODB_ENDPOINT:-not set (AWS 사용)}"
echo "  DYNAMODB_TABLE: ${DYNAMODB_TABLE:-not set (기본값 사용)}"

log_success "환경 변수 로드 완료"
