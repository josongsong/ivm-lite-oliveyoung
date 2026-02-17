import { Page, Locator, expect } from '@playwright/test';
import { BasePage } from './base.page';

export type ContractKind = 'ENTITY_SCHEMA' | 'RULESET' | 'VIEW_DEFINITION' | 'SINK_RULE' | 'ALL';

export class ContractsPage extends BasePage {
  readonly path = '/contracts';

  // View Controls
  readonly gridViewBtn: Locator;
  readonly graphViewBtn: Locator;
  readonly searchInput: Locator;
  readonly kindFilter: Locator;

  // Content
  readonly contractsGrid: Locator;
  readonly contractGraph: Locator;
  readonly contractCards: Locator;

  // Stats
  readonly statsPanel: Locator;

  constructor(page: Page) {
    super(page);
    this.gridViewBtn = page.getByRole('button', { name: 'Grid View' });
    this.graphViewBtn = page.getByRole('button', { name: 'Dependency Graph' });
    this.searchInput = page.getByPlaceholder(/search/i);
    this.kindFilter = page.locator('select').first();
    this.contractsGrid = page.locator('[class*="contracts-grid"]');
    this.contractGraph = page.locator('[class*="graph"], .react-flow');
    this.contractCards = page.locator('[class*="contract-card"], a[href*="/contracts/"]');
    this.statsPanel = page.locator('[class*="stats"]');
  }

  async switchToGridView() {
    await this.gridViewBtn.click();
    await expect(this.contractsGrid).toBeVisible();
  }

  async switchToGraphView() {
    await this.graphViewBtn.click();
    // 그래프 로딩 대기 후 확인
    await this.page.waitForTimeout(2000);
    await expect(this.contractGraph.first()).toBeVisible({ timeout: 10000 });
  }

  async filterByKind(kind: ContractKind) {
    if (await this.kindFilter.isVisible()) {
      await this.kindFilter.selectOption({ label: new RegExp(kind, 'i') });
    }
  }

  async search(query: string) {
    if (await this.searchInput.isVisible()) {
      await this.searchInput.fill(query);
      await this.page.waitForTimeout(500); // debounce
    }
  }

  async clickContract(index: number = 0) {
    await this.contractCards.nth(index).click();
    await this.page.waitForURL(/contracts\/.*\/.*/);
  }

  async getContractCount(): Promise<number> {
    return this.contractCards.count();
  }

  async expectContractsLoaded() {
    // 메인 콘텐츠 로드 확인
    await expect(this.page.getByRole('main')).toBeVisible({ timeout: 10000 });
  }

  async hasContracts(): Promise<boolean> {
    return this.contractCards.first().isVisible({ timeout: 3000 }).catch(() => false);
  }
}
