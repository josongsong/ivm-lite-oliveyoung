import { motion } from 'framer-motion'
import type { QueueDepthMetrics } from '@/shared/types'

interface QueueStatusSectionProps {
  queueDepths: QueueDepthMetrics | undefined
}

function QueueBar({ label, value, total, color }: { label: string; value: number; total: number; color: string }) {
  const percent = total > 0 ? (value / total) * 100 : 0

  return (
    <div className="queue-bar-item">
      <div className="queue-bar-header">
        <span className="queue-bar-label">{label}</span>
        <span className="queue-bar-value mono">{value.toLocaleString()} ({percent.toFixed(1)}%)</span>
      </div>
      <div className="queue-bar-track">
        <motion.div
          className="queue-bar-fill"
          style={{ '--bar-color': color } as React.CSSProperties}
          initial={{ width: 0 }}
          animate={{ width: `${Math.min(percent, 100)}%` }}
          transition={{ duration: 0.5, delay: 0.2 }}
        />
      </div>
    </div>
  )
}

export function QueueStatusSection({ queueDepths }: QueueStatusSectionProps) {
  const totalQueue = queueDepths
    ? queueDepths.pending + queueDepths.processing + queueDepths.failed + queueDepths.dlq
    : 1

  return (
    <motion.div
      className="obs-section"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.4 }}
    >
      <h2 className="section-title">Queue Status</h2>
      <div className="queue-bars">
        <QueueBar
          label="Pending"
          value={queueDepths?.pending ?? 0}
          total={totalQueue}
          color="var(--status-pending)"
        />
        <QueueBar
          label="Processing"
          value={queueDepths?.processing ?? 0}
          total={totalQueue}
          color="var(--status-processing)"
        />
        <QueueBar
          label="Failed"
          value={queueDepths?.failed ?? 0}
          total={totalQueue}
          color="var(--status-error)"
        />
        <QueueBar
          label="DLQ"
          value={queueDepths?.dlq ?? 0}
          total={totalQueue}
          color="var(--accent-magenta)"
        />
        <QueueBar
          label="Stale"
          value={queueDepths?.stale ?? 0}
          total={totalQueue}
          color="var(--accent-orange)"
        />
      </div>
    </motion.div>
  )
}
