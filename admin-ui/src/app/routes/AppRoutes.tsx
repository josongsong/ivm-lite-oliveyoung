import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { AnimatePresence } from 'framer-motion'
import { Layout } from '@/widgets/layout'
import { Dashboard } from '@/features/dashboard'
import { ContractDetail, Contracts } from '@/features/contracts'
import { Pipeline } from '@/features/pipeline'
import { Workflow } from '@/features/workflow'
import { Outbox } from '@/features/outbox'
import { Environment } from '@/features/environment'
import { Health } from '@/features/health'
import { Observability } from '@/features/observability'
import { Alerts } from '@/features/alerts'
import { Backfill } from '@/features/backfill'
import { TracesPage } from '@/features/traces'
import { DataExplorer } from '@/features/explorer'
import { PlaygroundPage } from '@/features/playground'
import { WebhooksPage } from '@/features/webhooks'
import {
  ComponentCategoryPage,
  ComponentDetail,
  DesignSystemPage,
  FeatureCategoryPage,
  FoundationsSection,
  PatternGuide,
  ResourcePage,
} from '@/features/design-system'

export function AppRoutes() {
  const location = useLocation()
  const isDesignSystem = location.pathname.startsWith('/design-system')

  // Design System은 자체 레이아웃 사용 (Layout 밖)
  if (isDesignSystem) {
    return (
      <AnimatePresence mode="wait">
        <Routes>
          <Route path="/design-system" element={<DesignSystemPage />}>
            <Route index element={<Navigate to="foundations/colors" replace />} />
            <Route path="foundations/:section" element={<FoundationsSection />} />
            <Route path="components/:category" element={<ComponentCategoryPage />} />
            <Route path="components/:category/:name" element={<ComponentDetail />} />
            <Route path="patterns" element={<PatternGuide />} />
            <Route path="patterns/:pattern" element={<PatternGuide />} />
            <Route path="features" element={<FeatureCategoryPage />} />
            <Route path="features/:feature" element={<FeatureCategoryPage />} />
            <Route path="features/:feature/:name" element={<FeatureCategoryPage />} />
            <Route path="resources/:resource" element={<ResourcePage />} />
          </Route>
        </Routes>
      </AnimatePresence>
    )
  }

  // 나머지 라우트 - Layout 사용
  return (
    <Layout>
      <AnimatePresence mode="wait">
        <Routes>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/contracts" element={<Contracts />} />
          <Route path="/contracts/:kind/:id" element={<ContractDetail />} />
          <Route path="/pipeline" element={<Pipeline />} />
          <Route path="/workflow" element={<Workflow />} />
          <Route path="/outbox" element={<Outbox />} />
          <Route path="/environment" element={<Environment />} />
          <Route path="/health" element={<Health />} />
          <Route path="/observability" element={<Observability />} />
          <Route path="/traces" element={<TracesPage />} />
          <Route path="/alerts" element={<Alerts />} />
          <Route path="/backfill" element={<Backfill />} />
          <Route path="/explorer" element={<DataExplorer />} />
          <Route path="/explorer/:entityId" element={<DataExplorer />} />
          <Route path="/playground" element={<PlaygroundPage />} />
          <Route path="/webhooks" element={<WebhooksPage />} />
        </Routes>
      </AnimatePresence>
    </Layout>
  )
}
