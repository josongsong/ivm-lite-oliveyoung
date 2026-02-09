import { Page, Locator, expect } from '@playwright/test';

export abstract class BasePage {
  readonly page: Page;
  readonly main: Locator;
  readonly sidebar: Locator;

  constructor(page: Page) {
    this.page = page;
    this.main = page.getByRole('main');
    this.sidebar = page.locator('nav, [class*="sidebar"]');
  }

  abstract readonly path: string;

  async goto() {
    await this.page.goto(this.path);
    await this.waitForLoad();
  }

  async waitForLoad() {
    await expect(this.main).toBeVisible({ timeout: 10000 });
  }

  async navigateTo(path: string) {
    const link = this.page.locator(`a[href*="${path}"]`).first();
    await link.click();
    await this.page.waitForURL(new RegExp(path));
  }

  async reload() {
    await this.page.reload();
    await this.waitForLoad();
  }

  async screenshot(name: string) {
    return this.page.screenshot({ path: `e2e/regression/snapshots/${name}.png`, fullPage: true });
  }
}
