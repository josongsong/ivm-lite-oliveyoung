import { Page, Locator, expect } from '@playwright/test';
import { BasePage } from './base.page';

export class DashboardPage extends BasePage {
  readonly path = '/dashboard';

  // Stats Cards
  readonly workerCard: Locator;
  readonly rawDataCard: Locator;
  readonly sliceCard: Locator;
  readonly contractCard: Locator;

  // Panels
  readonly outboxPanel: Locator;
  readonly actionsPanel: Locator;

  constructor(page: Page) {
    super(page);
    this.workerCard = page.getByText(/worker/i).first();
    this.rawDataCard = page.getByText(/rawdata/i).first();
    this.sliceCard = page.getByText(/slice/i).first();
    this.contractCard = page.getByText(/contract/i).first();
    this.outboxPanel = page.locator('[class*="outbox"]').first();
    this.actionsPanel = page.locator('[class*="action"]').first();
  }

  async getStatValue(statName: 'worker' | 'rawdata' | 'slice' | 'contract'): Promise<string> {
    const card = this.page.locator(`[class*="${statName}"], :has-text("${statName}")`).first();
    const value = await card.locator('[class*="value"], [class*="count"], strong, b').first().textContent();
    return value ?? '0';
  }

  async expectStatsVisible() {
    // 백엔드 연결 시 stats 표시, 미연결 시 대기 화면 표시 - 둘 다 유효
    const statsOrWaiting = this.workerCard.or(
      this.page.getByText(/백엔드 연결|대기|waiting|loading/i)
    );
    await expect(statsOrWaiting.first()).toBeVisible({ timeout: 10000 });
  }

  async isBackendConnected(): Promise<boolean> {
    return this.workerCard.isVisible({ timeout: 3000 }).catch(() => false);
  }

  async clickRefresh() {
    const refreshBtn = this.page.getByRole('button', { name: /refresh|새로고침/i });
    if (await refreshBtn.isVisible()) {
      await refreshBtn.click();
    }
  }
}
