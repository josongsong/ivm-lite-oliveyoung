import { test, expect } from '@playwright/test';
import { ExplorerPage, DashboardPage } from '../pages';

test.describe('CUJ-002: Data Exploration', () => {
  test('Dashboard → Explorer → 데이터 탐색', async ({ page }) => {
    // 1. Dashboard에서 시작
    const dashboard = new DashboardPage(page);
    await dashboard.goto();
    await dashboard.expectStatsVisible();

    // 2. Explorer 페이지로 이동
    await dashboard.navigateTo('explorer');

    // 3. Explorer 페이지 로드 확인
    const explorer = new ExplorerPage(page);
    await explorer.waitForLoad();
    await expect(page).toHaveURL(/explorer/);
  });

  test('Explorer 탭 네비게이션', async ({ page }) => {
    const explorer = new ExplorerPage(page);
    await explorer.goto();

    // RawData 탭
    await explorer.switchTab('rawdata');
    await page.waitForTimeout(300);

    // Slices 탭으로 전환
    await explorer.switchTab('slices');
    await page.waitForTimeout(300);

    // Views 탭으로 전환
    await explorer.switchTab('views');
    await page.waitForTimeout(300);
  });

  test('Explorer 검색 기능', async ({ page }) => {
    const explorer = new ExplorerPage(page);
    await explorer.goto();

    // Entity 검색
    await explorer.searchEntity('product-001');

    // 페이지가 정상적으로 반응하는지 확인
    await expect(page.getByRole('main')).toBeVisible();
  });
});
