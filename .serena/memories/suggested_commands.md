# IVM-Lite 권장 개발 명령어

> `just` 사용 권장 (`brew install just`)

## 실행
| 명령어 | 설명 |
|--------|------|
| `just admin-dev` | Admin Backend Hot Reload (:8081) |
| `just admin-ui-dev` | Admin Frontend HMR (:3000) |
| `just admin-fast` | Admin 빠른 실행 |
| `just runtime` | Runtime API (:8080) |
| `just dev` | 전체 개발 환경 가이드 |

## 빌드
| 명령어 | 설명 |
|--------|------|
| `just build` / `./gradlew fastBuild` | 빠른 빌드 (테스트 스킵) |
| `just build-all` | 전체 빌드 |
| `just build-ui` | Frontend 빌드 |
| `just clean-build` | 클린 빌드 |

## 테스트
| 명령어 | 설명 |
|--------|------|
| `just test` / `./gradlew unitTest` | 단위 테스트 |
| `just test-integration` | 통합 테스트 (Docker 필요) |
| `just test-pkg slices` | 특정 패키지 테스트 |

## 검사 (PR 전)
| 명령어 | 설명 |
|--------|------|
| `just check` | 전체 검사 (테스트+린트) |
| `just lint` | Kotlin 린트 |
| `just lint-ui` | Frontend 린트 |
| `just typecheck-ui` | Frontend 타입체크 |

## DB (`.env` 로드 필수)
| 명령어 | 설명 |
|--------|------|
| `source .env && just jooq` | jOOQ 코드 생성 |
| `source .env && just migrate` | Flyway 마이그레이션 |

## 유틸
| 명령어 | 설명 |
|--------|------|
| `just kill-ports` | 8080/8081/3000 포트 프로세스 종료 |
| `just ports` | 포트 사용 여부 확인 |

## 환경변수 (.env)
- DB_URL, DB_USER, DB_PASSWORD (PostgreSQL)
- AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY (DynamoDB)
- DYNAMODB_TABLE
