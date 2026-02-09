import { test, expect } from '@playwright/test';
import { TracesPage, DashboardPage } from '../pages';

test.describe('CUJ-006: Traces Analysis', () => {
  test('Dashboard → Traces 페이지 이동', async ({ page }) => {
    const dashboard = new DashboardPage(page);
    await dashboard.goto();
    await dashboard.expectStatsVisible();

    // Traces 페이지로 이동
    await dashboard.navigateTo('traces');

    const traces = new TracesPage(page);
    await traces.expectPageLoaded();
  });

  test('Traces 페이지 로드 및 UI 확인', async ({ page }) => {
    const traces = new TracesPage(page);
    await traces.goto();
    await traces.expectPageLoaded();

    // 필터 영역 또는 메인 콘텐츠 확인
    const hasFilter = await traces.filterSection.first().isVisible().catch(() => false);
    if (hasFilter) {
      // 시간 범위 필터 있으면 적용
      if (await traces.timeRangeFilter.first().isVisible().catch(() => false)) {
        await traces.selectTimeRange('1h');
      }
    }
  });

  test('Traces 목록에서 상세 조회', async ({ page }) => {
    const traces = new TracesPage(page);
    await traces.goto();
    await traces.expectPageLoaded();

    // 트레이스 아이템이 있으면 클릭
    const hasTraces = await traces.hasTraceItems();
    if (hasTraces) {
      await traces.clickTrace(0);
      await traces.expectDetailPanelVisible();
    }
  });

  test('Traces 워터폴 타임라인 확인', async ({ page }) => {
    const traces = new TracesPage(page);
    await traces.goto();
    await traces.expectPageLoaded();

    // 트레이스 선택 후 워터폴 확인
    const hasTraces = await traces.hasTraceItems();
    if (hasTraces) {
      await traces.clickTrace(0);
      await traces.expectWaterfallVisible();
    }
  });

  test('Traces 스팬 상세 정보 확인', async ({ page }) => {
    const traces = new TracesPage(page);
    await traces.goto();
    await traces.expectPageLoaded();

    const hasTraces = await traces.hasTraceItems();
    if (hasTraces) {
      await traces.clickTrace(0);
      await traces.expectDetailPanelVisible();

      // 스팬 클릭하여 상세 정보 확인
      const hasSpans = await traces.hasSpans();
      if (hasSpans) {
        await traces.clickSpan(0);
        await traces.expectSpanDetailsVisible();
      }
    }
  });
});
