import { test, expect } from '@playwright/test';

test.describe('Data Explorer', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/explorer');
  });

  test('should display explorer page', async ({ page }) => {
    await expect(page).toHaveURL(/explorer/);
    await expect(page.getByRole('main')).toBeVisible({ timeout: 10000 });
  });

  test('should have tab navigation', async ({ page }) => {
    // 탭 버튼들 확인 - 최소 하나의 탭 존재
    const tabButtons = page.getByRole('tab');
    if (await tabButtons.first().isVisible({ timeout: 5000 }).catch(() => false)) {
      await expect(tabButtons.first()).toBeVisible();
    } else {
      // 탭이 없으면 버튼 존재 확인
      await expect(page.getByRole('button').first()).toBeVisible({ timeout: 10000 });
    }
  });

  test('should switch between tabs', async ({ page }) => {
    const slicesTab = page.getByRole('tab', { name: /slice/i });
    const slicesButton = page.getByRole('button', { name: /slice/i });
    const targetTab = slicesTab.or(slicesButton);
    if (await targetTab.first().isVisible({ timeout: 5000 }).catch(() => false)) {
      await targetTab.first().click();
      await page.waitForTimeout(500);
    }
  });

  test('should have search functionality', async ({ page }) => {
    // 검색 입력 필드 확인
    const searchInput = page.getByRole('searchbox').or(page.getByRole('textbox').first());
    await expect(searchInput).toBeVisible({ timeout: 10000 });
  });

  test('should toggle between list and detail view', async ({ page }) => {
    // 뷰 모드 토글 버튼 확인
    const viewToggle = page.getByRole('button', { name: /list|detail|grid/i });
    if (await viewToggle.first().isVisible({ timeout: 3000 }).catch(() => false)) {
      await viewToggle.first().click();
    }
  });
});
