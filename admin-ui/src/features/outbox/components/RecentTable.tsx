import { motion } from 'framer-motion'
import { Eye } from 'lucide-react'
import type { OutboxItem } from '@/shared/types'
import { StatusBadge } from '@/shared/ui'

interface RecentTableProps {
  items: OutboxItem[]
  onViewDetail: (id: string) => void
}

export function RecentTable({ items, onViewDetail }: RecentTableProps) {
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
