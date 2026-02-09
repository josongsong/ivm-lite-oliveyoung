import { Page, Locator, expect } from '@playwright/test';
import { BasePage } from './base.page';

export class WorkflowPage extends BasePage {
  readonly path = '/workflow';

  // Canvas
  readonly canvas: Locator;
  readonly nodes: Locator;
  readonly edges: Locator;

  // Controls
  readonly zoomControls: Locator;
  readonly zoomInBtn: Locator;
  readonly zoomOutBtn: Locator;
  readonly fitViewBtn: Locator;
  readonly refreshBtn: Locator;

  // Filter
  readonly entityFilter: Locator;

  // Detail Panel
  readonly detailPanel: Locator;

  constructor(page: Page) {
    super(page);
    this.canvas = page.locator('.react-flow');
    this.nodes = page.locator('.react-flow__node');
    this.edges = page.locator('.react-flow__edge');
    this.zoomControls = page.locator('.react-flow__controls');
    this.zoomInBtn = page.locator('.react-flow__controls-zoomin');
    this.zoomOutBtn = page.locator('.react-flow__controls-zoomout');
    this.fitViewBtn = page.locator('.react-flow__controls-fitview');
    this.refreshBtn = page.getByRole('button', { name: /refresh|새로고침/i });
    this.entityFilter = page.locator('select').or(page.getByRole('combobox'));
    this.detailPanel = page.locator('[class*="detail-panel"], [class*="DetailPanel"]');
  }

  async waitForCanvas() {
    // 캔버스 또는 로딩/에러 상태 확인
    const canvasOrLoading = this.canvas.or(
      this.page.getByText(/loading|로딩|에러|error|백엔드/i)
    ).or(this.page.getByRole('main'));
    await expect(canvasOrLoading.first()).toBeVisible({ timeout: 10000 });
  }

  async hasCanvas(): Promise<boolean> {
    return this.canvas.isVisible({ timeout: 3000 }).catch(() => false);
  }

  async clickNode(index: number = 0) {
    await this.nodes.nth(index).click();
  }

  async expectDetailPanelVisible() {
    await expect(this.detailPanel).toBeVisible({ timeout: 5000 });
  }

  async getNodeCount(): Promise<number> {
    return this.nodes.count();
  }

  async getEdgeCount(): Promise<number> {
    return this.edges.count();
  }

  async zoomIn() {
    await this.zoomInBtn.click({ force: true });
  }

  async zoomOut() {
    await this.zoomOutBtn.click({ force: true });
  }

  async fitView() {
    await this.fitViewBtn.click({ force: true });
  }

  async filterByEntity(entity: string) {
    if (await this.entityFilter.first().isVisible()) {
      await this.entityFilter.first().selectOption({ label: new RegExp(entity, 'i') });
    }
  }
}
