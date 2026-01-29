import { motion } from 'framer-motion'
import { Eye, Loader2, RotateCcw } from 'lucide-react'
import type { OutboxEntryDto } from '@/shared/types'

interface DlqTableProps {
  items: OutboxEntryDto[]
  onReplay: (id: string) => void
  replayingId: string | null
  onViewDetail: (id: string) => void
}

export function DlqTable({ items, onReplay, replayingId, onViewDetail }: DlqTableProps) {
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
              <td colSpan={7} className="empty-cell">DLQ가 비어있습니다</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}
