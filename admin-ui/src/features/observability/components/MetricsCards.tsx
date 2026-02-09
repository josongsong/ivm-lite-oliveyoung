import { motion } from 'framer-motion'
import { Activity, AlertCircle, Clock, Minus, TrendingDown, TrendingUp } from 'lucide-react'
import type { LagMetrics, PipelineMetrics } from '@/shared/types'
import { fadeInUp, staggerContainer } from '@/shared/ui'

interface MetricsCardsProps {
  metrics: PipelineMetrics | undefined
  lag: LagMetrics | undefined
}

function LagTrendIcon({ trend }: { trend: string }) {
  switch (trend) {
    case 'RISING':
      return <TrendingUp size={16} className="trend-up-bad" />
    case 'FALLING':
      return <TrendingDown size={16} className="trend-down-good" />
    default:
      return <Minus size={16} className="trend-stable" />
  }
}

export function MetricsCards({ metrics, lag }: MetricsCardsProps) {
  return (
    <motion.div
      className="metrics-grid"
      variants={staggerContainer}
      initial="hidden"
      animate="show"
    >
      <motion.div variants={fadeInUp} className="metric-card">
        <div className="metric-icon throughput">
          <Activity size={24} />
        </div>
        <div className="metric-content">
          <span className="metric-label">THROUGHPUT</span>
          <span className="metric-value">
            {Math.round(metrics?.throughput?.recordsPerMinute ?? 0).toLocaleString()}/min
          </span>
          <div className="metric-trend">
            <span className="trend-neutral mono">
              {(metrics?.throughput?.recordsPerSecond ?? 0).toFixed(1)}/sec
            </span>
          </div>
        </div>
      </motion.div>

      <motion.div variants={fadeInUp} className="metric-card">
        <div className="metric-icon latency">
          <Clock size={24} />
        </div>
        <div className="metric-content">
          <span className="metric-label">LATENCY (P99)</span>
          <span className="metric-value">
            {Math.round(metrics?.e2eLatency?.p99Ms ?? 0)}ms
          </span>
          <div className="metric-trend">
            <span className="trend-neutral mono">
              avg: {Math.round(metrics?.e2eLatency?.avgMs ?? 0)}ms
            </span>
          </div>
        </div>
      </motion.div>

      <motion.div variants={fadeInUp} className="metric-card">
        <div className="metric-icon lag">
          <AlertCircle size={24} />
        </div>
        <div className="metric-content">
          <span className="metric-label">LAG</span>
          <span className="metric-value">
            {(lag?.currentLag ?? 0).toLocaleString()} records
          </span>
          <div className="metric-trend">
            <LagTrendIcon trend={lag?.trend ?? 'STABLE'} />
            <span className="trend-neutral">{lag?.trend?.toLowerCase() ?? 'stable'}</span>
          </div>
        </div>
      </motion.div>
    </motion.div>
  )
}
