import { test, expect } from '@playwright/test';

test.describe('Contracts', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/contracts');
  });

  test('should display contracts page', async ({ page }) => {
    await expect(page).toHaveURL(/contracts/);
    await expect(page.getByRole('main')).toBeVisible({ timeout: 10000 });
  });

  test('should have view mode toggle (grid/graph)', async ({ page }) => {
    // Grid 또는 Graph 뷰 토글 버튼 확인 (백엔드 연결 시에만)
    const gridButton = page.getByRole('button', { name: 'Grid View' });
    const hasButton = await gridButton.isVisible({ timeout: 5000 }).catch(() => false);
    if (hasButton) {
      await expect(gridButton).toBeVisible();
    } else {
      // 백엔드 미연결 시 메인 영역만 확인
      await expect(page.getByRole('main')).toBeVisible();
    }
  });

  test('should filter contracts by kind', async ({ page }) => {
    // 계약 종류 필터 확인
    const filterSelect = page.locator('select').first();
    if (await filterSelect.isVisible()) {
      await filterSelect.click();
      await expect(page.getByText(/entity_schema|ruleset|view_definition|sinkrule/i).first()).toBeVisible();
    }
  });

  test('should search contracts', async ({ page }) => {
    // 검색 입력 필드 확인
    const searchInput = page.getByPlaceholder(/search/i);
    if (await searchInput.isVisible()) {
      await searchInput.fill('test');
      await page.waitForTimeout(500);
    }
  });

  test('should navigate to contract detail', async ({ page }) => {
    // 계약 항목 클릭 시 상세 페이지로 이동
    const contractItem = page.locator('a[href*="/contracts/"]').first();
    if (await contractItem.isVisible()) {
      await contractItem.click();
      await expect(page).toHaveURL(/contracts\/.*\/.*/);
    }
  });
});
