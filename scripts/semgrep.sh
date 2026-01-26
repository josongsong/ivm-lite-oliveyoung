#!/bin/bash
# Semgrep 정적 분석 (보안/버그 패턴)
#
# 사전 요구: pip install semgrep 또는 brew install semgrep
# Usage: ./scripts/semgrep.sh [PATH]
#   PATH 생략 시 프로젝트 루트(.) 대상

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
TARGET="${1:-.}"

# pip install semgrep 시 executable 경로 (Gradle 등에서 PATH 미포함 대비)
for py in 3.12 3.11 3.10 3.9; do
  for base in "$HOME/Library/Python/$py" "$HOME/.local"; do
    [ -d "$base/bin" ] && PATH="$base/bin:$PATH"
  done
done
[ -d /opt/homebrew/bin ] && PATH="/opt/homebrew/bin:$PATH"
[ -d /usr/local/bin ] && PATH="/usr/local/bin:$PATH"

cd "$PROJECT_ROOT"

SEMGREP_CMD=""
if command -v semgrep &> /dev/null; then
    SEMGREP_CMD=semgrep
else
    echo "❌ semgrep not found. Install: pip install semgrep | pipx install semgrep | brew install semgrep"
    exit 1
fi

echo "🔍 ivm-lite Semgrep scan"
echo "   Config: p/default, p/kotlin, p/security-audit + config/semgrep/semgrep.yml"
echo "   Target: $TARGET"
echo ""

$SEMGREP_CMD scan \
    --config p/default \
    --config p/kotlin \
    --config p/security-audit \
    --config config/semgrep/semgrep.yml \
    "$TARGET"
