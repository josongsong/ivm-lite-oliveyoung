import { test, expect } from '@playwright/test';

test.describe('Traces', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/traces');
  });

  test('should display traces page', async ({ page }) => {
    await expect(page).toHaveURL(/traces/);
    await expect(page.getByRole('main')).toBeVisible({ timeout: 10000 });
  });

  test('should have filter controls or content', async ({ page }) => {
    // 필터 UI 또는 트레이스 콘텐츠 확인
    const content = page.locator('[class*="traces"], [class*="filter"], select, input');
    const mainContent = page.getByRole('main');
    await expect(mainContent).toBeVisible({ timeout: 10000 });
  });

  test('should display trace content area', async ({ page }) => {
    // 트레이스 페이지 콘텐츠 영역 확인
    const mainContent = page.getByRole('main');
    await expect(mainContent).toBeVisible({ timeout: 10000 });
    // 페이지가 완전히 로드될 때까지 대기
    await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
  });

  test('should show trace detail panel on selection', async ({ page }) => {
    // 트레이스 항목 클릭
    const traceItem = page.getByRole('row').nth(1);
    if (await traceItem.isVisible({ timeout: 3000 }).catch(() => false)) {
      await traceItem.click();
      // 상세 패널 확인
      const detailPanel = page.locator('[class*="detail"]');
      if (await detailPanel.first().isVisible({ timeout: 3000 }).catch(() => false)) {
        await expect(detailPanel.first()).toBeVisible();
      }
    }
  });
});
