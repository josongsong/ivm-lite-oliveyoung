import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import {
  Activity,
  AlertCircle,
  Clock,
  Gauge,
  Minus,
  TrendingDown,
  TrendingUp
} from 'lucide-react'
import { fetchApi } from '@/shared/api'
import { QUERY_CONFIG } from '@/shared/config'
import type { ObservabilityResponse } from '@/shared/types'
import { ApiError, fadeInUp, Loading, PageHeader, staggerContainer, StatusBadge } from '@/shared/ui'
import './Observability.css'

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

export function Observability() {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['observability-dashboard'],
    queryFn: () => fetchApi<ObservabilityResponse>('/observability/dashboard'),
    refetchInterval: QUERY_CONFIG.OBSERVABILITY_INTERVAL,
    retry: 1,
  })

  if (isLoading) return <Loading />

  if (isError) {
    return (
      <div className="page-container">
        <PageHeader title="Observability" subtitle="파이프라인 성능 지표를 실시간으로 모니터링합니다" />
        <ApiError onRetry={() => refetch()} />
      </div>
    )
  }

  const metrics = data?.metrics
  const lag = data?.lag
  const status = data?.status
  const queueDepths = metrics?.queueDepths

  const totalQueue = queueDepths
    ? queueDepths.pending + queueDepths.processing + queueDepths.failed + queueDepths.dlq
    : 1

  return (
    <div className="page-container">
      <PageHeader title="Observability" subtitle="파이프라인 성능 지표를 실시간으로 모니터링합니다" />

      {/* Status Banner */}
      {status && (
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
      )}

      {/* Key Metrics */}
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

      {/* Throughput Summary */}
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

      {/* Latency Percentiles */}
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

      {/* Queue Status */}
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

      {/* Timestamp */}
      <motion.div
        className="timestamp"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.5 }}
      >
        마지막 업데이트: {data?.timestamp ? new Date(data.timestamp).toLocaleString('ko-KR') : '-'}
      </motion.div>
    </div>
  )
}
