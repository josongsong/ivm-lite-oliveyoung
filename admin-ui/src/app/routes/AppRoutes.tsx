import { Navigate, Route, Routes } from 'react-router-dom'
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

export function AppRoutes() {
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
