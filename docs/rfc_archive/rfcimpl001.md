RFC-IMPL-001 — Build Bootstrap + Baseline Contracts + CI Gates

Status: Accepted
Created: 2026-01-25
Scope: “코딩을 시작할 수 있는 상태”를 만드는 최소 작업 (build/test/lint/arch gates + contract SSOT 위치 고정)
Depends on: RFC-V4-002, RFC-V4-003, RFC-V4-010
Audience: Platform / All Contributors
Non-Goals: 런타임 기능 구현, DynamoDB 어댑터 구현 (RFC-IMPL-007), RuleSet DSL 구현

0. Executive Summary

본 RFC는 ivm-lite 구현의 첫 단계로, **반드시 먼저 갖춰야 하는 개발/CI 기반**을 고정한다.

- Gradle Wrapper를 레포에 포함하여 “누구나 동일한 빌드/테스트”가 가능해야 함
- 계약(contracts) SSOT는 `src/main/resources/contracts/v1/`로 고정 (런타임 로드 가능)
- RFC-V4-010 가드레일은 **ArchUnit + Detekt**로 자동 강제

---

1. Decisions (SSOT)

1-1. Gradle Wrapper 필수
- 레포에 반드시 포함:
  - `./gradlew`, `./gradlew.bat`
  - `gradle/wrapper/gradle-wrapper.properties`
  - `gradle/wrapper/gradle-wrapper.jar`
- CI/로컬 모두 `./gradlew`만 사용 (로컬 gradle 설치 의존 금지)

1-2. Contracts SSOT 위치 (v1 Bootstrap)
- **v1 (Bootstrap/개발/테스트)**: 로컬 YAML 파일
  - `src/main/resources/contracts/v1/*.yaml`
  - `LocalYamlContractRegistryAdapter`로 로드
  - 개발/테스트 시 빠른 반복 지원 (IDE에서 바로 수정 가능)
- **v2 (운영)**: DynamoDB Schema Registry
  - `DynamoDBContractRegistryAdapter`로 전환 (RFC-IMPL-007)
  - 스키마 버전 관리, 배포 분리, 멀티 환경 지원
  - 운영 SSOT는 DynamoDB이며, 로컬 YAML은 "seed/bootstrap" 용도로만 사용
- **포트는 동일**:
  - `ContractRegistryPort` 인터페이스는 변경 없음 (Hexagonal Architecture)
  - 어댑터만 교체하면 v1 → v2 전환 완료

- **주의 (RFC-V4-010 레이아웃과의 충돌 방지)**:
  - `resources/contracts/*`는 **개발/테스트용 bootstrap 데이터**이다.
  - 운영 SSOT는 DynamoDB Registry이며, 여기서 조회한다.
  - 향후 "registry policy / authoring UI" 용도로 별도 서비스가 필요할 수 있다.

1-3. Architecture Gates
- RFC-V4-010 제약을 테스트로 강제:
  - ArchUnit: `ArchitectureConstraintsTest`
- Detekt: 기본 코드 품질 규칙 적용

---

2. Deliverables

2-1. Build/CI
- `./gradlew test`가 로컬/CI에서 동일하게 동작
- `./gradlew detekt`가 동작 (또는 `./gradlew check`에 포함)

2-2. Contracts
- 다음 파일이 존재:
  - `src/main/resources/contracts/v1/changeset.v1.yaml`
  - `src/main/resources/contracts/v1/join-spec.v1.yaml`
  - `src/main/resources/contracts/v1/inverted-index.v1.yaml`
  - `src/main/resources/contracts/v1/index-value-canonicalizer.v1.yaml`
- 런타임 어댑터의 기본 리소스 루트는 `/contracts/v1`

---

3. Acceptance Criteria (Must Pass)

3-1. Commands
- `./gradlew test` ✅
- `./gradlew test --tests ArchitectureConstraintsTest` ✅
- `./gradlew detekt` ✅
- `./gradlew checkAll` ✅ (tests + detekt)

3-2. Architecture constraints (RFC-V4-010)

**ArchUnit 테스트로 강제되는 규칙 (SSOT, 코드와 1:1 매핑)**:

다음 규칙은 `src/test/kotlin/com/oliveyoung/ivmlite/ArchitectureConstraintsTest.kt`에 정의되어 있으며,
모든 PR에서 `./gradlew test --tests ArchitectureConstraintsTest`로 검증된다.

**P0 규칙 (필수, 실패 시 PR 거부)**:

