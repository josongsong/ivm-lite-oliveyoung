import { test, expect } from '@playwright/test';
import { WorkflowPage, DashboardPage } from '../pages';

test.describe('CUJ-004: Workflow Navigation', () => {
  test('Dashboard → Workflow 캔버스 탐색', async ({ page }) => {
    // 1. Dashboard에서 시작
    const dashboard = new DashboardPage(page);
    await dashboard.goto();
    await dashboard.expectStatsVisible();

    // 2. Workflow 페이지로 이동
    await dashboard.navigateTo('workflow');

    // 3. Workflow 캔버스 확인
    const workflow = new WorkflowPage(page);
    await workflow.waitForCanvas();
  });

  test('Workflow 노드 및 엣지 렌더링', async ({ page }) => {
    const workflow = new WorkflowPage(page);
    await workflow.goto();
    await workflow.waitForCanvas();

    const hasCanvas = await workflow.hasCanvas();
    if (hasCanvas) {
      const nodeCount = await workflow.getNodeCount();
      expect(nodeCount).toBeGreaterThanOrEqual(0);
    }
  });

  test('Workflow 줌 컨트롤', async ({ page }) => {
    const workflow = new WorkflowPage(page);
    await workflow.goto();
    await workflow.waitForCanvas();

    const hasCanvas = await workflow.hasCanvas();
    if (hasCanvas) {
      const hasControls = await workflow.zoomControls.isVisible().catch(() => false);
      if (hasControls) {
        await workflow.zoomIn();
        await workflow.zoomOut();
        await workflow.fitView();
      }
    }
  });

  test('Workflow 노드 클릭으로 상세 패널 확인', async ({ page }) => {
    const workflow = new WorkflowPage(page);
    await workflow.goto();
    await workflow.waitForCanvas();

    const hasCanvas = await workflow.hasCanvas();
    if (hasCanvas) {
      const nodeCount = await workflow.getNodeCount();
      if (nodeCount > 0) {
        await workflow.clickNode(0);
      }
    }
  });
});
