import { test, expect } from '@playwright/test';

interface PerformanceMetrics {
  fcp: number;      // First Contentful Paint
  lcp: number;      // Largest Contentful Paint
  ttfb: number;     // Time to First Byte
  domLoad: number;  // DOM Content Loaded
  fullLoad: number; // Full Page Load
}

// 개발 모드 버짓 (Vite HMR 포함, 프로덕션은 더 빠름)
const PAGE_BUDGETS: Record<string, { load: number; fcp: number }> = {
  '/dashboard': { load: 5000, fcp: 2000 },
  '/contracts': { load: 5000, fcp: 2000 },
  '/explorer': { load: 5000, fcp: 2000 },
  '/outbox': { load: 5000, fcp: 2000 },
  '/workflow': { load: 6000, fcp: 2500 },
  '/playground': { load: 8000, fcp: 3000 }, // Monaco 에디터 로드
  '/traces': { load: 5000, fcp: 2000 },
};

async function measurePerformance(page: any, path: string): Promise<PerformanceMetrics> {
  const startTime = Date.now();

  await page.goto(path);
  await page.waitForLoadState('domcontentloaded');
  const domLoad = Date.now() - startTime;

  await page.waitForLoadState('load');
  const fullLoad = Date.now() - startTime;

  const metrics = await page.evaluate(() => {
    const paint = performance.getEntriesByType('paint');
    const nav = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming;

    const fcp = paint.find(p => p.name === 'first-contentful-paint')?.startTime ?? 0;

    return {
      fcp,
      ttfb: nav?.responseStart ?? 0,
    };
  });

  return {
    fcp: metrics.fcp,
    lcp: 0, // LCP는 별도 observer 필요
    ttfb: metrics.ttfb,
    domLoad,
    fullLoad,
  };
}

test.describe('Performance Budgets', () => {
  for (const [path, budget] of Object.entries(PAGE_BUDGETS)) {
    test(`${path} 로드 시간 < ${budget.load}ms`, async ({ page }) => {
      const metrics = await measurePerformance(page, path);

      console.log(`[${path}] Performance:`, {
        fullLoad: `${metrics.fullLoad}ms`,
        domLoad: `${metrics.domLoad}ms`,
        fcp: `${metrics.fcp}ms`,
        ttfb: `${metrics.ttfb}ms`,
      });

      expect(metrics.fullLoad).toBeLessThan(budget.load);
    });
  }
});

test.describe('Core Web Vitals', () => {
  test('Dashboard FCP < 1.5s', async ({ page }) => {
    const metrics = await measurePerformance(page, '/dashboard');
    expect(metrics.fcp).toBeLessThan(1500);
  });

  test('모든 페이지 TTFB < 600ms', async ({ page }) => {
    for (const path of Object.keys(PAGE_BUDGETS)) {
      const metrics = await measurePerformance(page, path);
      expect(metrics.ttfb).toBeLessThan(600);
    }
  });
});

test.describe('Bundle Size Check', () => {
  test('초기 JS 번들 크기 확인', async ({ page }) => {
    await page.goto('/dashboard');

    const resources = await page.evaluate(() => {
      return performance.getEntriesByType('resource')
        .filter((r: PerformanceResourceTiming) => r.initiatorType === 'script')
        .map((r: PerformanceResourceTiming) => ({
          name: r.name.split('/').pop(),
          size: r.transferSize,
        }));
    });

    console.log('JS Bundles:', resources);

    // 총 JS 크기 확인 (개발모드 10MB 미만, 프로덕션은 더 작음)
    const totalSize = resources.reduce((sum: number, r: any) => sum + (r.size || 0), 0);
    expect(totalSize).toBeLessThan(10 * 1024 * 1024);
  });
});
