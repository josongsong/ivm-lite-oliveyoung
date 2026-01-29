#!/bin/bash
# Outbox 확인 스크립트 (환경 변수 자동 로드)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# 환경 변수 로드
if [ -f "$PROJECT_ROOT/scripts/load-env.sh" ]; then
    source "$PROJECT_ROOT/scripts/load-env.sh"
fi

# JDBC URL을 PostgreSQL URL로 변환
if [ -n "${DB_URL:-}" ] && [[ "$DB_URL" == jdbc:* ]]; then
    # jdbc:postgresql://host:port/db -> postgresql://user:pass@host:port/db
    DB_URL_CONVERTED=$(echo "$DB_URL" | sed 's/jdbc:postgresql:\/\//postgresql:\/\/'"${DB_USER:-postgres}"':'"${DB_PASSWORD:-postgres}"'@/')
    export DB_URL="$DB_URL_CONVERTED"
fi

# Python 스크립트 실행
python3 "$PROJECT_ROOT/check-outbox.py"
