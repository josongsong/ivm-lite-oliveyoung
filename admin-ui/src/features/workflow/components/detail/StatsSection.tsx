import { Activity, Database } from 'lucide-react'
import type { NodeStats } from '../../model/types'
import { formatNumber } from './utils'

interface StatsSectionProps {
  stats: NodeStats
}

export function StatsSection({ stats }: StatsSectionProps) {
  return (
    <section className="panel-section">
      <h4 className="section-title">
        <Activity size={14} />
        실시간 통계
      </h4>
      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-left">
            <Database size={14} className="stat-icon" />
            <span className="stat-label">Total Records</span>
          </div>
          <div className="stat-value">
            {formatNumber(stats.recordCount ?? 0)}
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-left">
            <Activity size={14} className="stat-icon" />
            <span className="stat-label">Throughput</span>
          </div>
          <div className="stat-value">
            {typeof stats.throughput === 'number'
              ? stats.throughput.toFixed(1)
              : '0.0'}
            <span className="stat-unit">/min</span>
          </div>
        </div>
        {typeof stats.latencyP99Ms === 'number' && (
          <div className="stat-card">
            <div className="stat-left">
              <Activity size={14} className="stat-icon" />
              <span className="stat-label">P99 Latency</span>
            </div>
            <div className="stat-value">
              {stats.latencyP99Ms}
              <span className="stat-unit">ms</span>
            </div>
          </div>
        )}
        <div className={`stat-card ${(stats.errorCount ?? 0) > 0 ? 'error' : ''}`}>
          <div className="stat-left">
            <Activity size={14} className="stat-icon" />
            <span className="stat-label">Errors</span>
          </div>
          <div className="stat-value">
            {stats.errorCount ?? 0}
          </div>
        </div>
      </div>
    </section>
  )
}
