/**
 * OutboxTabContent - 탭 콘텐츠 렌더링 컴포넌트
 */
import { AnimatePresence, motion } from 'framer-motion'
import type {
  DlqResponse,
  FailedResponse,
  RecentOutboxResponse,
  StaleResponse,
} from '@/shared/types'
import { Loading, uniqueBy } from '@/shared/ui'
import { DlqTable } from './DlqTable'
import { FailedTable } from './FailedTable'
import { RecentTable } from './RecentTable'
import { StaleTable } from './StaleTable'

type TabType = 'recent' | 'failed' | 'dlq' | 'stale'

interface OutboxTabContentProps {
  activeTab: TabType
  isLoading: boolean
  recentData: RecentOutboxResponse | undefined
  failedData: FailedResponse | undefined
  dlqData: DlqResponse | undefined
  staleData: StaleResponse | undefined
  onViewDetail: (id: string) => void
  onProcess: (id: string) => void
  onRetry: (id: string) => void
  onReplay: (id: string) => void
  processingId: string | null
  retryingId: string | null
  replayingId: string | null
}

export function OutboxTabContent({
  activeTab,
  isLoading,
  recentData,
  failedData,
  dlqData,
  staleData,
  onViewDetail,
  onProcess,
  onRetry,
  onReplay,
  processingId,
  retryingId,
  replayingId,
}: OutboxTabContentProps) {
  if (isLoading) {
    return <Loading size="lg" fullPage={false} />
  }

  return (
    <AnimatePresence mode="wait">
      {activeTab === 'recent' && (
        <motion.div key="recent" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
          <RecentTable
            items={uniqueBy(recentData?.items ?? [], (i) => i.id)}
            onViewDetail={onViewDetail}
            onProcess={onProcess}
            processingId={processingId}
          />
        </motion.div>
      )}
      {activeTab === 'failed' && (
        <motion.div key="failed" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
          <FailedTable
            items={uniqueBy(failedData?.items ?? [], (i) => i.id)}
            onViewDetail={onViewDetail}
            onRetry={onRetry}
            retryingId={retryingId}
          />
        </motion.div>
      )}
      {activeTab === 'dlq' && (
        <motion.div key="dlq" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
          <DlqTable
            items={uniqueBy(dlqData?.items ?? [], (i) => i.id)}
            onReplay={onReplay}
            replayingId={replayingId ?? null}
            onViewDetail={onViewDetail}
          />
        </motion.div>
      )}
      {activeTab === 'stale' && (
        <motion.div key="stale" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
          <StaleTable
            items={uniqueBy(staleData?.items ?? [], (i) => i.id)}
            timeoutSeconds={staleData?.timeoutSeconds ?? 300}
          />
        </motion.div>
      )}
    </AnimatePresence>
  )
}
