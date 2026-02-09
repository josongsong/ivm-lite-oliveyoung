/**
 * OutboxActions - 탭별 액션 버튼 컴포넌트
 */
import { RefreshCw, RotateCcw, Zap } from 'lucide-react'
import { Button } from '@/shared/ui'

type TabType = 'recent' | 'failed' | 'dlq' | 'stale'

interface OutboxActionsProps {
  activeTab: TabType
  failedCount: number
  staleCount: number
  onRetryAll: () => void
  onReleaseStale: () => void
  onRefresh: () => void
  isRetryingAll: boolean
  isReleasingStale: boolean
}

export function OutboxActions({
  activeTab,
  failedCount,
  staleCount,
  onRetryAll,
  onReleaseStale,
  onRefresh,
  isRetryingAll,
  isReleasingStale,
}: OutboxActionsProps) {
  return (
    <div className="outbox-actions">
      {activeTab === 'failed' && failedCount > 0 ? (
        <Button
          variant="primary"
          size="sm"
          onClick={onRetryAll}
          loading={isRetryingAll}
          icon={<RotateCcw size={14} />}
        >
          Retry All ({failedCount})
        </Button>
      ) : null}
      {activeTab === 'stale' && staleCount > 0 ? (
        <Button
          variant="primary"
          size="sm"
          onClick={onReleaseStale}
          loading={isReleasingStale}
          icon={<Zap size={14} />}
        >
          Release All ({staleCount})
        </Button>
      ) : null}
      <Button variant="ghost" size="sm" onClick={onRefresh} icon={<RefreshCw size={14} />}>
        새로고침
      </Button>
    </div>
  )
}
