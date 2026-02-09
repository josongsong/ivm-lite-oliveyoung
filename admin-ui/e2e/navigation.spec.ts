import { test, expect } from '@playwright/test';

test.describe('Navigation', () => {
  test('should load dashboard as default page', async ({ page }) => {
    await page.goto('/');
    // Dashboard로 리다이렉트 또는 접근
    await expect(page).toHaveURL(/dashboard/);
  });

  test('should navigate between pages via sidebar', async ({ page }) => {
    await page.goto('/dashboard');

    // Contracts 링크 클릭
    await page.click('a[href*="contracts"]');
    await expect(page).toHaveURL(/contracts/);

    // Explorer 링크 클릭
    await page.click('a[href*="explorer"]');
    await expect(page).toHaveURL(/explorer/);

    // Outbox 링크 클릭
    await page.click('a[href*="outbox"]');
    await expect(page).toHaveURL(/outbox/);
  });
});
