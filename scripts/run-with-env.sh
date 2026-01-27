#!/bin/bash
# SOTA급: 환경 변수 자동 로드 후 애플리케이션 실행

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# 환경 변수 로드
source "$SCRIPT_DIR/load-env.sh"

# 애플리케이션 실행
cd "$PROJECT_ROOT"
exec "$@"
