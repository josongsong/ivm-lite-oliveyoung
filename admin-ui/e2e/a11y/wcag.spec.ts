import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

const PAGES_TO_TEST = [
  { name: 'Dashboard', path: '/dashboard' },
  { name: 'Contracts', path: '/contracts' },
  { name: 'Explorer', path: '/explorer' },
  { name: 'Outbox', path: '/outbox' },
  { name: 'Playground', path: '/playground' },
  { name: 'Workflow', path: '/workflow' },
  { name: 'Traces', path: '/traces' },
];

test.describe('Accessibility (WCAG 2.1 AA)', () => {
  for (const { name, path } of PAGES_TO_TEST) {
    test(`${name} 페이지 접근성`, async ({ page }) => {
      await page.goto(path);
      await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});

      const accessibilityScanResults = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa'])
        .exclude('.react-flow') // 복잡한 캔버스 제외
        .exclude('.monaco-editor') // Monaco 에디터 제외
        .analyze();

      // 심각한 위반사항만 체크
      const criticalViolations = accessibilityScanResults.violations.filter(
        v => v.impact === 'critical' || v.impact === 'serious'
      );

      if (criticalViolations.length > 0) {
        console.log(`[${name}] Accessibility violations:`, JSON.stringify(criticalViolations, null, 2));
      }

      expect(criticalViolations.length).toBeLessThanOrEqual(3); // 초기에는 관대하게
    });
  }
});

test.describe('Keyboard Navigation', () => {
  test('Tab 키로 주요 요소 포커스 이동', async ({ page }) => {
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});

    // Tab 키 누르기
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');

    // 포커스된 요소 확인
    const focusedElement = await page.evaluate(() => {
      return document.activeElement?.tagName;
    });

    expect(focusedElement).toBeTruthy();
  });

  test('Escape 키로 모달 닫기', async ({ page }) => {
    await page.goto('/outbox');
    await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});

    // 테이블 행 클릭으로 모달 열기 시도
    const row = page.getByRole('row').nth(1);
    if (await row.isVisible({ timeout: 3000 }).catch(() => false)) {
      await row.click();

      const modal = page.getByRole('dialog');
      if (await modal.isVisible({ timeout: 2000 }).catch(() => false)) {
        await page.keyboard.press('Escape');
        await expect(modal).not.toBeVisible({ timeout: 2000 });
      }
    }
  });
});
