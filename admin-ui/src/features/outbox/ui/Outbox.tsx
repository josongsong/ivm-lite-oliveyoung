import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import {
  AlertTriangle,
  Clock,
  Eye,
  Inbox,
  Loader2,
  RefreshCw,
  RotateCcw,
  X,
  Zap
} from 'lucide-react'
import { fetchApi, postApi } from '@/shared/api'
import type { DlqResponse, OutboxEntryDto, OutboxItem, RecentOutboxResponse } from '@/shared/types'
import { formatAge, StatusBadge } from '@/shared/ui'
import './Outbox.css'

type TabType = 'recent' | 'failed' | 'dlq' | 'stale'

interface StaleResponse {
  items: { id: string; aggregateType: string; aggregateId: string; eventType: string; claimedAt: string | null; claimedBy: string | null; ageSeconds: number }[]
  count: number
  timeoutSeconds: number
}

interface FailedResponse {
  items: { id: string; aggregateType: string; aggregateId: string; eventType: string; createdAt: string | null; retryCount: number; failureReason: string | null }[]
  count: number
}

export function Outbox() {
  const [activeTab, setActiveTab] = useState<TabType>('recent')
  const [selectedEntry, setSelectedEntry] = useState<OutboxEntryDto | null>(null)
  const queryClient = useQueryClient()

  // 모든 탭 개수를 처음부터 로드 (탭 배지용)
  const { data: recentData, isLoading: loadingRecent } = useQuery({
    queryKey: ['outbox-recent'],
    queryFn: () => fetchApi<RecentOutboxResponse>('/outbox/recent?limit=50'),
  })

  const { data: failedData, isLoading: loadingFailed } = useQuery({
    queryKey: ['outbox-failed'],
    queryFn: () => fetchApi<FailedResponse>('/outbox/failed?limit=50'),
  })

  const { data: dlqData, isLoading: loadingDlq } = useQuery({
    queryKey: ['outbox-dlq'],
    queryFn: () => fetchApi<DlqResponse>('/outbox/dlq?limit=50'),
  })

  const { data: staleData, isLoading: loadingStale } = useQuery({
    queryKey: ['outbox-stale'],
    queryFn: () => fetchApi<StaleResponse>('/outbox/stale?timeout=300'),
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
    mutationFn: () => postApi('/outbox/failed/retry-all?limit=100'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['outbox-failed'] })
      queryClient.invalidateQueries({ queryKey: ['outbox-recent'] })
    },
  })

  const releaseStale = useMutation({
    mutationFn: () => postApi('/outbox/stale/release?timeout=300'),
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
      console.error('Failed to fetch entry detail:', error)
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

function RecentTable({ items, onViewDetail }: { items: OutboxItem[]; onViewDetail: (id: string) => void }) {
  return (
    <div className="table-container">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Type</th>
            <th>Aggregate ID</th>
            <th>Event</th>
            <th>Status</th>
            <th>Created</th>
            <th>Processed</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <motion.tr 
              key={item.id}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
            >
              <td className="mono">{item.id.slice(0, 8)}...</td>
              <td>{item.aggregateType}</td>
              <td className="mono truncate" style={{ maxWidth: '200px' }}>{item.aggregateId}</td>
              <td>{item.eventType}</td>
              <td>
                <StatusBadge status={item.status} />
              </td>
              <td className="text-secondary">
                {item.createdAt ? new Date(item.createdAt).toLocaleString('ko-KR') : '-'}
              </td>
              <td className="text-secondary">
                {item.processedAt ? new Date(item.processedAt).toLocaleString('ko-KR') : '-'}
              </td>
              <td>
                <button className="btn-icon" onClick={() => onViewDetail(item.id)} title="View Detail">
                  <Eye size={16} />
                </button>
              </td>
            </motion.tr>
          ))}
          {items.length === 0 && (
            <tr>
              <td colSpan={8} className="empty-cell">데이터가 없습니다</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}

function FailedTable({ items, onViewDetail, onRetry, retryingId }: {
  items: { id: string; aggregateType: string; aggregateId: string; eventType: string; createdAt: string | null; retryCount: number; failureReason: string | null }[]
  onViewDetail: (id: string) => void
  onRetry: (id: string) => void
  retryingId: string | null
}) {
  return (
    <div className="table-container">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Type</th>
            <th>Aggregate ID</th>
            <th>Event</th>
            <th>Retries</th>
            <th>Failure Reason</th>
            <th>Created</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <motion.tr
              key={item.id}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
            >
              <td className="mono">{item.id.slice(0, 8)}...</td>
              <td>{item.aggregateType}</td>
              <td className="mono truncate" style={{ maxWidth: '150px' }}>{item.aggregateId}</td>
              <td>{item.eventType}</td>
              <td className="text-orange">{item.retryCount}</td>
              <td className="truncate text-error" style={{ maxWidth: '200px' }} title={item.failureReason ?? ''}>
                {item.failureReason ?? '-'}
              </td>
              <td className="text-secondary">
                {item.createdAt ? new Date(item.createdAt).toLocaleString('ko-KR') : '-'}
              </td>
              <td>
                <div className="action-buttons">
                  {/* GAP-1: 재시도 버튼 */}
                  <button
                    className="btn-icon retry"
                    onClick={() => onRetry(item.id)}
                    disabled={retryingId === item.id}
                    title="Retry"
                  >
                    {retryingId === item.id ? (
                      <Loader2 size={16} className="spin" />
                    ) : (
                      <RotateCcw size={16} />
                    )}
                  </button>
                  <button className="btn-icon" onClick={() => onViewDetail(item.id)} title="View Detail">
                    <Eye size={16} />
                  </button>
                </div>
              </td>
            </motion.tr>
          ))}
          {items.length === 0 && (
            <tr>
              <td colSpan={8} className="empty-cell">실패한 엔트리가 없습니다</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}

function DlqTable({ items, onReplay, replayingId, onViewDetail }: { 
  items: OutboxEntryDto[]
  onReplay: (id: string) => void
  replayingId: string | null
  onViewDetail: (id: string) => void
}) {
  return (
    <div className="table-container">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Type</th>
            <th>Aggregate ID</th>
            <th>Event</th>
            <th>Retries</th>
            <th>Failure Reason</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <motion.tr 
              key={item.id}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
            >
              <td className="mono">{item.id.slice(0, 8)}...</td>
              <td>{item.aggregateType}</td>
              <td className="mono truncate" style={{ maxWidth: '150px' }}>{item.aggregateId}</td>
              <td>{item.eventType}</td>
              <td className="text-orange">{item.retryCount}</td>
              <td className="truncate text-error" style={{ maxWidth: '200px' }} title={item.failureReason ?? ''}>
                {item.failureReason ?? '-'}
              </td>
              <td>
                <div className="action-buttons">
                  <button 
                    className="btn-icon replay"
                    onClick={() => onReplay(item.id)}
                    disabled={replayingId === item.id}
                    title="Replay"
                  >
                    {replayingId === item.id ? (
                      <Loader2 size={16} className="spin" />
                    ) : (
                      <RotateCcw size={16} />
                    )}
                  </button>
                  <button className="btn-icon" onClick={() => onViewDetail(item.id)} title="View Detail">
                    <Eye size={16} />
                  </button>
                </div>
              </td>
            </motion.tr>
          ))}
          {items.length === 0 && (
            <tr>
              <td colSpan={7} className="empty-cell">DLQ가 비어있습니다 👍</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}

function StaleTable({ items, timeoutSeconds }: { 
  items: { id: string; aggregateType: string; aggregateId: string; eventType: string; claimedAt: string | null; claimedBy: string | null; ageSeconds: number }[]
  timeoutSeconds: number
}) {
  return (
    <div className="table-container">
      <div className="table-info">
        <AlertTriangle size={16} className="text-warning" />
        <span>Timeout: {timeoutSeconds}초 이상 PROCESSING 상태인 엔트리</span>
      </div>
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Type</th>
            <th>Aggregate ID</th>
            <th>Event</th>
            <th>Claimed At</th>
            <th>Claimed By</th>
            <th>Age</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <motion.tr 
              key={item.id}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
            >
              <td className="mono">{item.id.slice(0, 8)}...</td>
              <td>{item.aggregateType}</td>
              <td className="mono truncate" style={{ maxWidth: '150px' }}>{item.aggregateId}</td>
              <td>{item.eventType}</td>
              <td className="text-secondary">
                {item.claimedAt ? new Date(item.claimedAt).toLocaleString('ko-KR') : '-'}
              </td>
              <td className="mono">{item.claimedBy ?? '-'}</td>
              <td className="text-warning">
                {formatAge(item.ageSeconds)}
              </td>
            </motion.tr>
          ))}
          {items.length === 0 && (
            <tr>
              <td colSpan={7} className="empty-cell">Stale 엔트리가 없습니다 👍</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}

