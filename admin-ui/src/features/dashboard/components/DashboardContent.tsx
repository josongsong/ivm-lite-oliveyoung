/**
 * DashboardContent Component
 *
 * Dashboard 메인 콘텐츠 영역
 */
import { motion } from 'framer-motion'
import type { DashboardResponse, PipelineOverviewResponse } from '@/shared/types'
import { PageHeader } from '@/shared/ui'
import { ActionsPanel } from './ActionsPanel'
import { OutboxPanel } from './OutboxPanel'
import { SliceTypesSection } from './SliceTypesSection'
import { SummaryRow } from './SummaryRow'

interface DashboardContentProps {
  dashboard: DashboardResponse
  pipeline?: PipelineOverviewResponse
}

export function DashboardContent({ dashboard, pipeline }: DashboardContentProps) {
  const outbox = dashboard.outbox?.total ?? { pending: 0, processing: 0, failed: 0, processed: 0 }
  const worker = dashboard.worker ?? { running: false, processed: 0, failed: 0, polls: 0 }
  const rawDataCount = dashboard.database?.rawDataCount ?? 0
  const sliceCount = pipeline?.slices?.total ?? 0
  const contractCount = dashboard.database?.contractsCount ?? 0

  return (
    <div className="page-container">
      <PageHeader title="Dashboard" subtitle="IVM Lite 시스템 상태 실시간 모니터링" />

      <SummaryRow
        worker={worker}
        rawDataCount={rawDataCount}
        sliceCount={sliceCount}
        contractCount={contractCount}
      />

      <div className="dashboard-grid">
        <OutboxPanel outbox={outbox} />
        <ActionsPanel worker={worker} />
      </div>

      <SliceTypesSection slicesByType={pipeline?.slices?.byType ?? {}} />

      <motion.div
        className="timestamp"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.5 }}
      >
        {dashboard.timestamp ? new Date(dashboard.timestamp).toLocaleString('ko-KR') : '-'}
      </motion.div>
    </div>
  )
}
