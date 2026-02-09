import { test, expect } from '@playwright/test';

test.describe('Playground', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/playground');
  });

  test('should display playground page', async ({ page }) => {
    await expect(page).toHaveURL(/playground/);
    await expect(page.getByRole('main')).toBeVisible({ timeout: 10000 });
  });

  test('should have editor panel', async ({ page }) => {
    // Monaco 에디터 또는 textarea 확인
    const monaco = page.locator('.monaco-editor');
    const textarea = page.getByRole('textbox');
    await expect(monaco.or(textarea).first()).toBeVisible({ timeout: 10000 });
  });

  test('should have preview panel', async ({ page }) => {
    // 미리보기 패널 확인 - Preview 텍스트나 패널 영역
    const previewText = page.getByText(/preview|미리보기/i);
    const previewPanel = page.locator('[class*="preview"]');
    await expect(previewText.or(previewPanel).first()).toBeVisible({ timeout: 10000 });
  });

  test('should have run/execute button', async ({ page }) => {
    // 실행 버튼 확인
    const runButton = page.getByRole('button', { name: /run|실행|execute|play|simulate/i });
    await expect(runButton.first()).toBeVisible({ timeout: 10000 });
  });
});
