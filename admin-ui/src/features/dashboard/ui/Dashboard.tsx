import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Clock,
  Cpu,
  Database,
  Loader2,
  Zap
} from 'lucide-react'
import { fetchApi } from '@/shared/api'
import type { DashboardResponse, PipelineOverviewResponse } from '@/shared/types'
import { fadeInUp, Loading, PageHeader, staggerContainer } from '@/shared/ui'
import { HourlyChart } from './HourlyChart'
import './Dashboard.css'

export function Dashboard() {
  const { data: dashboard, isLoading: loadingDashboard } = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => fetchApi<DashboardResponse>('/dashboard'),
  })

  const { data: pipeline, isLoading: loadingPipeline } = useQuery({
    queryKey: ['pipeline-overview'],
    queryFn: () => fetchApi<PipelineOverviewResponse>('/pipeline/overview'),
  })

  if (loadingDashboard || loadingPipeline) return <Loading />

  const outbox = dashboard?.outbox?.total ?? { pending: 0, processing: 0, failed: 0, processed: 0 }
  const worker = dashboard?.worker ?? { running: false, processed: 0, failed: 0, polls: 0 }

  return (
    <div className="page-container">
      <PageHeader title="Dashboard" subtitle="IVM Lite 시스템 상태를 실시간으로 모니터링합니다" />

      {/* Worker Status Banner */}
      <motion.div 
        className={`worker-banner ${worker.running ? 'running' : 'stopped'}`}
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
      >
        <div className="worker-indicator">
          {worker.running ? (
            <motion.div
              animate={{ scale: [1, 1.2, 1] }}
              transition={{ repeat: Infinity, duration: 2 }}
            >
              <Zap size={24} />
            </motion.div>
          ) : (
            <AlertTriangle size={24} />
          )}
        </div>
        <div className="worker-info">
          <span className="worker-status">
            Worker {worker.running ? '실행 중' : '중지됨'}
          </span>
          <span className="worker-stats">
            처리: {worker.processed.toLocaleString()} | 
            실패: {worker.failed.toLocaleString()} | 
            Polls: {worker.polls.toLocaleString()}
          </span>
        </div>
      </motion.div>

      {/* Stats Grid */}
      <motion.div 
        className="stats-grid"
        variants={staggerContainer}
        initial="hidden"
        animate="show"
      >
        <motion.div variants={fadeInUp} className="stat-card pending">
          <div className="stat-icon">
            <Clock size={24} />
          </div>
          <div className="stat-content">
            <span className="stat-value">{outbox.pending.toLocaleString()}</span>
            <span className="stat-label">Pending</span>
          </div>
          <div className="stat-bar" style={{ '--progress': `${Math.min(outbox.pending / 100, 100)}%` } as React.CSSProperties} />
        </motion.div>

        <motion.div variants={fadeInUp} className="stat-card processing">
          <div className="stat-icon">
            <Loader2 size={24} className="spin" />
          </div>
          <div className="stat-content">
            <span className="stat-value">{outbox.processing.toLocaleString()}</span>
            <span className="stat-label">Processing</span>
          </div>
          <div className="stat-bar" style={{ '--progress': `${Math.min(outbox.processing / 50, 100)}%` } as React.CSSProperties} />
        </motion.div>

        <motion.div variants={fadeInUp} className="stat-card failed">
          <div className="stat-icon">
            <AlertTriangle size={24} />
          </div>
          <div className="stat-content">
            <span className="stat-value">{outbox.failed.toLocaleString()}</span>
            <span className="stat-label">Failed</span>
          </div>
          <div className="stat-bar" style={{ '--progress': `${Math.min(outbox.failed / 10, 100)}%` } as React.CSSProperties} />
        </motion.div>

        <motion.div variants={fadeInUp} className="stat-card processed">
          <div className="stat-icon">
            <CheckCircle2 size={24} />
          </div>
          <div className="stat-content">
            <span className="stat-value">{outbox.processed.toLocaleString()}</span>
            <span className="stat-label">Processed</span>
          </div>
          <div className="stat-bar" style={{ '--progress': `${Math.min(outbox.processed / 1000, 100)}%` } as React.CSSProperties} />
        </motion.div>
      </motion.div>

      {/* GAP-2: 시간대별 통계 차트 */}
      <HourlyChart />

      {/* Pipeline Flow */}
      <motion.div
        className="pipeline-section"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.3 }}
      >
        <h2 className="section-title">Data Pipeline</h2>
        <div className="pipeline-flow">
          {pipeline?.stages?.map((stage, index) => (
            <motion.div 
              key={stage.name}
              className={`pipeline-stage ${stage.status.toLowerCase()}`}
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.4 + index * 0.1 }}
            >
              <div className="stage-icon">{stage.icon}</div>
              <div className="stage-info">
                <span className="stage-name">{stage.name}</span>
                <span className="stage-count">{stage.count.toLocaleString()}</span>
              </div>
              <span className="stage-description">{stage.description}</span>
              {index < (pipeline?.stages?.length ?? 0) - 1 && (
                <div className="stage-connector">
                  <motion.div
                    className="connector-line"
                    initial={{ scaleX: 0 }}
                    animate={{ scaleX: 1 }}
                    transition={{ delay: 0.5 + index * 0.1 }}
                  />
                  <motion.div
                    className="connector-arrow"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    transition={{ delay: 0.6 + index * 0.1 }}
                  >
                    →
                  </motion.div>
                </div>
              )}
            </motion.div>
          ))}
        </div>
      </motion.div>

      {/* Database Stats */}
      <motion.div 
        className="db-section"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.5 }}
      >
        <h2 className="section-title">Database</h2>
        <div className="db-grid">
          <div className="db-card">
            <Database size={32} className="db-icon" />
            <div className="db-info">
              <span className="db-value">{dashboard?.database?.rawDataCount?.toLocaleString() ?? 0}</span>
              <span className="db-label">Raw Data Records</span>
            </div>
          </div>
          <div className="db-card">
            <Cpu size={32} className="db-icon" />
            <div className="db-info">
              <span className="db-value">{pipeline?.slices?.total?.toLocaleString() ?? 0}</span>
              <span className="db-label">Slice Records</span>
            </div>
          </div>
          <div className="db-card">
            <Activity size={32} className="db-icon" />
            <div className="db-info">
              <span className="db-value">
                {(outbox.pending + outbox.processing + outbox.failed + outbox.processed).toLocaleString()}
              </span>
              <span className="db-label">Outbox Entries</span>
            </div>
          </div>
        </div>
      </motion.div>

      {/* Slice Types */}
      {pipeline?.slices?.byType && Object.keys(pipeline.slices.byType).length > 0 && (
        <motion.div 
          className="slices-section"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.6 }}
        >
          <h2 className="section-title">Slice Types</h2>
          <div className="slices-grid">
            {Object.entries(pipeline.slices.byType).map(([type, count], index) => (
              <motion.div 
                key={type}
                className="slice-card"
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ delay: 0.7 + index * 0.05 }}
              >
                <span className="slice-type">{type}</span>
                <span className="slice-count">{count.toLocaleString()}</span>
              </motion.div>
            ))}
          </div>
        </motion.div>
      )}

      {/* Timestamp */}
      <motion.div 
        className="timestamp"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.8 }}
      >
        마지막 업데이트: {dashboard?.timestamp ? new Date(dashboard.timestamp).toLocaleString('ko-KR') : '-'}
      </motion.div>
    </div>
  )
}
