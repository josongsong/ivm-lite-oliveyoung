import { Page, Locator, expect } from '@playwright/test';
import { BasePage } from './base.page';

export class TracesPage extends BasePage {
  readonly path = '/traces';

  // Filters
  readonly filterSection: Locator;
  readonly traceIdInput: Locator;
  readonly statusFilter: Locator;
  readonly timeRangeFilter: Locator;
  readonly dateRangeFilter: Locator;
  readonly searchBtn: Locator;

  // List
  readonly traceList: Locator;
  readonly traceRows: Locator;
  readonly traceItems: Locator;

  // Detail Panel
  readonly detailPanel: Locator;
  readonly waterfallTimeline: Locator;
  readonly spanDetails: Locator;
  readonly spans: Locator;

  // Stats
  readonly statsCards: Locator;

  constructor(page: Page) {
    super(page);
    this.filterSection = page.locator('[class*="filter"], [class*="Filter"], form');
    this.traceIdInput = page.getByPlaceholder(/trace/i);
    this.statusFilter = page.locator('select').first();
    this.timeRangeFilter = page.locator('select, [class*="time-range"]');
    this.dateRangeFilter = page.locator('[class*="date"]');
    this.searchBtn = page.getByRole('button', { name: /search|조회/i });
    this.traceList = page.locator('[class*="trace-list"], [class*="TraceList"]');
    this.traceRows = page.getByRole('row');
    this.traceItems = page.locator('[class*="trace-item"], [class*="TraceItem"], tr');
    this.detailPanel = page.locator('[class*="detail"], [class*="Detail"]');
    this.waterfallTimeline = page.locator('[class*="waterfall"], [class*="Waterfall"], [class*="timeline"]');
    this.spanDetails = page.locator('[class*="span-detail"], [class*="SpanDetail"]');
    this.spans = page.locator('[class*="span"], [class*="Span"]');
    this.statsCards = page.locator('[class*="stats"], [class*="card"]');
  }

  async searchTrace(traceId: string) {
    if (await this.traceIdInput.isVisible()) {
      await this.traceIdInput.fill(traceId);
      if (await this.searchBtn.isVisible()) {
        await this.searchBtn.click();
      }
      await this.page.waitForTimeout(500);
    }
  }

  async clickTrace(index: number = 1) {
    const items = this.traceItems;
    if (await items.nth(index).isVisible().catch(() => false)) {
      await items.nth(index).click();
    }
  }

  async expectDetailVisible() {
    await expect(this.detailPanel.first()).toBeVisible({ timeout: 5000 });
  }

  async expectDetailPanelVisible() {
    await expect(this.detailPanel.first()).toBeVisible({ timeout: 5000 });
  }

  async expectWaterfallVisible() {
    const waterfall = this.waterfallTimeline.or(this.page.locator('[class*="timeline"]'));
    await expect(waterfall.first()).toBeVisible({ timeout: 5000 });
  }

  async expectSpanDetailsVisible() {
    await expect(this.spanDetails.first().or(this.spans.first())).toBeVisible({ timeout: 5000 });
  }

  async getTraceCount(): Promise<number> {
    return (await this.traceRows.count()) - 1; // header 제외
  }

  async hasTraceItems(): Promise<boolean> {
    return this.traceItems.first().isVisible({ timeout: 3000 }).catch(() => false);
  }

  async hasSpans(): Promise<boolean> {
    return this.spans.first().isVisible({ timeout: 3000 }).catch(() => false);
  }

  async clickSpan(index: number = 0) {
    if (await this.spans.nth(index).isVisible().catch(() => false)) {
      await this.spans.nth(index).click();
    }
  }

  async selectTimeRange(range: string) {
    if (await this.timeRangeFilter.first().isVisible().catch(() => false)) {
      await this.timeRangeFilter.first().selectOption({ label: new RegExp(range, 'i') }).catch(() => {});
    }
  }

  async filterByStatus(status: string) {
    if (await this.statusFilter.isVisible().catch(() => false)) {
      await this.statusFilter.selectOption({ label: new RegExp(status, 'i') }).catch(() => {});
    }
  }

  async expectPageLoaded() {
    await expect(this.page.getByRole('main')).toBeVisible({ timeout: 10000 });
  }
}
