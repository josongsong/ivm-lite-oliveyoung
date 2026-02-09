# SOTA급 E2E Test Suite 계획

## 현재 상태
- **33개 테스트 통과** (15.5초)
- 8개 페이지 기본 커버리지 확보
- Playwright + Chromium 단일 브라우저

---

## Phase 1: 테스트 인프라 강화

### 1.1 Visual Regression Testing
```typescript
// Percy 또는 Playwright 내장 스냅샷 활용
test('dashboard visual regression', async ({ page }) => {
  await page.goto('/dashboard');
  await expect(page).toHaveScreenshot('dashboard.png', {
    maxDiffPixelRatio: 0.01,
  });
});
```

**도구 선택:**
| 도구 | 장점 | 단점 |
|------|------|------|
| Percy | 클라우드 기반, CI 통합 우수 | 유료 |
| Playwright Screenshots | 내장, 무료 | 직접 관리 필요 |
| Chromatic | Storybook 통합 | Storybook 의존 |

**권장:** Playwright 내장 스냅샷 (무료 + 충분한 기능)

### 1.2 Cross-Browser Testing
```typescript
// playwright.config.ts
projects: [
  { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
  { name: 'webkit', use: { ...devices['Desktop Safari'] } },
  { name: 'mobile-chrome', use: { ...devices['Pixel 5'] } },
  { name: 'mobile-safari', use: { ...devices['iPhone 12'] } },
]
```

### 1.3 API Mocking Layer (MSW)
```typescript
// e2e/mocks/handlers.ts
import { http, HttpResponse } from 'msw';

export const handlers = [
  http.get('/api/dashboard', () => {
    return HttpResponse.json({
      stats: { workers: 5, rawdata: 1000, slices: 500 }
    });
  }),

  // 에러 시나리오
  http.get('/api/contracts', () => {
    return HttpResponse.error();
  }),
];
```

---

## Phase 2: 테스트 케이스 확장

### 2.1 Critical User Journeys (CUJ)

```gherkin
# CUJ-001: Contract 생성 → 시뮬레이션 → 배포
Feature: Contract Lifecycle
  Scenario: 신규 Entity Schema 생성 및 검증
    Given 사용자가 Contracts 페이지에 있다
    When "New Contract" 버튼을 클릭한다
    And ENTITY_SCHEMA 타입을 선택한다
    And YAML 에디터에 스키마를 입력한다
    Then 실시간 유효성 검사가 통과한다
    And "Simulate" 버튼으로 영향도를 확인한다
    And "Save" 버튼으로 저장한다

# CUJ-002: Data Explorer 조회 플로우
Feature: Data Exploration
  Scenario: Entity 검색 → Slice 확인 → View 미리보기
    Given 사용자가 Explorer 페이지에 있다
    When entity ID로 검색한다
    Then RawData 탭에 원본 데이터가 표시된다
    When Slices 탭으로 전환한다
    Then 슬라이스 목록이 표시된다
    When Views 탭으로 전환한다
    Then 뷰 미리보기가 표시된다

# CUJ-003: Outbox 재처리 플로우
Feature: Outbox Management
  Scenario: 실패 이벤트 재처리
    Given Failed 탭에 실패한 이벤트가 있다
    When 이벤트를 선택한다
    And "Retry" 버튼을 클릭한다
    Then 이벤트가 재처리 대기열에 추가된다
    And 성공 시 Recent 탭으로 이동한다

# CUJ-004: Workflow 시각화 탐색
Feature: Workflow Navigation
  Scenario: 노드 클릭으로 상세 정보 확인
    Given Workflow 페이지에 있다
    When RawData 노드를 클릭한다
    Then 상세 패널에 노드 정보가 표시된다
    When 연결된 Slice 노드를 클릭한다
    Then 상세 패널이 업데이트된다

# CUJ-005: Playground 시뮬레이션
Feature: Playground Simulation
  Scenario: 실시간 데이터 변환 미리보기
    Given Playground 페이지에 있다
    When 샘플 입력 데이터를 입력한다
    And Contract를 선택한다
    And "Simulate" 버튼을 클릭한다
    Then Preview 패널에 변환 결과가 표시된다
```

