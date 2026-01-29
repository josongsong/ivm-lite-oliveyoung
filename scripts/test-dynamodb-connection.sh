#!/bin/bash
# SOTA급: AWS DynamoDB 연결 테스트
# 실제 AWS DynamoDB에 연결하여 테이블 목록을 조회합니다

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# 환경 변수 로드
source "$SCRIPT_DIR/load-env.sh"

# 색상 출력
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

log_success() {
    echo -e "${GREEN}✓${NC} $1"
}

log_error() {
    echo -e "${RED}✗${NC} $1" >&2
}

log_warn() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# AWS CLI 설치 확인
if ! command -v aws &> /dev/null; then
    log_error "AWS CLI가 설치되어 있지 않습니다."
    echo "설치 방법: https://aws.amazon.com/cli/"
    exit 1
fi

log_info "AWS DynamoDB 연결 테스트 시작..."
echo ""

# 1. 자격 증명 확인
log_info "1. AWS 자격 증명 확인..."
if [[ -z "${AWS_ACCESS_KEY_ID:-}" ]] || [[ -z "${AWS_SECRET_ACCESS_KEY:-}" ]]; then
    log_error "AWS 자격 증명이 설정되지 않았습니다."
    exit 1
fi
log_success "자격 증명 확인 완료"

# 2. Region 확인
REGION="${AWS_REGION:-ap-northeast-2}"
log_info "2. Region: $REGION"

# 3. DynamoDB 연결 테스트 (테이블 목록 조회)
log_info "3. DynamoDB 연결 테스트 (list-tables)..."
echo ""

if aws dynamodb list-tables \
    --region "$REGION" \
    --output table 2>&1; then
    echo ""
    log_success "DynamoDB 연결 성공!"
else
    EXIT_CODE=$?
    echo ""
    log_error "DynamoDB 연결 실패 (exit code: $EXIT_CODE)"
    echo ""
    log_info "문제 해결:"
    echo "  1. AWS 자격 증명 확인: echo \$AWS_ACCESS_KEY_ID"
    echo "  2. Region 확인: echo \$AWS_REGION"
    echo "  3. 네트워크 연결 확인"
    echo "  4. IAM 권한 확인 (dynamodb:ListTables 필요)"
    exit $EXIT_CODE
fi

# 4. 특정 테이블 존재 확인
TABLE_NAME="${DYNAMODB_TABLE:-}"
if [[ -z "$TABLE_NAME" ]]; then
    log_error "DYNAMODB_TABLE이 설정되지 않았습니다 (remote-only)."
    exit 1
fi
log_info "4. 테이블 존재 확인: $TABLE_NAME"
echo ""

if aws dynamodb describe-table \
    --table-name "$TABLE_NAME" \
    --region "$REGION" \
    --output table 2>&1; then
    echo ""
    log_success "테이블 '$TABLE_NAME' 존재 확인 완료"
else
    EXIT_CODE=$?
    echo ""
    log_warn "테이블 '$TABLE_NAME'을 찾을 수 없습니다 (exit code: $EXIT_CODE)"
    echo ""
    log_info "테이블 생성은 IaC/운영 절차로 진행하세요 (remote-only)."
    log_info "또는 다른 테이블 이름을 사용하려면:"
    echo "  export DYNAMODB_TABLE=your-table-name"
fi

echo ""
log_success "AWS DynamoDB 연결 테스트 완료!"
