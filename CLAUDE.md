# CLAUDE.md - AI Assistant Instructions

이 파일은 AI 어시스턴트(Claude, GPT 등)가 이 프로젝트를 이해하고 도움을 줄 때 참고하는 지침서입니다.

---

## 프로젝트 정보

| 항목 | 값 |
|------|-----|
| 프로젝트명 | IVM-Lite (Incremental View Maintenance) |
| 언어 | Kotlin 1.9, TypeScript 5.7 |
| 프레임워크 | Ktor (Backend), React 19 (Frontend) |
| 빌드 | Gradle 8.5, Vite 7 |
| 아키텍처 | Hexagonal + Domain-Sliced |

---

## 필수 명령어

### Backend (Kotlin)

| 목적 | 명령어 |
|------|--------|
| Admin 실행 | ./gradlew fastAdmin |
| Runtime 실행 | ./gradlew run |
| 빠른 빌드 | ./gradlew fastBuild |
| 단위 테스트 | ./gradlew unitTest |
| 통합 테스트 | ./gradlew integrationTest |
| 패키지 테스트 | ./gradlew testPackage -Dpkg=slices |
| 전체 검사 | ./gradlew checkAll |
| 린트 | ./gradlew lint |
| 클린 | ./gradlew clean |

### Frontend (React)

| 목적 | 명령어 |
|------|--------|
| 개발 서버 | cd admin-ui && npm run dev |
| 빌드 | cd admin-ui && npm run build |
| 린트 | cd admin-ui && npm run lint |
| 타입체크 | cd admin-ui && npm run typecheck |

---

## 디렉토리 가이드

```
/                           # 프로젝트 루트
├── src/main/kotlin/        # Kotlin 소스
│   └── com/oliveyoung/ivmlite/
│       ├── apps/           # 애플리케이션 레이어
│       │   ├── admin/      # Admin API (:8081)
│       │   ├── runtimeapi/ # Runtime API (:8080)
│       │   └── opscli/     # CLI 도구
│       ├── pkg/            # 도메인 패키지
│       │   ├── contracts/  # 계약 관리
│       │   ├── rawdata/    # 원본 데이터
│       │   ├── slices/     # 슬라이싱
│       │   ├── views/      # 뷰 조합
│       │   ├── sinks/      # 외부 전송
│       │   └── orchestration/ # Outbox 워커
│       └── shared/         # 공통 유틸
│
├── src/main/resources/
│   ├── contracts/v1/       # YAML 계약 정의
│   ├── db/migration/       # Flyway 마이그레이션
│   └── application.yaml    # 앱 설정
│
├── admin-ui/               # React Admin UI
│   └── src/
│       ├── app/            # 앱 설정
│       ├── features/       # 기능별 모듈
│       ├── shared/         # 공통 컴포넌트
│       └── widgets/        # 레이아웃
│
├── docs/
│   ├── rfc/                # RFC 문서
│   └── adr/                # ADR 문서
│
└── build.gradle.kts        # Gradle 빌드 설정
```

---

## 핵심 아키텍처 개념

### 1. Contract is Law
- 모든 스키마/규칙은 src/main/resources/contracts/v1/*.yaml에 정의
- YAML이 SSOT (Single Source of Truth)
- 종류: ENTITY_SCHEMA, RULESET, VIEW_DEFINITION, SINKRULE

### 2. 데이터 흐름
```
RawData → [RuleSet] → Slices → [ViewDef] → Views → [SinkRule] → Sink
```

### 3. Hexagonal Architecture
```
[Adapter] → [Port] → [Application] → [Domain]
```
- adapters/: 외부 시스템 연동
- ports/: 인터페이스 정의
- application/: 비즈니스 로직
- domain/: 도메인 모델

---

## 빌드 최적화

이 프로젝트는 SOTA급 빌드 최적화가 적용되어 있습니다:

- Configuration Cache: 설정 단계 캐싱
- Build Cache: 태스크 결과 캐싱
- Parallel Build: 멀티코어 활용
- Incremental Compilation: 증분 컴파일
- G1 GC: 대용량 힙 최적화

첫 빌드 후 증분 빌드는 ~3초 내로 완료됩니다.

---

## 테스트 전략

| 태그 | 설명 | Docker |
|------|------|--------|
| 기본 | 단위 테스트 | 불필요 |
| IntegrationTag | 통합 테스트 | 필요 |

```bash
# 단위 테스트만 (빠름)
./gradlew unitTest

# 통합 테스트 (Docker 필요)
./gradlew integrationTest

# 특정 패키지
./gradlew testPackage -Dpkg=slices
```

---

## 코딩 컨벤션

### Kotlin
- 4 spaces 들여쓰기
- camelCase for functions/variables
- PascalCase for classes
- UPPER_SNAKE_CASE for constants
- Detekt 린터 사용

### TypeScript (Frontend)
- 2 spaces 들여쓰기
- ESLint + Prettier
- FSD (Feature-Sliced Design) 구조

---

## 환경변수 설정 (필수!)

**.env 파일에 DB/AWS 접속 정보가 있습니다. jOOQ 코드 생성, 테스트 실행 전 반드시 로드하세요!**

```bash
# .env 로드 후 Gradle 실행
source .env && ./gradlew jooqCodegen
source .env && ./gradlew test
source .env && ./gradlew run
```

.env 파일 주요 변수:
| 변수 | 용도 |
|------|------|
| DB_URL | PostgreSQL JDBC URL |
| DB_USER | DB 사용자 |
| DB_PASSWORD | DB 비밀번호 |
| AWS_ACCESS_KEY_ID | DynamoDB 접근 |
| AWS_SECRET_ACCESS_KEY | DynamoDB 접근 |
| DYNAMODB_TABLE | DynamoDB 테이블명 |

---

## 주의사항

1. **환경변수**: .env 파일에서 로드 필수 (`source .env`)
2. DB 마이그레이션: ./gradlew flywayMigrate (DB 연결 필요)
3. jOOQ 코드 생성: ./gradlew jooqCodegen (DB 연결 필요, `.env` 로드 필수)
4. Admin UI 빌드: admin-ui/npm run build → src/main/resources/static/admin/에 출력

---

## 유용한 링크

- Admin UI: http://localhost:3000 (개발) / http://localhost:8081/admin (프로덕션)
- Runtime API: http://localhost:8080
- 테스트 리포트: build/reports/tests/test/index.html
- Detekt 리포트: build/reports/detekt/detekt.html

---

## AI 어시스턴트 팁

1. 빌드 실패 시: ./gradlew clean fastBuild로 캐시 정리
2. 테스트 실패 시: 에러 메시지와 함께 테스트 파일 확인 요청
3. 새 기능 추가 시: 관련 RFC 문서 (docs/rfc/) 먼저 확인
4. 계약 수정 시: contracts/v1/ 디렉토리의 YAML 파일 수정
5. 프론트엔드 작업 시: admin-ui/src/features/ 구조 따르기
