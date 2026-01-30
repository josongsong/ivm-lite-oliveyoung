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

> 💡 **Tip**: `just` 명령어 러너를 사용하면 더 간편합니다! (`brew install just` 또는 `cargo install just`)
> 
> ```bash
> just admin-dev      # Admin Backend 개발 모드
> just admin-ui-dev   # Admin Frontend 개발 모드
> just dev            # 전체 개발 환경 실행 가이드
> just --list         # 사용 가능한 모든 명령어 보기
> ```

### Backend (Kotlin)

| 목적 | 명령어 (Gradle) | 명령어 (Just) |
|------|----------------|--------------|
| Admin 실행 | ./gradlew fastAdmin | `just admin-fast` |
| Admin 개발 모드 | ./gradlew runAdminDev | `just admin` |
| Admin Hot Reload | ./gradlew --no-configuration-cache --continuous runAdminDev | `just admin-dev` |
| Runtime 실행 | ./gradlew run | `just runtime` |
| Runtime 개발 모드 | ./gradlew runApiDev | `just runtime-dev` |
| 빠른 빌드 | ./gradlew fastBuild | `just build` |
| 단위 테스트 | ./gradlew unitTest | `just test` |
| 통합 테스트 | ./gradlew integrationTest | `just test-integration` |
| 패키지 테스트 | ./gradlew testPackage -Dpkg=slices | `just test-pkg slices` |
| 전체 검사 | ./gradlew checkAll | `just check` |
| 린트 | ./gradlew lint | `just lint` |
| 클린 | ./gradlew clean | `just clean` |

### Frontend (React)

| 목적 | 명령어 (npm) | 명령어 (Just) |
|------|-------------|--------------|
| 개발 서버 (Hot Reload) | cd admin-ui && npm run dev | `just admin-ui-dev` |
| 빌드 | cd admin-ui && npm run build | `just build-ui` |
| 린트 | cd admin-ui && npm run lint | `just lint-ui` |
| 타입체크 | cd admin-ui && npm run typecheck | `just typecheck-ui` |

**접속 주소**:
- 개발 서버: http://localhost:3000 (Vite HMR 자동 지원)
- 프로덕션: http://localhost:8081/admin

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

## 개발 모드 (Hot Reload)

### Admin 앱 개발 모드 (Backend)
```bash
# Just 사용 (권장)
just admin-dev

# 또는 Gradle 직접 사용
./gradlew --no-configuration-cache --continuous runAdminDev
```

**주의사항**:
- Configuration Cache와 `--continuous` 모드 호환성 문제로 `--no-configuration-cache` 옵션 권장
- 포트 충돌 시: `just kill-ports` 또는 `lsof -ti:8081 | xargs kill -9`
- `DEV_MODE=true` 환경변수 자동 설정 (에러 상세 출력)

### Admin UI 개발 모드 (Frontend)
```bash
# Just 사용 (권장)
just admin-ui-dev

# 또는 npm 직접 사용
cd admin-ui && npm run dev
```

**접속 주소**:
- 개발 서버: http://localhost:3000 (Vite HMR 자동 지원)
- 프로덕션 빌드: http://localhost:8081/admin (Backend에 빌드된 정적 파일 서빙)

### 전체 개발 환경 실행
```bash
# Just 사용 (권장)
just dev  # 실행 가이드 표시

# 터미널 1: Backend (Hot Reload)
just admin-dev

# 터미널 2: Frontend (Hot Reload)
just admin-ui-dev
```

---

## AI 어시스턴트 팁

1. 빌드 실패 시: ./gradlew clean fastBuild로 캐시 정리
2. 테스트 실패 시: 에러 메시지와 함께 테스트 파일 확인 요청
3. 새 기능 추가 시: 관련 RFC 문서 (docs/rfc/) 먼저 확인
4. 계약 수정 시: contracts/v1/ 디렉토리의 YAML 파일 수정
5. 프론트엔드 작업 시: admin-ui/src/features/ 구조 따르기
6. 개발 중 Hot Reload: `--no-configuration-cache --continuous` 옵션 사용

---

## 코딩 컨벤션

### Kotlin 에러 처리

**⚠️ 중요: try-catch 대신 Arrow의 Result 타입 사용**

이 프로젝트는 Arrow 라이브러리를 사용하여 함수형 에러 처리를 합니다. `try-catch` 블록 대신 Arrow의 `Either` 타입과 `either` 빌더를 사용하세요.

**❌ 잘못된 예시 (try-catch 사용):**
```kotlin
fun getData(): Result<Data> {
    return try {
        val data = fetchData()
        Result.Ok(data)
    } catch (e: Exception) {
        Result.Err(DomainError.StorageError(e.message))
    }
}
```

**✅ 올바른 예시 (Arrow Either 사용):**
```kotlin
import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either

fun getData(): Either<DomainError, Data> = either {
    val data = catch({ e: Exception ->
        raise(DomainError.StorageError("Failed to fetch data: ${e.message}"))
    }) {
        fetchData()
    }
    data
}
```

**Arrow Either 사용 패턴:**

1. **함수 반환 타입**: `Either<DomainError, T>` 사용
2. **에러 처리**: `either { }` 빌더 내에서 `catch { }` 사용
3. **에러 발생**: `raise(DomainError.xxx)` 사용
4. **중첩 호출**: `.bind()` 사용하여 Either 언래핑

**예시:**
```kotlin
fun getEnvironment(env: String): Either<DomainError, EnvironmentData> = either {
    val databases = getDatabaseInfo().bind()  // Either 언래핑
    val config = getEnvironmentConfig().bind()
    
    EnvironmentData(
        environment = env,
        databases = databases,
        config = config
    )
}.catch { e: Exception ->
    DomainError.StorageError("Failed to get environment: ${e.message}")
}

private fun getDatabaseInfo(): Either<DomainError, List<DatabaseInfo>> = either {
    catch({ e: Exception ->
        raise(DomainError.StorageError("Failed to get database info: ${e.message}"))
    }) {
        // 데이터베이스 정보 조회 로직
        listOf(...)
    }
}
```

**참고:**
- Arrow 라이브러리: `io.arrow-kt:arrow-core:1.2.1`
- 문서: https://arrow-kt.io/docs/core/either/