import { Page, Locator, expect } from '@playwright/test';
import { BasePage } from './base.page';

export type ExplorerTab = 'rawdata' | 'slices' | 'views';

export class ExplorerPage extends BasePage {
  readonly path = '/explorer';

  // Search
  readonly entityIdInput: Locator;
  readonly tenantInput: Locator;
  readonly searchBtn: Locator;

  // Tabs
  readonly rawdataTab: Locator;
  readonly slicesTab: Locator;
  readonly viewsTab: Locator;

  // Content
  readonly dataTable: Locator;
  readonly sliceList: Locator;
  readonly rawDataEditor: Locator;
  readonly viewPreview: Locator;
  readonly lineageGraph: Locator;

  constructor(page: Page) {
    super(page);
    this.entityIdInput = page.getByPlaceholder(/entity/i).or(page.locator('input').first());
    this.tenantInput = page.getByPlaceholder(/tenant/i);
    this.searchBtn = page.getByRole('button', { name: /search|조회/i });
    this.rawdataTab = page.getByRole('tab', { name: /rawdata|raw/i }).or(page.getByRole('button', { name: /rawdata|raw/i }));
    this.slicesTab = page.getByRole('tab', { name: /slice/i }).or(page.getByRole('button', { name: /slice/i }));
    this.viewsTab = page.getByRole('tab', { name: /view/i }).or(page.getByRole('button', { name: /view/i }));
    this.dataTable = page.locator('[class*="table"], table');
    this.sliceList = page.locator('[class*="slice-list"], [class*="SliceList"]');
    this.rawDataEditor = page.locator('[class*="editor"], .monaco-editor');
    this.viewPreview = page.locator('[class*="preview"]');
    this.lineageGraph = page.locator('[class*="lineage"], [class*="graph"]');
  }

  async searchEntity(entityId: string, tenant?: string) {
    await this.entityIdInput.fill(entityId);
    if (tenant && await this.tenantInput.isVisible()) {
      await this.tenantInput.fill(tenant);
    }
    if (await this.searchBtn.isVisible()) {
      await this.searchBtn.click();
    }
    await this.page.waitForTimeout(500);
  }

  async switchTab(tab: ExplorerTab) {
    const tabLocator = tab === 'rawdata' ? this.rawdataTab :
                       tab === 'slices' ? this.slicesTab : this.viewsTab;
    if (await tabLocator.first().isVisible({ timeout: 3000 }).catch(() => false)) {
      await tabLocator.first().click();
      await this.page.waitForTimeout(300);
    }
  }

  async expectDataLoaded() {
    const hasData = await this.dataTable.first().isVisible({ timeout: 5000 }).catch(() => false) ||
                    await this.sliceList.first().isVisible({ timeout: 3000 }).catch(() => false) ||
                    await this.rawDataEditor.first().isVisible({ timeout: 3000 }).catch(() => false);
    expect(hasData).toBeTruthy();
  }

  async waitForLoad() {
    // 페이지 메인 콘텐츠 로드 대기
    await expect(this.page.getByRole('main')).toBeVisible({ timeout: 10000 });
  }
}
