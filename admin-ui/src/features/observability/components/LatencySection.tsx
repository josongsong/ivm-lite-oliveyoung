import { motion } from 'framer-motion'
import type { PipelineMetrics } from '@/shared/types'

interface LatencySectionProps {
  metrics: PipelineMetrics | undefined
}

export function LatencySection({ metrics }: LatencySectionProps) {
  return (
    <motion.div
      className="obs-section"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.3 }}
    >
      <h2 className="section-title">Latency Distribution</h2>
      <div className="percentiles-grid">
        <div className="percentile-card">
          <span className="percentile-label">P50</span>
          <span className="percentile-value mono">
            {Math.round(metrics?.e2eLatency?.p50Ms ?? 0)}ms
          </span>
        </div>
        <div className="percentile-card">
          <span className="percentile-label">P95</span>
          <span className="percentile-value mono">
            {Math.round(metrics?.e2eLatency?.p95Ms ?? 0)}ms
          </span>
        </div>
        <div className="percentile-card highlight">
          <span className="percentile-label">P99</span>
          <span className="percentile-value mono">
            {Math.round(metrics?.e2eLatency?.p99Ms ?? 0)}ms
          </span>
        </div>
        <div className="percentile-card">
          <span className="percentile-label">MAX</span>
          <span className="percentile-value mono">
            {Math.round(metrics?.e2eLatency?.maxMs ?? 0)}ms
          </span>
        </div>
      </div>
    </motion.div>
  )
}
