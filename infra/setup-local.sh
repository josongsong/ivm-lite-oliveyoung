#!/bin/bash
# ivm-lite 로컬 인프라 전체 설정
# Usage: ./setup-local.sh

set -e

echo "ERROR: Remote-only 정책으로 로컬 DynamoDB/PostgreSQL 기반 setup-local은 더 이상 지원하지 않습니다." >&2
echo "필요한 원격 리소스(DB/DynamoDB)는 환경 변수(DB_URL/DB_USER/DB_PASSWORD/DYNAMODB_TABLE 등)로 주입하세요." >&2
exit 1
