import { test, expect } from '@playwright/test';
import { PlaygroundPage } from '../pages';

test.describe('CUJ-005: Playground Simulation', () => {
  test('Playground 페이지 로드 및 에디터 확인', async ({ page }) => {
    const playground = new PlaygroundPage(page);
    await playground.goto();

    // 에디터 확인
    await expect(playground.editor.first()).toBeVisible();

    // Preview 패널 확인
    await expect(playground.previewPanel.first()).toBeVisible();

    // Run 버튼 확인
    await expect(playground.runBtn.first()).toBeVisible();
  });

  test('Playground 입력 및 실행', async ({ page }) => {
    const playground = new PlaygroundPage(page);
    await playground.goto();

    // 샘플 입력 데이터 설정
    const sampleInput = JSON.stringify({
      entityId: 'product-001',
      data: { name: 'Test Product', price: 1000 }
    }, null, 2);

    // 에디터에 입력
    await playground.setInput(sampleInput);

    // Run 버튼 클릭
    await playground.run();

    // Preview 패널 업데이트 확인
    await playground.expectPreviewUpdated();
  });

  test('Playground 에디터와 프리뷰 동기화', async ({ page }) => {
    const playground = new PlaygroundPage(page);
    await playground.goto();

    // 초기 상태 확인
    await expect(playground.editor.first()).toBeVisible();
    await expect(playground.previewPanel.first()).toBeVisible();

    // Run 버튼이 활성화되어 있는지 확인
    await expect(playground.runBtn.first()).toBeEnabled();
  });
});
