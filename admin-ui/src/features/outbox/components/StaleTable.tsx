import { motion } from 'framer-motion'
import { AlertTriangle } from 'lucide-react'
import type { StaleOutboxItem } from '@/shared/types'
import { formatAge } from '@/shared/ui'

interface StaleTableProps {
  items: StaleOutboxItem[]
  timeoutSeconds: number
}

export function StaleTable({ items, timeoutSeconds }: StaleTableProps) {
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
              <td colSpan={7} className="empty-cell">Stale 엔트리가 없습니다</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}
