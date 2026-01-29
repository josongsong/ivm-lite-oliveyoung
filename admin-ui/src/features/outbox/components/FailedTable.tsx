import { motion } from 'framer-motion'
import { Eye, Loader2, RotateCcw } from 'lucide-react'
import type { FailedOutboxItem } from '@/shared/types'

interface FailedTableProps {
  items: FailedOutboxItem[]
  onViewDetail: (id: string) => void
  onRetry: (id: string) => void
  retryingId: string | null
}

export function FailedTable({ items, onViewDetail, onRetry, retryingId }: FailedTableProps) {
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