### 2.2 Edge Case 테스트

```typescript
// e2e/edge-cases/
├── network-failures.spec.ts    // 네트워크 장애 시나리오
├── large-datasets.spec.ts      // 대용량 데이터 처리
├── concurrent-users.spec.ts    // 동시 접속 시뮬레이션
├── session-timeout.spec.ts     // 세션 만료 처리
├── browser-back.spec.ts        // 브라우저 뒤로가기
└── keyboard-navigation.spec.ts // 키보드 접근성
```

### 2.3 Accessibility Testing (a11y)

```typescript
import { injectAxe, checkA11y } from 'axe-playwright';

test('dashboard meets WCAG 2.1 AA', async ({ page }) => {
  await page.goto('/dashboard');
  await injectAxe(page);
  await checkA11y(page, undefined, {
    detailedReport: true,
    axeOptions: {
      runOnly: ['wcag2a', 'wcag2aa'],
    },
  });
});
```

---

## Phase 3: 성능 테스트 통합

### 3.1 Core Web Vitals 측정

```typescript
// e2e/performance/web-vitals.spec.ts
import { test, expect } from '@playwright/test';

test('dashboard performance metrics', async ({ page }) => {
  await page.goto('/dashboard');

  const metrics = await page.evaluate(() => {
    return new Promise((resolve) => {
      new PerformanceObserver((list) => {
        const entries = list.getEntries();
        resolve({
          LCP: entries.find(e => e.entryType === 'largest-contentful-paint')?.startTime,
          FID: entries.find(e => e.entryType === 'first-input')?.processingStart,
          CLS: entries.find(e => e.entryType === 'layout-shift')?.value,
        });
      }).observe({ entryTypes: ['largest-contentful-paint', 'first-input', 'layout-shift'] });
    });
  });

  expect(metrics.LCP).toBeLessThan(2500);  // Good: <2.5s
  expect(metrics.CLS).toBeLessThan(0.1);   // Good: <0.1
});
```

### 3.2 Load Time Budgets

```typescript
// e2e/performance/budgets.spec.ts
const PAGE_BUDGETS = {
  '/dashboard': { load: 3000, fcp: 1500 },
  '/contracts': { load: 2500, fcp: 1200 },
  '/explorer': { load: 3500, fcp: 1800 },
  '/workflow': { load: 4000, fcp: 2000 },  // 그래프 렌더링 고려
};

for (const [path, budget] of Object.entries(PAGE_BUDGETS)) {
  test(`${path} meets performance budget`, async ({ page }) => {
    const start = Date.now();
    await page.goto(path);
    const loadTime = Date.now() - start;

    expect(loadTime).toBeLessThan(budget.load);
  });
}
```

---

## Phase 4: CI/CD 파이프라인 통합

### 4.1 GitHub Actions Workflow

```yaml
# .github/workflows/e2e.yml
name: E2E Tests

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  e2e:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        shard: [1, 2, 3, 4]  # 병렬 실행
    steps:
      - uses: actions/checkout@v4

      - name: Setup Node
        uses: actions/setup-node@v4
        with:
          node-version: 20

      - name: Install dependencies
        run: cd admin-ui && pnpm install

      - name: Install Playwright
        run: cd admin-ui && npx playwright install --with-deps

      - name: Start backend
        run: ./gradlew runAdminDev &

      - name: Run E2E tests
        run: cd admin-ui && pnpm test:e2e --shard=${{ matrix.shard }}/4

      - name: Upload artifacts
        uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: playwright-report-${{ matrix.shard }}
          path: admin-ui/playwright-report/
```

### 4.2 Test Reporting

```typescript
// playwright.config.ts
reporter: [
  ['html', { open: 'never' }],
  ['json', { outputFile: 'test-results/results.json' }],
  ['github'],  // GitHub Actions 통합
  ['blob', { outputDir: 'blob-report' }],  // 병합용
],
```

---

## Phase 5: 테스트 데이터 관리

### 5.1 Fixtures 시스템

