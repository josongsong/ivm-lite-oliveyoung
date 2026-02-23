# 엔지니어링 게이트 — 품질 관리

이 문서는 IVM-Lite 프로젝트의 테스트 커버리지, 린트 설정, 제약사항, CI 파이프라인, 그리고 엔지니어링 관련 게이트들을 종합적으로 정리합니다.

---

## 📊 목차

1. [테스트 커버리지](#테스트-커버리지)
2. [린트 설정](#린트-설정)
3. [코드 품질 제약사항](#코드-품질-제약사항)
4. [CI 파이프라인](#ci-파이프라인)
5. [엔지니어링 게이트](#엔지니어링-게이트)

---

## 테스트 커버리지

### Backend (Kotlin)

**현재 상태**: JaCoCo 플러그인이 명시적으로 설정되어 있지 않습니다. (`build.gradle.kts`에 주석만 존재)

**테스트 프레임워크**:
- **Kotest** (v5.9.1): 주요 테스트 프레임워크
- **MockK** (v1.13.10): 모킹 라이브러리
- **Testcontainers** (v1.21.3): 통합 테스트용

**테스트 태스크**:
```bash
./gradlew test              # 기본 테스트 (통합 테스트 제외)
./gradlew unitTest          # 단위 테스트만 (빠름)
./gradlew integrationTest   # 통합 테스트만 (Docker 필요)
./gradlew testPackage -Dpkg=slices  # 특정 패키지 테스트
```

**테스트 리포트 위치**:
- HTML 리포트: `build/reports/tests/test/index.html`
- JUnit XML: `build/reports/tests/test/TEST-*.xml`

**권장사항**: JaCoCo 플러그인 추가하여 커버리지 측정 및 게이트 설정 권장

### Frontend (TypeScript/React)

**설정 파일**: `admin-ui/vitest.config.ts`

**커버리지 설정**:
```typescript
coverage: {
  provider: 'v8',
  reporter: ['text', 'json', 'html'],
  include: ['src/**/*.{ts,tsx}'],
  exclude: [
    'src/**/*.test.{ts,tsx}',
    'src/**/*.spec.{ts,tsx}',
    'src/test/**',
    'src/main.tsx',
    'src/**/*.d.ts',
  ],
}
```

**커버리지 리포트 위치**:
- HTML 리포트: `admin-ui/coverage/index.html`
- JSON 리포트: `admin-ui/coverage/coverage-final.json`

**실행 명령어**:
```bash
cd admin-ui && pnpm test --coverage
```

---

## 린트 설정

### Backend (Kotlin) - Detekt

**설정 파일**: `config/detekt/detekt.yml`

**플러그인 버전**: `1.23.1`

**주요 설정**:
- **최대 이슈 수**: 50 (점진적으로 줄여나가기)
- **가중치**:
  - Complexity: 2
  - Style: 1
  - Comments: 1
  - Coroutines: 2
  - Exceptions: 2

**리포트 형식**:
- HTML: `build/reports/detekt/detekt.html`
- XML: `build/reports/detekt/detekt.xml`
- SARIF: `build/reports/detekt/detekt.sarif` (GitHub Code Scanning 호환)

**실행 명령어**:
```bash
./gradlew detekt              # 린트 검사
./gradlew detektBaseline      # 기준선 생성 (기존 이슈 무시)
./gradlew lint                # 린트만 실행 (빠른 체크)
```

**자동 수정**: `autoCorrect = true` (일부 규칙 자동 수정)

**아키텍처 제약**: Detekt에는 아키텍처 제약이 없습니다. **ArchUnit 테스트**에서 강제됩니다.

### Frontend (TypeScript/React) - ESLint

**설정 파일**: `admin-ui/eslint.config.js`

**주요 규칙**:
- TypeScript ESLint 권장 규칙
- React Hooks 규칙
- React Refresh 규칙
- Import 순서 정렬 (`sort-imports`)

**⚠️ 아키텍처 제약**: ESLint에는 FSD 레이어 제약 규칙이 **없습니다**. **Semgrep**으로 강제됩니다.

**실행 명령어**:
```bash
cd admin-ui && pnpm lint              # 린트 검사
cd admin-ui && pnpm lint:security     # Semgrep 보안/아키텍처 검사 (⚠️ package.json에 없음)
```

### 아키텍처 제약 강제 메커니즘

#### Backend (Kotlin)

**ArchUnit 테스트** (`ArchitectureConstraintsTest.kt`):
- 비즈니스 도메인 간 직접 import 금지
- apps는 도메인 서비스 직접 호출 금지 (orchestration 통해서만)
- shared는 비즈니스 로직 금지
- 레이어 의존성 방향 강제 (pkg → sdk → apps)

**Semgrep** (`config/semgrep/semgrep.yml`):
- pkg → sdk 역참조 금지
- pkg → apps 역참조 금지
- sdk → apps 역참조 금지
- shared 독립성 강제

**실행**:
```bash
./gradlew test --tests ArchitectureConstraintsTest  # ArchUnit 테스트
./gradlew semgrep                                    # Semgrep 검사
```

#### Frontend (TypeScript/React)

**Semgrep** (`admin-ui/.semgrep/rules.yaml`):
- `fsd-no-cross-feature-import`: features 간 직접 import 금지
- `fsd-no-upward-import-from-features`: features에서 app/widgets import 금지
- `fsd-no-upward-import-from-widgets`: widgets에서 app import 금지
- `shared-ui-no-native-button`: Native `<button>` 금지
- `shared-ui-no-native-select`: Native `<select>` 금지

**⚠️ 문제점**: 
- `lint:security` 스크립트가 `package.json`에 없음
- CI에서 Semgrep이 실행되지 않음

**권장사항**: 
- `package.json`에 `lint:security` 스크립트 추가
- CI 파이프라인에 Semgrep 단계 추가

### 보안 정적 분석 - Semgrep

**Backend 설정 파일**: `config/semgrep/semgrep.yml`

**Frontend 설정 파일**: `admin-ui/.semgrep/rules.yaml`

**실행 명령어**:
```bash
./gradlew semgrep                    # Backend (Kotlin)
cd admin-ui && semgrep --config .semgrep/rules.yaml src/  # Frontend (수동)
```

---

## 아키텍처 제약 강제 현황

### 요약

| 항목 | Backend (Kotlin) | Frontend (TypeScript/React) |
|------|------------------|------------------------------|
| **아키텍처 제약 도구** | ArchUnit 테스트 + Semgrep | Semgrep만 (ESLint 없음) |
| **CI에서 실행** | ✅ (`./gradlew test`) | ❌ **미실행** |
| **로컬 실행 가능** | ✅ | ⚠️ (스크립트 없음) |
| **상태** | ✅ **완전히 강제됨** | ⚠️ **규칙은 있으나 강제 안 됨** |

### Backend (Kotlin)

#### ArchUnit 테스트 (`ArchitectureConstraintsTest.kt`)

**강제되는 규칙**:
- ✅ 비즈니스 도메인(rawdata, slices, changeset) 간 직접 import 금지
- ✅ apps는 도메인 서비스 직접 호출 금지 (orchestration 통해서만)
- ✅ shared는 비즈니스 로직 금지
- ✅ 레이어 의존성 방향 강제

**실행**:
```bash
./gradlew test --tests ArchitectureConstraintsTest
```

**CI 통합**: `./gradlew test`에 포함되어 자동 실행됨 ✅

#### Semgrep (`config/semgrep/semgrep.yml`)

**강제되는 규칙**:
- ✅ pkg → sdk 역참조 금지
- ✅ pkg → apps 역참조 금지
- ✅ sdk → apps 역참조 금지
- ✅ shared 독립성 강제

**실행**:
```bash
./gradlew semgrep
```

**CI 통합**: 수동 실행 (자동화 권장)

### Frontend (TypeScript/React)

#### Semgrep (`admin-ui/.semgrep/rules.yaml`)

**정의된 규칙**:
- ✅ `fsd-no-cross-feature-import`: features 간 직접 import 금지
- ✅ `fsd-no-upward-import-from-features`: features에서 app/widgets import 금지
- ✅ `fsd-no-upward-import-from-widgets`: widgets에서 app import 금지
- ✅ `shared-ui-no-native-button`: Native `<button>` 금지
- ✅ `shared-ui-no-native-select`: Native `<select>` 금지

**⚠️ 문제점**:
- ❌ `lint:security` 스크립트가 `package.json`에 없음
- ❌ CI에서 실행되지 않음
- ❌ ESLint에 아키텍처 제약 규칙 없음

**현재 실행 방법** (수동):
```bash
cd admin-ui && semgrep --config .semgrep/rules.yaml src/
```

**권장 조치**: 아래 "권장 개선사항" 섹션 참조

---

## 코드 품질 제약사항

### Backend (Kotlin) - Detekt 규칙

#### 복잡도 제약

| 규칙 | 임계값 | 설명 |
|------|-------|------|
| `LongParameterList` | 함수: 8, 생성자: 12 | 파라미터 개수 제한 |
| `LongMethod` | 80줄 | 메서드 길이 제한 |
| `TooManyFunctions` | 파일/클래스: 30개 | 함수 개수 제한 |
| `CyclomaticComplexMethod` | 15 | 순환 복잡도 제한 |
| `NestedBlockDepth` | 5 | 중첩 깊이 제한 |

#### 코루틴 규칙

- `GlobalCoroutineUsage`: GlobalScope 사용 금지
- `InjectDispatcher`: Dispatcher 주입 권장
- `RedundantSuspendModifier`: 불필요한 suspend 제거
- `SleepInsteadOfDelay`: Thread.sleep() 대신 delay() 사용

#### 스타일 규칙

- `MaxLineLength`: 140자
- `NoTabs`: 탭 사용 금지 (스페이스만)
- `NewLineAtEndOfFile`: 파일 끝 개행 필수
- `ModifierOrder`: 수정자 순서 강제

### Frontend (TypeScript/React) - ESLint 규칙

#### 아키텍처 제약 (FSD)

**레이어 의존성 규칙**:
```
app → widgets → features → shared
```

| From | Can Import |
|------|------------|
| `app/` | widgets, features, shared |
| `widgets/` | features, shared |
| `features/` | shared ONLY |
| `shared/` | external packages only |

**⚠️ 중요**: ESLint에는 아키텍처 제약 규칙이 **없습니다**. **Semgrep**으로만 강제됩니다.

**위반 시**: 
- Semgrep ERROR (현재 CI에서 실행 안 됨)
- ESLint: 에러 없음 (import order만 검사)

#### 컴포넌트 제약

**Native HTML 요소 금지** (shared/ui 외부):
- `<button>` → `<Button>`, `<IconButton>` 사용
- `<select>` → `<Select>` 사용

#### 코드 품질 제약

| 규칙 | 임계값 | 설명 |
|------|-------|------|
| Function complexity | ≤ 20 | 복잡도 제한 |
| Function lines | ≤ 150 | 함수 길이 제한 |
| Nesting depth | ≤ 5 | 중첩 깊이 제한 |
| Parameters | ≤ 6 | 파라미터 개수 제한 |

#### 보안 제약 (Semgrep)

**금지 패턴**:
- `dangerouslySetInnerHTML` (XSS 위험)
- 하드코딩된 시크릿 (API 키, 비밀번호)
- `as any` (타입 안전성 저하)

---

## CI 파이프라인

### 1. Package Publish (`package-publish.yml`)

**트리거**:
- 태그 푸시: `v*` (예: `v1.0.0`)
- 수동 실행: `workflow_dispatch`
- 다른 워크플로우에서 호출: `workflow_call`

**단계**:
1. ✅ Checkout
2. ✅ Java 17 설정
3. ✅ Gradle 캐시
4. ✅ **빌드 및 테스트**: `./gradlew clean build test`
5. ✅ **패키지 배포**: GitHub Packages 또는 Nexus

**게이트**: 빌드 및 테스트 통과 필수

### 2. Admin UI CI (`admin-ui.yml`)

**트리거**:
- `main` 브랜치 푸시/PR (경로: `admin-ui/**`)

**단계**:
1. ✅ Checkout
2. ✅ Node.js 설정 (`.nvmrc` 기반)
3. ✅ pnpm 설정 (v10.28.2)
4. ✅ pnpm 캐시
5. ✅ 의존성 설치: `pnpm install --frozen-lockfile`
6. ✅ **TypeScript 체크**: `pnpm run typecheck`
7. ✅ **린트**: `pnpm run lint`
8. ✅ **빌드**: `pnpm run build`

**⚠️ 누락된 단계**:
- ❌ **Semgrep 아키텍처/보안 검사**: FSD 레이어 제약 검증 없음

**게이트**: 모든 단계 통과 필수

### 3. E2E Tests (`e2e.yml`)

**트리거**:
- `main` 브랜치 푸시/PR (경로: `admin-ui/**`)

**작업**:
1. **E2E (Chromium)**: 기본 E2E 테스트
2. **E2E (Cross-Browser)**: Firefox, WebKit 테스트
3. **Accessibility Tests**: 접근성 테스트
4. **Performance Tests**: 성능 테스트
5. **Visual Regression**: 시각적 회귀 테스트

**게이트**: Chromium 테스트 통과 후 다른 테스트 실행

---

## 엔지니어링 게이트

### Pre-Commit 게이트 (로컬)

**권장 체크리스트**:
```bash
# Backend
./gradlew unitTest          # 단위 테스트 통과
./gradlew detekt            # 린트 통과

# Frontend
cd admin-ui && pnpm lint    # 린트 통과
cd admin-ui && pnpm typecheck  # 타입 체크 통과
```

### CI 게이트 (자동)

#### Backend 게이트

| 게이트 | 조건 | 실패 시 |
|-------|------|---------|
| **빌드** | `./gradlew clean build` 성공 | PR 병합 불가 |
| **테스트** | 모든 단위 테스트 통과 | PR 병합 불가 |
| **린트** | Detekt 이슈 ≤ 50 | PR 병합 불가 |
| **보안** | Semgrep 블로킹 이슈 없음 | 경고 (선택적) |

#### Frontend 게이트

| 게이트 | 조건 | 실패 시 | 상태 |
|-------|------|---------|------|
| **타입 체크** | TypeScript 컴파일 성공 | PR 병합 불가 | ✅ 활성화 |
| **린트** | ESLint 에러 없음 | PR 병합 불가 | ✅ 활성화 |
| **빌드** | 프로덕션 빌드 성공 | PR 병합 불가 | ✅ 활성화 |
| **아키텍처 검사** | Semgrep FSD 규칙 통과 | PR 병합 불가 | ❌ **미활성화** |
| **보안 검사** | Semgrep 보안 규칙 통과 | 경고 (선택적) | ❌ **미활성화** |
| **E2E** | Chromium 테스트 통과 | PR 병합 불가 (선택적) | ⚠️ 선택적 |

### 배포 게이트

#### 패키지 배포 (`package-publish.yml`)

**게이트**:
1. ✅ 태그 형식: `v*` (예: `v1.0.0`)
2. ✅ 빌드 성공
3. ✅ 테스트 통과
4. ✅ 패키지 배포 성공

**실패 시**: 배포 중단

### 품질 게이트 요약

| 게이트 | Backend | Frontend | 필수 여부 |
|--------|---------|----------|-----------|
| **컴파일/빌드** | ✅ | ✅ | 필수 |
| **단위 테스트** | ✅ | ⚠️ (Vitest) | 필수 |
| **통합 테스트** | ⚠️ (선택적) | ❌ | 선택적 |
| **E2E 테스트** | ❌ | ✅ | 선택적 |
| **린트** | ✅ (Detekt) | ✅ (ESLint) | 필수 |
| **타입 체크** | ✅ (컴파일 시) | ✅ (TypeScript) | 필수 |
| **보안 스캔** | ⚠️ (Semgrep) | ⚠️ (Semgrep) | 선택적 |
| **커버리지** | ❌ (미설정) | ✅ (Vitest) | 선택적 |

---

## 권장 개선사항

### 1. Frontend 아키텍처 제약 강제 활성화 (⚠️ 중요)

**현재 문제점**:
- Semgrep 규칙은 정의되어 있지만 CI에서 실행되지 않음
- `lint:security` 스크립트가 `package.json`에 없음
- ESLint에 아키텍처 제약 규칙 없음

**권장 조치**:

#### 1-1. package.json에 스크립트 추가
```json
{
  "scripts": {
    "lint:security": "semgrep --config .semgrep/rules.yaml src/",
    "check": "pnpm typecheck && pnpm lint && pnpm lint:security"
  }
}
```

#### 1-2. CI 파이프라인에 Semgrep 단계 추가
```yaml
# .github/workflows/admin-ui.yml에 추가
- name: Architecture & Security Check
  run: pnpm lint:security
```

#### 1-3. ESLint 플러그인 추가 (선택적, 더 강력한 검증)
```bash
pnpm add -D eslint-plugin-boundaries
```

```js
// eslint.config.js에 추가
import boundaries from 'eslint-plugin-boundaries'

export default tseslint.config({
  plugins: {
    boundaries: boundaries,
  },
  rules: {
    'boundaries/element-types': ['error', {
      default: 'disallow',
      rules: [
        {
          from: 'features',
          allow: ['shared'],
          disallow: ['app', 'widgets', 'features'],
        },
        {
          from: 'widgets',
          allow: ['features', 'shared'],
          disallow: ['app'],
        },
        {
          from: 'app',
          allow: ['widgets', 'features', 'shared'],
        },
      ],
    }],
  },
})
```

### 2. Backend 테스트 커버리지 추가

**현재**: JaCoCo 플러그인 미설정

**권장**:
```kotlin
// build.gradle.kts에 추가
plugins {
    id("jacoco")
}

jacoco {
    toolVersion = "0.8.11"
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}
```

**커버리지 게이트 설정**:
```kotlin
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()  // 80% 커버리지 요구
            }
        }
    }
}
```

### 3. CI 파이프라인에 커버리지 업로드 추가

**GitHub Actions 예시**:
```yaml
- name: Upload coverage to Codecov
  uses: codecov/codecov-action@v3
  with:
    files: ./build/reports/jacoco/test/jacocoTestReport.xml
    flags: backend
```

### 4. 린트 게이트 강화

**Detekt 최대 이슈 수 점진적 감소**:
- 현재: 50
- 목표: 0 (단계적 감소)

### 5. E2E 테스트 게이트 강화

**현재**: 선택적 (실패해도 PR 병합 가능)

**권장**: 필수 게이트로 전환 (중요 기능에 한해)

---

## 참고 자료

- [Detekt 설정](./config/detekt/detekt.yml)
- [ESLint 설정](./admin-ui/eslint.config.js)
- [Vitest 설정](./admin-ui/vitest.config.ts)
- [CI 워크플로우](./.github/workflows/)
- [빌드 설정](./build.gradle.kts)
- [프로젝트 규칙](./.cursorrules)

---

**최종 업데이트**: 2026-02-06
