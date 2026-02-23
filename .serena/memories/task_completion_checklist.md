# IVM-Lite 작업 완료 시 체크리스트

## 코드 수정 후
1. `./gradlew unitTest` 또는 `just test` - 단위 테스트
2. 수정한 패키지 테스트: `./gradlew testPackage -Dpkg=패키지명`

## PR 전 필수
1. `./gradlew checkAll` 또는 `just check` - 전체 검사
2. `cd admin-ui && pnpm run lint && pnpm run typecheck` - Frontend 검사

## 참고
- Configuration Cache + `--continuous` 호환 문제 → `--no-configuration-cache` 사용
- jOOQ/테스트/마이그레이션 시 `source .env` 필수
- 계약 수정 시 `src/main/resources/contracts/v1/` YAML 수정
