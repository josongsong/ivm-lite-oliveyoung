import { motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import { AlertTriangle, ArrowRight, CheckCircle2, Clock, Inbox, Loader2 } from 'lucide-react'
import { fadeInUp, staggerContainer } from '@/shared/ui'

interface OutboxPanelProps {
  outbox: {
    pending: number
    processing: number
    failed: number
    processed: number
  }
}

export function OutboxPanel({ outbox }: OutboxPanelProps) {
  return (
    <motion.div
      className="outbox-panel"
      variants={staggerContainer}
      initial="hidden"
      animate="show"
    >
      <div className="panel-header">
        <Inbox size={18} />
        <h3>Outbox Queue</h3>
        <Link to="/outbox" className="view-all">
          상세보기 <ArrowRight size={14} />
        </Link>
      </div>

      <div className="outbox-stats">
        <motion.div variants={fadeInUp} className="outbox-stat pending">
          <Clock size={20} />
          <div className="stat-info">
            <span className="stat-value">{outbox.pending.toLocaleString()}</span>
            <span className="stat-label">Pending</span>
          </div>
          <div className="stat-bar" style={{ '--progress': `${Math.min(outbox.pending / 100, 1) * 100}%` } as React.CSSProperties} />
        </motion.div>

        <motion.div variants={fadeInUp} className="outbox-stat processing">
          <Loader2 size={20} className="spin" />
          <div className="stat-info">
            <span className="stat-value">{outbox.processing.toLocaleString()}</span>
            <span className="stat-label">Processing</span>
          </div>
          <div className="stat-bar" style={{ '--progress': `${Math.min(outbox.processing / 50, 1) * 100}%` } as React.CSSProperties} />
        </motion.div>

        <motion.div variants={fadeInUp} className="outbox-stat failed">
          <AlertTriangle size={20} />
          <div className="stat-info">
            <span className="stat-value">{outbox.failed.toLocaleString()}</span>
            <span className="stat-label">Failed</span>
          </div>
          <div className="stat-bar" style={{ '--progress': `${Math.min(outbox.failed / 10, 1) * 100}%` } as React.CSSProperties} />
        </motion.div>

        <motion.div variants={fadeInUp} className="outbox-stat processed">
          <CheckCircle2 size={20} />
          <div className="stat-info">
            <span className="stat-value">{outbox.processed.toLocaleString()}</span>
            <span className="stat-label">Processed</span>
          </div>
          <div className="stat-bar" style={{ '--progress': `${Math.min(outbox.processed / 1000, 1) * 100}%` } as React.CSSProperties} />
        </motion.div>
      </div>
    </motion.div>
  )
}
