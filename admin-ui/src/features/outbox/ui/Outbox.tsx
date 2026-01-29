import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import {
  AlertTriangle,
  Clock,
  Inbox,
  Loader2,
  RefreshCw,
  RotateCcw,
  X,
  Zap
} from 'lucide-react'
import { fetchApi, postApi } from '@/shared/api'
import { OUTBOX_CONFIG } from '@/shared/config'
import type {
  DlqResponse,
  FailedResponse,
  OutboxEntryDto,
  RecentOutboxResponse,
  StaleResponse,
} from '@/shared/types'
import { toast } from '@/shared/ui'
import { DlqTable, FailedTable, RecentTable, StaleTable } from '../components'
import './Outbox.css'

type TabType = 'recent' | 'failed' | 'dlq' | 'stale'

export function Outbox() {
  const [activeTab, setActiveTab] = useState<TabType>('recent')
  const [selectedEntry, setSelectedEntry] = useState<OutboxEntryDto | null>(null)
  const queryClient = useQueryClient()

  const { DEFAULT_LIMIT, BATCH_RETRY_LIMIT, STALE_TIMEOUT_SECONDS } = OUTBOX_CONFIG

  // 모든 탭 개수를 처음부터 로드 (탭 배지용)
  const { data: recentData, isLoading: loadingRecent } = useQuery({
    queryKey: ['outbox-recent'],
    queryFn: () => fetchApi<RecentOutboxResponse>(`/outbox/recent?limit=${DEFAULT_LIMIT}`),
  })

  const { data: failedData, isLoading: loadingFailed } = useQuery({
    queryKey: ['outbox-failed'],
    queryFn: () => fetchApi<FailedResponse>(`/outbox/failed?limit=${DEFAULT_LIMIT}`),
  })

  const { data: dlqData, isLoading: loadingDlq } = useQuery({
    queryKey: ['outbox-dlq'],
    queryFn: () => fetchApi<DlqResponse>(`/outbox/dlq?limit=${DEFAULT_LIMIT}`),
  })

  const { data: staleData, isLoading: loadingStale } = useQuery({
    queryKey: ['outbox-stale'],
    queryFn: () => fetchApi<StaleResponse>(`/outbox/stale?timeout=${STALE_TIMEOUT_SECONDS}`),
  })

  const replayMutation = useMutation({
    mutationFn: (id: string) => postApi(`/outbox/dlq/${id}/replay`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['outbox-dlq'] })
      queryClient.invalidateQueries({ queryKey: ['outbox-recent'] })
    },
  })

  // GAP-1: 개별 실패 작업 재시도
  const retryMutation = useMutation({
    mutationFn: (id: string) => postApi(`/outbox/${id}/retry`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['outbox-failed'] })
      queryClient.invalidateQueries({ queryKey: ['outbox-recent'] })
    },
  })

  // GAP-1: 일괄 실패 작업 재시도
  const retryAllMutation = useMutation({
    mutationFn: () => postApi(`/outbox/failed/retry-all?limit=${BATCH_RETRY_LIMIT}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['outbox-failed'] })
      queryClient.invalidateQueries({ queryKey: ['outbox-recent'] })
    },
  })

  const releaseStale = useMutation({
    mutationFn: () => postApi(`/outbox/stale/release?timeout=${STALE_TIMEOUT_SECONDS}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['outbox-stale'] })
      queryClient.invalidateQueries({ queryKey: ['outbox-recent'] })
    },
  })

  const handleViewDetail = async (id: string) => {
    try {
      const entry = await fetchApi<OutboxEntryDto>(`/outbox/${id}`)
      setSelectedEntry(entry)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to fetch entry detail'
      toast.error(message)
    }
  }

  const tabs = [
    { key: 'recent' as TabType, label: 'Recent', icon: Clock, count: recentData?.count },
    { key: 'failed' as TabType, label: 'Failed', icon: AlertTriangle, count: failedData?.count },
    { key: 'dlq' as TabType, label: 'DLQ', icon: Inbox, count: dlqData?.count },
    { key: 'stale' as TabType, label: 'Stale', icon: Loader2, count: staleData?.count },
  ]

  const isLoading = loadingRecent || loadingFailed || loadingDlq || loadingStale

  return (
    <div className="page-container">
      <div className="page-header">
        <motion.h1 
          className="page-title"
          initial={{ opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
        >
          Outbox
        </motion.h1>
        <motion.p 
          className="page-subtitle"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.1 }}
        >
          메시지 처리 상태를 모니터링하고 DLQ, 재처리를 관리합니다
        </motion.p>
      </div>

      {/* Tabs */}
      <motion.div 
        className="outbox-tabs"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
      >
        {tabs.map((tab) => {
          const Icon = tab.icon
          return (
            <button
              key={tab.key}
              className={`outbox-tab ${activeTab === tab.key ? 'active' : ''}`}
              onClick={() => setActiveTab(tab.key)}
            >
              <Icon size={18} />
              <span>{tab.label}</span>
              {tab.count !== undefined && (
                <span className="tab-count">{tab.count}</span>
              )}
            </button>
          )
        })}
      </motion.div>

      {/* Actions Bar */}
      <motion.div
        className="actions-bar"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.1 }}
      >
        {/* GAP-1: Failed 탭 일괄 재시도 버튼 */}
        {activeTab === 'failed' && failedData && failedData.items.length > 0 && (
          <button
            className="btn btn-primary"
            onClick={() => retryAllMutation.mutate()}
            disabled={retryAllMutation.isPending}
          >
            {retryAllMutation.isPending ? (
              <Loader2 size={16} className="spin" />
            ) : (
              <RotateCcw size={16} />
            )}
            Retry All ({failedData.items.length})
          </button>
        )}
        {activeTab === 'stale' && staleData && staleData.items.length > 0 && (
          <button
            className="btn btn-primary"
            onClick={() => releaseStale.mutate()}
            disabled={releaseStale.isPending}
          >
            {releaseStale.isPending ? (
              <Loader2 size={16} className="spin" />
            ) : (
              <Zap size={16} />
            )}
            Release All Stale ({staleData.items.length})
          </button>
        )}
        <button
          className="btn btn-secondary"
          onClick={() => queryClient.invalidateQueries()}
        >
          <RefreshCw size={16} />
          새로고침
        </button>
      </motion.div>

      {/* Content */}
      <motion.div 
        className="outbox-content"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2 }}
      >
        {isLoading ? (
          <div className="loading">
            <div className="loading-spinner" />
          </div>
        ) : (
          <AnimatePresence mode="wait">
            {activeTab === 'recent' && (
              <motion.div
                key="recent"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
              >
                <RecentTable 
                  items={recentData?.items ?? []} 
                  onViewDetail={handleViewDetail}
                />
              </motion.div>
            )}

            {activeTab === 'failed' && (
              <motion.div
                key="failed"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
              >
                <FailedTable
                  items={failedData?.items ?? []}
                  onViewDetail={handleViewDetail}
                  onRetry={(id) => retryMutation.mutate(id)}
                  retryingId={retryMutation.isPending ? retryMutation.variables ?? null : null}
                />
              </motion.div>
            )}

            {activeTab === 'dlq' && (
              <motion.div
                key="dlq"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
              >
                <DlqTable 
                  items={dlqData?.items ?? []}
                  onReplay={(id) => replayMutation.mutate(id)}
                  replayingId={replayMutation.isPending ? replayMutation.variables : null}
                  onViewDetail={handleViewDetail}
                />
              </motion.div>
            )}

            {activeTab === 'stale' && (
              <motion.div
                key="stale"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
              >
                <StaleTable 
                  items={staleData?.items ?? []}
                  timeoutSeconds={staleData?.timeoutSeconds ?? 300}
                />
              </motion.div>
            )}
          </AnimatePresence>
        )}
      </motion.div>

      {/* Detail Modal */}
      <AnimatePresence>
        {selectedEntry && (
          <motion.div 
            className="modal-overlay"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => setSelectedEntry(null)}
          >
            <motion.div 
              className="modal-content"
              initial={{ scale: 0.9, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.9, opacity: 0 }}
              onClick={(e) => e.stopPropagation()}
            >
              <div className="modal-header">
                <h2>Outbox Entry Detail</h2>
                <button className="modal-close" onClick={() => setSelectedEntry(null)}>
                  <X size={20} />
                </button>
              </div>
              <div className="modal-body">
                <div className="detail-grid">
                  <div className="detail-item">
                    <span className="detail-label">ID</span>
                    <span className="detail-value mono">{selectedEntry.id}</span>
                  </div>
                  <div className="detail-item">
                    <span className="detail-label">Status</span>
                    <span className={`badge badge-${selectedEntry.status.toLowerCase()}`}>
                      {selectedEntry.status}
                    </span>
                  </div>
                  <div className="detail-item">
                    <span className="detail-label">Aggregate Type</span>
                    <span className="detail-value">{selectedEntry.aggregateType}</span>
                  </div>
                  <div className="detail-item">
                    <span className="detail-label">Aggregate ID</span>
                    <span className="detail-value mono">{selectedEntry.aggregateId}</span>
                  </div>
                  <div className="detail-item">
                    <span className="detail-label">Event Type</span>
                    <span className="detail-value">{selectedEntry.eventType}</span>
                  </div>
                  <div className="detail-item">
                    <span className="detail-label">Retry Count</span>
                    <span className="detail-value">{selectedEntry.retryCount}</span>
                  </div>
                  <div className="detail-item">
                    <span className="detail-label">Created At</span>
                    <span className="detail-value">{new Date(selectedEntry.createdAt).toLocaleString('ko-KR')}</span>
                  </div>
                  <div className="detail-item">
                    <span className="detail-label">Processed At</span>
                    <span className="detail-value">
                      {selectedEntry.processedAt ? new Date(selectedEntry.processedAt).toLocaleString('ko-KR') : '-'}
                    </span>
                  </div>
                </div>
                {selectedEntry.failureReason && (
                  <div className="detail-section">
                    <h3>Failure Reason</h3>
                    <pre className="failure-reason">{selectedEntry.failureReason}</pre>
                  </div>
                )}
                <div className="detail-section">
                  <h3>Payload</h3>
                  <pre className="payload-content">
                    {JSON.stringify(JSON.parse(selectedEntry.payload || '{}'), null, 2)}
                  </pre>
                </div>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