```typescript
// e2e/fixtures/index.ts
import { test as base } from '@playwright/test';

type TestFixtures = {
  authenticatedPage: Page;
  testContract: ContractData;
  testRawData: RawDataRecord;
};

export const test = base.extend<TestFixtures>({
  authenticatedPage: async ({ page }, use) => {
    // 인증 상태 설정 (필요시)
    await use(page);
  },

  testContract: async ({}, use) => {
    const contract = await createTestContract();
    await use(contract);
    await deleteTestContract(contract.id);
  },

  testRawData: async ({}, use) => {
    const rawData = await createTestRawData();
    await use(rawData);
    await deleteTestRawData(rawData.id);
  },
});
```

### 5.2 Seed Data Generator

```typescript
// e2e/utils/seed.ts
export async function seedDatabase() {
  await api.post('/api/test/seed', {
    contracts: [
      { kind: 'ENTITY_SCHEMA', id: 'test.product.v1' },
      { kind: 'RULESET', id: 'test.product.rules' },
    ],
    rawData: [
      { entityId: 'product-001', data: { name: 'Test Product' } },
    ],
  });
}

export async function cleanupDatabase() {
  await api.post('/api/test/cleanup');
}
```

---

## Phase 6: 모니터링 및 유지보수

### 6.1 Flaky Test Detection

```typescript
// playwright.config.ts
retries: process.env.CI ? 2 : 0,
```

```yaml
# 주간 flaky test 리포트
- name: Analyze flaky tests
  run: npx playwright test --repeat-each=5 --reporter=json
```

### 6.2 Test Coverage Tracking

| 메트릭 | 현재 | 목표 |
|--------|------|------|
| 페이지 커버리지 | 100% (8/8) | 100% |
| CUJ 커버리지 | 0% | 100% |
| Edge Case | 0% | 80% |
| Visual Regression | 0% | 주요 페이지 |
| Accessibility | 0% | WCAG 2.1 AA |
| Performance | 0% | Core Web Vitals |

---

## 구현 우선순위

### Sprint 1 (즉시)
- [ ] CUJ 테스트 5개 작성
- [ ] Visual Regression 기본 설정
- [ ] CI/CD 파이프라인 구축

### Sprint 2
- [ ] MSW Mock Layer 구축
- [ ] Edge Case 테스트 추가
- [ ] Cross-Browser 테스트

### Sprint 3
- [ ] Accessibility 테스트
- [ ] Performance 테스트
- [ ] Flaky Test 모니터링

### Sprint 4
- [ ] Fixtures 시스템 고도화
- [ ] 테스트 리포팅 대시보드
- [ ] 문서화 완료

---

## 예상 결과

```
┌─────────────────────────────────────────────┐
│           E2E Test Suite Summary            │
├─────────────────────────────────────────────┤
│ Total Tests:        150+                    │
│ Execution Time:     < 5분 (4-shard 병렬)     │
│ Browser Coverage:   Chrome, Firefox, Safari │
│ Mobile Coverage:    iOS, Android            │
│ Visual Tests:       20+                     │
│ A11y Compliance:    WCAG 2.1 AA            │
│ Flaky Rate:         < 1%                    │
└─────────────────────────────────────────────┘
```

---

## 파일 구조 (최종)

```
admin-ui/
├── e2e/
│   ├── fixtures/
│   │   ├── index.ts
│   │   └── test-data.ts
│   ├── mocks/
│   │   ├── handlers.ts
│   │   └── server.ts
│   ├── pages/                    # Page Object Model
│   │   ├── dashboard.page.ts
│   │   ├── contracts.page.ts
│   │   ├── explorer.page.ts
│   │   └── ...
│   ├── journeys/                 # Critical User Journeys
│   │   ├── contract-lifecycle.spec.ts
│   │   ├── data-exploration.spec.ts
│   │   └── ...
│   ├── regression/               # Visual Regression
│   │   ├── snapshots/
│   │   └── visual.spec.ts
│   ├── a11y/                     # Accessibility
│   │   └── wcag.spec.ts
│   ├── performance/              # Performance
│   │   ├── web-vitals.spec.ts
│   │   └── budgets.spec.ts
│   ├── edge-cases/               # Edge Cases
│   │   └── ...
│   └── *.spec.ts                 # 기존 기본 테스트
├── playwright.config.ts
└── package.json
```
