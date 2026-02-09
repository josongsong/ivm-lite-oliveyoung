import { Page, Locator, expect } from '@playwright/test';
import { BasePage } from './base.page';

export type OutboxTab = 'recent' | 'failed' | 'stale' | 'dlq';

export class OutboxPage extends BasePage {
  readonly path = '/outbox';

  // Tabs
  readonly recentTab: Locator;
  readonly failedTab: Locator;
  readonly staleTab: Locator;
  readonly dlqTab: Locator;

  // Content
  readonly eventsTable: Locator;
  readonly tableRows: Locator;

  // Actions
  readonly retryBtn: Locator;
  readonly deleteBtn: Locator;

  // Modal
  readonly detailModal: Locator;
  readonly modalCloseBtn: Locator;

  constructor(page: Page) {
    super(page);
    this.recentTab = page.getByRole('tab', { name: /recent/i });
    this.failedTab = page.getByRole('tab', { name: /failed/i });
    this.staleTab = page.getByRole('tab', { name: /stale/i });
    this.dlqTab = page.getByRole('tab', { name: /dlq|dead/i });
    this.eventsTable = page.getByRole('table').or(page.getByRole('grid'));
    this.tableRows = page.getByRole('row');
    this.retryBtn = page.getByRole('button', { name: /retry|재시도/i });
    this.deleteBtn = page.getByRole('button', { name: /delete|삭제/i });
    this.detailModal = page.getByRole('dialog');
    this.modalCloseBtn = page.getByRole('button', { name: /close|닫기/i });
  }

  async switchTab(tab: OutboxTab) {
    const tabMap = {
      recent: this.recentTab,
      failed: this.failedTab,
      stale: this.staleTab,
      dlq: this.dlqTab,
    };
    await tabMap[tab].first().click();
    await this.page.waitForTimeout(300);
  }

  async clickRow(index: number = 1) {
    await this.tableRows.nth(index).click();
  }

  async expectModalOpen() {
    await expect(this.detailModal).toBeVisible({ timeout: 5000 });
  }

  async closeModal() {
    if (await this.modalCloseBtn.isVisible()) {
      await this.modalCloseBtn.click();
    } else {
      await this.page.keyboard.press('Escape');
    }
  }

  async getRowCount(): Promise<number> {
    return (await this.tableRows.count()) - 1; // header row 제외
  }

  async retryEvent(rowIndex: number = 1) {
    await this.clickRow(rowIndex);
    await this.expectModalOpen();
    if (await this.retryBtn.isVisible()) {
      await this.retryBtn.click();
    }
  }

  async expectTabsVisible() {
    // 메인 콘텐츠 로드 확인
    await expect(this.page.getByRole('main')).toBeVisible({ timeout: 10000 });
  }

  async hasTable(): Promise<boolean> {
    return this.eventsTable.first().isVisible({ timeout: 3000 }).catch(() => false);
  }

  async hasTabs(): Promise<boolean> {
    return this.recentTab.first().isVisible({ timeout: 3000 }).catch(() => false);
  }
}
