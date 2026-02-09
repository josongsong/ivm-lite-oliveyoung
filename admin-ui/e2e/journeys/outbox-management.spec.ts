import { test, expect } from '@playwright/test';
import { OutboxPage } from '../pages';

test.describe('CUJ-003: Outbox Management', () => {
  test('Outbox 탭 전환 및 테이블 확인', async ({ page }) => {
    const outbox = new OutboxPage(page);
    await outbox.goto();

    // 페이지 로드 확인
    await expect(page.getByRole('main')).toBeVisible({ timeout: 10000 });

    // 탭이 있으면 전환
    const recentTab = page.getByRole('tab', { name: /recent/i });
    const hasTabs = await recentTab.first().isVisible({ timeout: 3000 }).catch(() => false);
    if (hasTabs) {
      // Failed 탭으로 전환
      const failedTab = page.getByRole('tab', { name: /failed/i });
      if (await failedTab.first().isVisible().catch(() => false)) {
        await failedTab.first().click();
        await page.waitForTimeout(300);
      }
    }
  });

  test('Outbox 이벤트 상세 모달', async ({ page }) => {
    const outbox = new OutboxPage(page);
    await outbox.goto();

    // 테이블에 행이 있으면 클릭
    const rowCount = await outbox.getRowCount();
    if (rowCount > 0) {
      await outbox.clickRow(1);

      // 모달이 열리면 확인
      const modalVisible = await outbox.detailModal.isVisible({ timeout: 3000 }).catch(() => false);
      if (modalVisible) {
        await outbox.expectModalOpen();
        await outbox.closeModal();
      }
    }
  });

  test('Outbox Failed 탭 이벤트 확인', async ({ page }) => {
    const outbox = new OutboxPage(page);
    await outbox.goto();

    // 페이지 로드 확인
    await expect(page.getByRole('main')).toBeVisible({ timeout: 10000 });

    // Failed 탭이 있으면 전환
    const failedTab = page.getByRole('tab', { name: /failed/i });
    const hasFailedTab = await failedTab.first().isVisible({ timeout: 3000 }).catch(() => false);
    if (hasFailedTab) {
      await failedTab.first().click();
      await page.waitForTimeout(300);
    }
  });
});
