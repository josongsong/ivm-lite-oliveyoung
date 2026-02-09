import { motion } from 'framer-motion'
import type { PipelineStatus } from '@/shared/types'
import { StatusBadge } from '@/shared/ui'

interface StatusBannerProps {
  status: PipelineStatus
}

export function StatusBanner({ status }: StatusBannerProps) {
  return (
    <motion.div
      className="status-banner"
      initial={{ opacity: 0, y: -10 }}
      animate={{ opacity: 1, y: 0 }}
    >
      <StatusBadge status={status.health} />
      <span className="status-summary">{status.summary}</span>
      {status.issues.length > 0 && (
        <span className="status-issues">
          ({status.issues.length} issue{status.issues.length > 1 ? 's' : ''})
        </span>
      )}
    </motion.div>
  )
}
