import { AnimatePresence, motion } from 'framer-motion'
import { X } from 'lucide-react'
import type { OutboxEntryDto } from '@/shared/types'
import { IconButton } from '@/shared/ui'

interface OutboxDetailModalProps {
  entry: OutboxEntryDto | null
  isLoading: boolean
  onClose: () => void
}

export function OutboxDetailModal({ entry, isLoading, onClose }: OutboxDetailModalProps) {
  const isOpen = entry !== null || isLoading

  if (!isOpen) return null

  return (
    <AnimatePresence>
      <motion.div
        className="modal-overlay"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        onClick={onClose}
        role="presentation"
      >
        <motion.div
          className="modal-content"
          initial={{ scale: 0.9, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          exit={{ scale: 0.9, opacity: 0 }}
          onClick={(e) => e.stopPropagation()}
          role="dialog"
          aria-modal="true"
          aria-labelledby="outbox-modal-title"
        >
          <div className="modal-header">
            <h2 id="outbox-modal-title">Outbox Entry Detail</h2>
            <IconButton
              icon={X}
              size="md"
              variant="ghost"
              className="modal-close"
              onClick={onClose}
              tooltip="모달 닫기"
            />
          </div>
          {isLoading ? (
            <div className="modal-body">
              <div className="loading"><div className="loading-spinner" /></div>
            </div>
          ) : entry ? (
            <div className="modal-body">
              <div className="detail-grid">
                <DetailItem label="ID" value={entry.id} mono />
                <DetailItem label="Status">
                  <span className={`badge badge-${entry.status.toLowerCase()}`}>
                    {entry.status}
                  </span>
                </DetailItem>
                <DetailItem label="Aggregate Type" value={entry.aggregateType} />
                <DetailItem label="Aggregate ID" value={entry.aggregateId} mono />
                <DetailItem label="Event Type" value={entry.eventType} />
                <DetailItem label="Retry Count" value={String(entry.retryCount)} />
                <DetailItem
                  label="Created At"
                  value={new Date(entry.createdAt).toLocaleString('ko-KR')}
                />
                <DetailItem
                  label="Processed At"
                  value={entry.processedAt ? new Date(entry.processedAt).toLocaleString('ko-KR') : '-'}
                />
              </div>
              {entry.failureReason ? (
                <div className="detail-section">
                  <h3>Failure Reason</h3>
                  <pre className="failure-reason">{entry.failureReason}</pre>
                </div>
              ) : null}
              <div className="detail-section">
                <h3>Payload</h3>
                <pre className="payload-content">
                  {formatPayload(entry.payload)}
                </pre>
              </div>
            </div>
          ) : null}
        </motion.div>
      </motion.div>
    </AnimatePresence>
  )
}

function DetailItem({
  label,
  value,
  mono,
  children,
}: {
  label: string
  value?: string
  mono?: boolean
  children?: React.ReactNode
}) {
  return (
    <div className="detail-item">
      <span className="detail-label">{label}</span>
      {children ?? <span className={`detail-value${mono ? ' mono' : ''}`}>{value}</span>}
    </div>
  )
}

function formatPayload(payload?: string): string {
  if (!payload) return '{}'
  try {
    return JSON.stringify(JSON.parse(payload), null, 2)
  } catch {
    return `// JSON 파싱 실패\n${payload}`
  }
}