1. **도메인 간 직접 import 금지**
   - `package/<domainA>/**` → `package/<domainB>/domain/**` 직접 import 금지
   - 도메인 간 통신은 반드시 `ports/**` 경유만 허용
   - 검증: `noClasses().that().resideInAPackage("..package.$domainA..").should().dependOnClassesThat().resideInAPackage("..package.$domainB.domain..")`

2. **apps는 orchestration만 호출**
   - `apps/**` → `package/<domain>/domain/**` 직접 호출 금지
   - apps는 반드시 `package/orchestration/**`만 호출
   - 검증: `noClasses().that().resideInAPackage("..apps..").should().dependOnClassesThat().resideInAPackage("..package.*.domain..")`

3. **orchestration → orchestration 호출 금지 (깊이 제한)**
   - `package/orchestration/**` → `package/orchestration/application/**` 호출 금지
   - workflow가 다른 workflow를 호출하는 중첩 금지
   - 검증: `noClasses().that().resideInAPackage("..package.orchestration..").should().dependOnClassesThat().resideInAPackage("..package.orchestration.application..")`

4. **orchestration은 ports를 통해서만 도메인 호출**
   - `package/orchestration/**` → `package/<domain>/domain/**` 직접 호출 금지
   - orchestration은 반드시 `package/<domain>/ports/**`만 사용
   - 검증: `noClasses().that().resideInAPackage("..package.orchestration..").should().dependOnClassesThat().resideInAPackage("..package.*.domain..")`

5. **shared는 비즈니스 로직 금지**
   - `shared/**` → `package/<domain>/domain/**` 의존 금지
   - shared는 공통 코어(결정성/에러/타입/포트)만 포함
   - 검증: `noClasses().that().resideInAPackage("..shared..").should().dependOnClassesThat().resideInAPackage("..package.*.domain..")`

6. **tooling은 런타임 도메인 호출 금지**
   - `tooling/**` → `package/**` 또는 `package/orchestration/**` 호출 금지
   - tooling은 개발/테스트 전용 도구 (런타임과 분리)
   - 검증: `noClasses().that().resideInAPackage("..tooling..").should().dependOnClassesThat().resideInAPackage("..package..")`

7. **orchestration 네이밍 규칙**
   - `package/orchestration/application/**`의 클래스는 반드시 `*Workflow` 또는 `*Orchestration`으로 끝나야 함
   - 검증: `classes().that().resideInAPackage("..package.orchestration.application..").should().haveSimpleNameEndingWith("Workflow").orShould().haveSimpleNameEndingWith("Orchestration")`

**RFC-IMPL-009 인프라 규칙 (P0)**:

8. **shared에서 Koin(DI) 사용 금지**
   - `shared/**` → `org.koin.**` 의존 금지
   - DI/Wiring은 `apps/*/wiring` 또는 `orchestration/wiring`에만 허용
   - 검증: `noClasses().that().resideInAPackage("..shared..").should().dependOnClassesThat().resideInAPackage("org.koin..")`

9. **domain에서 Resilience4j 사용 금지**
   - `package/<domain>/domain/**` → `io.github.resilience4j.**` 의존 금지
   - Resilience는 `adapters/`에서만 적용
   - 검증: `noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAPackage("io.github.resilience4j..")`

10. **orchestration.application에서 Resilience4j 사용 금지**
    - `package/orchestration/application/**` → `io.github.resilience4j.**` 의존 금지
    - Resilience는 `adapters/`에서만 적용
    - 검증: `noClasses().that().resideInAPackage("..package.orchestration.application..").should().dependOnClassesThat().resideInAPackage("io.github.resilience4j..")`

11. **shared에서 Ktor 사용 금지 (프레임워크 오염 방지)**
    - `shared/**` → `io.ktor.**` 의존 금지
    - shared는 기반 라이브러리이며 프레임워크 오염 차단
    - 검증: `noClasses().that().resideInAPackage("..shared..").should().dependOnClassesThat().resideInAPackage("io.ktor..")`

**허용 규칙 (의도적으로 체크하지 않음)**:
- `ports/**`는 모든 도메인에서 접근 가능 (도메인 간 통신 허용 경로)
- `adapters/**`에서는 Resilience4j, Ktor Client 등 프레임워크 사용 허용

**규칙 변경 절차**:
- 이 목록을 변경하려면 `ArchitectureConstraintsTest.kt`와 RFC-IMPL-001을 동시에 수정해야 함
- 규칙 추가/제거는 RFC 절차를 따름

---

4. Rollback Plan

- Gate가 과도하게 개발을 막으면:
  - ArchUnit 규칙을 “warning-only” 모드로 내리지 않는다.
  - 대신 규칙 범위를 축소(정확히 문제되는 패키지만 제한)하여 재적용한다.

