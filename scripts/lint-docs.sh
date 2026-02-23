#!/usr/bin/env bash
# 문서 lint: markdownlint + RULES.md 규칙 검사
# 사용: ./scripts/lint-docs.sh 또는 just lint-docs

set -e

cd "$(dirname "$0")/.."
ROOT="$PWD"

echo "📄 문서 Lint (docs/RULES.md 규칙 + markdownlint)"
echo ""

# 1. 금지 패턴: 마크다운 링크에서 ../rfc/ 또는 ./rfc/ 사용 (RULES.md 제외)
BROKEN_RFC=$(grep -rE --include="*.md" '\]\(\.\./rfc/|\]\(\./rfc/|\]\(\./docs/rfc/' docs/ 2>/dev/null | grep -v 'docs/RULES.md' || true)
if [ -n "$BROKEN_RFC" ]; then
  echo "❌ RULES 위반: rfc/ 링크 사용 (rfc_archive/ 사용)"
  echo "$BROKEN_RFC"
  exit 1
fi

# 3. markdownlint (npx 사용, .markdownlint-cli2.jsonc 설정)
if command -v npx &>/dev/null; then
  npx --yes markdownlint-cli2 2>/dev/null || {
    echo "⚠️  markdownlint-cli2 실행 실패."
    echo "   npm install -D markdownlint-cli2 또는 pnpm add -D markdownlint-cli2"
    exit 1
  }
  echo ""
  echo "✅ 문서 lint 통과"
else
  echo "⚠️  npx 없음. 금지 패턴 검사만 수행 (통과)"
  echo "   markdownlint: Node 설치 후 npx markdownlint-cli2"
fi
