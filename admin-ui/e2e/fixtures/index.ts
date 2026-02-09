import { test as base, Page } from '@playwright/test';
import {
  DashboardPage,
  ContractsPage,
  ExplorerPage,
  OutboxPage,
  PlaygroundPage,
  WorkflowPage,
  TracesPage,
} from '../pages';

// Page Object 타입
type PageObjects = {
  dashboardPage: DashboardPage;
  contractsPage: ContractsPage;
  explorerPage: ExplorerPage;
  outboxPage: OutboxPage;
  playgroundPage: PlaygroundPage;
  workflowPage: WorkflowPage;
  tracesPage: TracesPage;
};

// 확장된 테스트 타입
export const test = base.extend<PageObjects>({
  dashboardPage: async ({ page }, use) => {
    const dashboardPage = new DashboardPage(page);
    await use(dashboardPage);
  },

  contractsPage: async ({ page }, use) => {
    const contractsPage = new ContractsPage(page);
    await use(contractsPage);
  },

  explorerPage: async ({ page }, use) => {
    const explorerPage = new ExplorerPage(page);
    await use(explorerPage);
  },

  outboxPage: async ({ page }, use) => {
    const outboxPage = new OutboxPage(page);
    await use(outboxPage);
  },

  playgroundPage: async ({ page }, use) => {
    const playgroundPage = new PlaygroundPage(page);
    await use(playgroundPage);
  },

  workflowPage: async ({ page }, use) => {
    const workflowPage = new WorkflowPage(page);
    await use(workflowPage);
  },

  tracesPage: async ({ page }, use) => {
    const tracesPage = new TracesPage(page);
    await use(tracesPage);
  },
});

export { expect } from '@playwright/test';
