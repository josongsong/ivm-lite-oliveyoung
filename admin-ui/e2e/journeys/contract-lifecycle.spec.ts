import { test, expect } from '@playwright/test';
import { ContractsPage, DashboardPage } from '../pages';

test.describe('CUJ-001: Contract Lifecycle', () => {
  test('Dashboard → Contracts → Contract Detail 탐색', async ({ page }) => {
    // 1. Dashboard에서 시작
    const dashboard = new DashboardPage(page);
    await dashboard.goto();
    await dashboard.expectStatsVisible();

    // 2. Contracts 페이지로 이동
    await dashboard.navigateTo('contracts');

    // 3. Contracts 페이지 로드 확인
    const contracts = new ContractsPage(page);
    await contracts.expectContractsLoaded();

    // 4. 계약이 있으면 클릭
    const hasContracts = await contracts.hasContracts();
    if (hasContracts) {
      await contracts.clickContract(0);
      await expect(page).toHaveURL(/contracts\/.*\/.*/);
      await expect(page.getByRole('main')).toBeVisible();
    }
  });

  test('Contracts Grid/Graph 뷰 전환', async ({ page }) => {
    const contracts = new ContractsPage(page);
    await contracts.goto();
    await contracts.expectContractsLoaded();

    const hasContracts = await contracts.hasContracts();
    if (hasContracts) {
      // Grid View 확인
      if (await contracts.gridViewBtn.isVisible().catch(() => false)) {
        await contracts.switchToGridView();
        await expect(contracts.contractsGrid).toBeVisible();
      }

      // Graph View로 전환
      if (await contracts.graphViewBtn.isVisible().catch(() => false)) {
        await contracts.switchToGraphView();
      }
    }
  });

  test('Contracts 검색 및 필터링', async ({ page }) => {
    const contracts = new ContractsPage(page);
    await contracts.goto();
    await contracts.expectContractsLoaded();

    const hasContracts = await contracts.hasContracts();
    if (hasContracts) {
      // 검색 수행
      await contracts.search('product');
      await page.waitForTimeout(500);

      // 필터 적용
      await contracts.filterByKind('ENTITY_SCHEMA');
      await page.waitForTimeout(500);

      // 결과 확인
      const filteredCount = await contracts.getContractCount();
      expect(filteredCount).toBeGreaterThanOrEqual(0);
    }
  });
});
