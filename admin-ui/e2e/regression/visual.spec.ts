import { test, expect } from '@playwright/test';
import { DashboardPage, ContractsPage, WorkflowPage, PlaygroundPage } from '../pages';

test.describe('Visual Regression Tests', () => {
  test.describe.configure({ retries: 0 }); // Visual 테스트는 재시도 없이

  test('Dashboard 스냅샷', async ({ page }) => {
    const dashboard = new DashboardPage(page);
    await dashboard.goto();
    await dashboard.expectStatsVisible();

    // 데이터 로드 대기
    await page.waitForTimeout(1000);

    await expect(page).toHaveScreenshot('dashboard.png', {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
    });
  });

  test('Contracts Grid 스냅샷', async ({ page }) => {
    const contracts = new ContractsPage(page);
    await contracts.goto();
    await contracts.expectContractsLoaded();

    await expect(page).toHaveScreenshot('contracts-grid.png', {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
    });
  });

  test('Contracts Graph 스냅샷', async ({ page }) => {
    const contracts = new ContractsPage(page);
    await contracts.goto();
    await contracts.expectContractsLoaded();
    await contracts.switchToGraphView();

    // 그래프 렌더링 대기
    await page.waitForTimeout(1000);

    await expect(page).toHaveScreenshot('contracts-graph.png', {
      maxDiffPixelRatio: 0.05, // 그래프는 약간의 변동 허용
      animations: 'disabled',
    });
  });

  test('Workflow Canvas 스냅샷', async ({ page }) => {
    const workflow = new WorkflowPage(page);
    await workflow.goto();
    await workflow.waitForCanvas();
    await workflow.fitView();

    // 캔버스 안정화 대기
    await page.waitForTimeout(1000);

    await expect(page).toHaveScreenshot('workflow-canvas.png', {
      maxDiffPixelRatio: 0.05,
      animations: 'disabled',
    });
  });

  test('Playground 스냅샷', async ({ page }) => {
    const playground = new PlaygroundPage(page);
    await playground.goto();

    await expect(page).toHaveScreenshot('playground.png', {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
    });
  });
});
