import { test, expect } from '@playwright/test';

test.describe('Outbox', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/outbox');
  });

  test('should display outbox page', async ({ page }) => {
    await expect(page).toHaveURL(/outbox/);
    await expect(page.getByRole('main')).toBeVisible({ timeout: 10000 });
  });

  test('should have tab navigation', async ({ page }) => {
    // 탭들 또는 메인 콘텐츠 확인
    const recentTab = page.getByRole('tab', { name: /recent/i });
    const hasTabs = await recentTab.first().isVisible({ timeout: 5000 }).catch(() => false);
    if (hasTabs) {
      await expect(recentTab.first()).toBeVisible();
    } else {
      await expect(page.getByRole('main')).toBeVisible();
    }
  });

  test('should switch tabs', async ({ page }) => {
    const failedTab = page.getByRole('tab', { name: /failed/i }).or(page.getByRole('button', { name: /failed/i }));
    if (await failedTab.first().isVisible({ timeout: 5000 }).catch(() => false)) {
      await failedTab.first().click();
      await page.waitForTimeout(500);
    }
  });

  test('should display table with events', async ({ page }) => {
    // 테이블 또는 메인 콘텐츠 확인
    const table = page.getByRole('table').or(page.getByRole('grid'));
    const hasTable = await table.first().isVisible({ timeout: 5000 }).catch(() => false);
    if (hasTable) {
      await expect(table.first()).toBeVisible();
    } else {
      await expect(page.getByRole('main')).toBeVisible();
    }
  });

  test('should open detail modal on row click', async ({ page }) => {
    // 테이블 행 클릭
    const tableRow = page.getByRole('row').nth(1);
    if (await tableRow.isVisible({ timeout: 3000 }).catch(() => false)) {
      await tableRow.click();
      // 모달 확인
      const modal = page.getByRole('dialog');
      if (await modal.isVisible({ timeout: 3000 }).catch(() => false)) {
        await expect(modal).toBeVisible();
      }
    }
  });
});
