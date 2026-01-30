import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import {
  AlertTriangle,
  ArrowRight,
  CheckCircle2,
  Clock,
  Database,
  FileCode2,
  Inbox,
  Layers,
  Loader2,
  Play,
  Search,
  Zap
} from 'lucide-react'
import { fetchApi } from '@/shared/api'
import type { DashboardResponse, PipelineOverviewResponse } from '@/shared/types'
import { ApiError, fadeInUp, Loading, PageHeader, staggerContainer } from '@/shared/ui'
import './Dashboard.css'

export function Dashboard() {
  const { data: dashboard, isLoading: loadingDashboard, isError: errorDashboard, refetch: refetchDashboard } = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => fetchApi<DashboardResponse>('/dashboard'),
    retry: 1,
  })

  const { data: pipeline, isLoading: loadingPipeline, isError: errorPipeline, refetch: refetchPipeline } = useQuery({
    queryKey: ['pipeline-overview'],
    queryFn: () => fetchApi<PipelineOverviewResponse>('/pipeline/overview'),
    retry: 1,
  })

  if (loadingDashboard || loadingPipeline) return <Loading />

  // 에러 시 ApiError 표시
  if (errorDashboard || errorPipeline) {
    return (
      <div className="page-container">
        <PageHeader title="Dashboard" subtitle="IVM Lite 시스템 상태 실시간 모니터링" />
        <ApiError
          title="백엔드 연결 대기 중"
          message="Admin 서버가 실행되면 대시보드가 표시됩니다"
          onRetry={() => {
            refetchDashboard()
            refetchPipeline()
          }}
        />
      </div>
    )
  }

  const outbox = dashboard?.outbox?.total ?? { pending: 0, processing: 0, failed: 0, processed: 0 }
  const worker = dashboard?.worker ?? { running: false, processed: 0, failed: 0, polls: 0 }
  const rawDataCount = dashboard?.database?.rawDataCount ?? 0
  const sliceCount = pipeline?.slices?.total ?? 0
  const contractCount = dashboard?.database?.contractsCount ?? 0

  return (
    <div className="page-container">
      <PageHeader title="Dashboard" subtitle="IVM Lite 시스템 상태 실시간 모니터링" />

      {/* Quick Summary Row */}
      <motion.div
        className="summary-row"
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <div className={`worker-status ${worker.running ? 'running' : 'stopped'}`}>
          {worker.running ? (
            <motion.div
              animate={{ scale: [1, 1.2, 1] }}
              transition={{ repeat: Infinity, duration: 2 }}
            >
              <Zap size={18} />
            </motion.div>
          ) : (
            <AlertTriangle size={18} />
          )}
          <span>Worker {worker.running ? 'Active' : 'Stopped'}</span>
        </div>
        <div className="summary-stats">
          <span><Database size={14} /> {rawDataCount.toLocaleString()} RawData</span>
          <span><Layers size={14} /> {sliceCount.toLocaleString()} Slices</span>
          <span><FileCode2 size={14} /> {contractCount} Contracts</span>
        </div>
      </motion.div>

      {/* Main Grid */}
      <div className="dashboard-grid">
        {/* Left: Outbox Status */}
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

        {/* Right: Quick Actions + Worker Info */}
        <motion.div
          className="actions-panel"
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ delay: 0.2 }}
        >
          <div className="panel-header">
            <Play size={18} />
            <h3>Quick Actions</h3>
          </div>

          <div className="quick-actions">
            <Link to="/explorer" className="action-card">
              <Search size={24} />
              <div>
                <span className="action-title">Data Explorer</span>
                <span className="action-desc">RawData / Slice 검색</span>
              </div>
              <ArrowRight size={16} />
            </Link>

            <Link to="/playground" className="action-card">
              <Play size={24} />
              <div>
                <span className="action-title">Playground</span>
                <span className="action-desc">데이터 즉시 테스트</span>
              </div>
              <ArrowRight size={16} />
            </Link>

            <Link to="/contracts" className="action-card">
              <FileCode2 size={24} />
              <div>
                <span className="action-title">Contracts</span>
                <span className="action-desc">스키마/룰셋 관리</span>
              </div>
              <ArrowRight size={16} />
            </Link>

            <Link to="/pipeline" className="action-card">
              <Layers size={24} />
              <div>
                <span className="action-title">Pipeline</span>
                <span className="action-desc">데이터 흐름 확인</span>
              </div>
              <ArrowRight size={16} />
            </Link>
          </div>

          {/* Worker Stats */}
          <div className="worker-details">
            <div className="worker-detail">
              <span className="detail-label">처리 완료</span>
              <span className="detail-value">{worker.processed.toLocaleString()}</span>
            </div>
            <div className="worker-detail">
              <span className="detail-label">실패</span>
              <span className="detail-value error">{worker.failed.toLocaleString()}</span>
            </div>
            <div className="worker-detail">
              <span className="detail-label">Polls</span>
              <span className="detail-value">{worker.polls.toLocaleString()}</span>
            </div>
          </div>
        </motion.div>
      </div>

      {/* Slice Types (compact) */}
      {pipeline?.slices?.byType && Object.keys(pipeline.slices.byType).length > 0 && (
        <motion.div
          className="slice-types-section"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.4 }}
        >
          <div className="panel-header">
            <Layers size={18} />
            <h3>Slice Types</h3>
          </div>
          <div className="slice-chips">
            {Object.entries(pipeline.slices.byType).map(([type, count]) => (
              <div key={type} className="slice-chip">
                <span className="chip-type">{type}</span>
                <span className="chip-count">{count.toLocaleString()}</span>
              </div>
            ))}
          </div>
        </motion.div>
      )}

      {/* Timestamp */}
      <motion.div
        className="timestamp"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.5 }}
      >
        {dashboard?.timestamp ? new Date(dashboard.timestamp).toLocaleString('ko-KR') : '-'}
      </motion.div>
    </div>
  )
}
