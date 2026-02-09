import { motion } from 'framer-motion'
import { Gauge } from 'lucide-react'
import type { PipelineMetrics } from '@/shared/types'

interface ThroughputSectionProps {
  metrics: PipelineMetrics | undefined
}

export function ThroughputSection({ metrics }: ThroughputSectionProps) {
  return (
    <motion.div
      className="obs-section"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.2 }}
    >
      <h2 className="section-title">Throughput Summary</h2>
      <div className="chart-container">
        {metrics?.throughput ? (
          <div className="throughput-summary">
            <div className="throughput-item">
              <span className="throughput-label">Last Minute</span>
              <span className="throughput-value mono">
                {metrics.throughput.recentMinuteCount.toLocaleString()} records
              </span>
            </div>
            <div className="throughput-item">
              <span className="throughput-label">Per Hour</span>
              <span className="throughput-value mono">
                {Math.round(metrics.throughput.recordsPerHour).toLocaleString()} records
              </span>
            </div>
            <div className="throughput-item">
              <span className="throughput-label">Sample Count</span>
              <span className="throughput-value mono">
                {(metrics.e2eLatency?.sampleCount ?? 0).toLocaleString()}
              </span>
            </div>
          </div>
        ) : (
          <div className="chart-empty">
            <Gauge size={48} />
            <span>데이터 수집 중...</span>
          </div>
        )}
      </div>
    </motion.div>
  )
}
