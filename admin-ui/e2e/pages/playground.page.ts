import { Page, Locator, expect } from '@playwright/test';
import { BasePage } from './base.page';

export class PlaygroundPage extends BasePage {
  readonly path = '/playground';

  // Editor
  readonly editor: Locator;
  readonly inputPanel: Locator;

  // Preview
  readonly previewPanel: Locator;
  readonly outputPanel: Locator;

  // Controls
  readonly runBtn: Locator;
  readonly resetBtn: Locator;
  readonly contractSelector: Locator;

  // Status
  readonly statusBar: Locator;

  constructor(page: Page) {
    super(page);
    this.editor = page.locator('.monaco-editor').or(page.getByRole('textbox'));
    this.inputPanel = page.locator('[class*="input"], [class*="editor"]').first();
    this.previewPanel = page.locator('[class*="preview"]');
    this.outputPanel = page.locator('[class*="output"]');
    this.runBtn = page.getByRole('button', { name: /run|simulate|실행/i });
    this.resetBtn = page.getByRole('button', { name: /reset|초기화/i });
    this.contractSelector = page.locator('select').or(page.getByRole('combobox'));
    this.statusBar = page.locator('[class*="status"]');
  }

  async setInput(json: string) {
    const monaco = this.page.locator('.monaco-editor').first();
    if (await monaco.isVisible()) {
      await monaco.click();
      await this.page.keyboard.press('Meta+A');
      await this.page.keyboard.type(json);
    } else {
      const textarea = this.page.getByRole('textbox').first();
      await textarea.fill(json);
    }
  }

  async selectContract(contractId: string) {
    if (await this.contractSelector.first().isVisible()) {
      await this.contractSelector.first().selectOption({ label: new RegExp(contractId, 'i') });
    }
  }

  async run() {
    await this.runBtn.first().click();
    await this.page.waitForTimeout(500);
  }

  async expectPreviewUpdated() {
    await expect(this.previewPanel.first()).toBeVisible({ timeout: 10000 });
  }

  async getOutput(): Promise<string> {
    return (await this.previewPanel.first().textContent()) ?? '';
  }
}
