import { test, expect } from '@playwright/test';

test.describe('Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/dashboard');
  });

  test('should display dashboard title', async ({ page }) => {
    await expect(page.locator('h1, h2').first()).toBeVisible();
  });

  test('should display summary statistics cards', async ({ page }) => {
    // Worker, RawData, Slice, Contract 통계 카드들 확인
    await expect(page.getByText(/worker|rawdata|slice|contract/i).first()).toBeVisible({ timeout: 10000 });
  });

  test('should show main content area', async ({ page }) => {
    await expect(page.getByRole('main')).toBeVisible({ timeout: 10000 });
  });

  test('should reload dashboard without crash', async ({ page }) => {
    // 대시보드 로드 후 새로고침해도 정상 동작
    await page.goto('/dashboard');
    await expect(page.getByRole('main')).toBeVisible();

    await page.reload();
    await expect(page.getByRole('main')).toBeVisible({ timeout: 10000 });
  });
});
