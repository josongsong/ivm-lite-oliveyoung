import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import {
  CheckCircle2,
  Clock,
  Loader2,
  Pause,
  Play,
  Plus,
  RefreshCw,
  X,
  XCircle,
  Zap
} from 'lucide-react'
import { fetchApi, postApi } from '@/shared/api'
import { QUERY_CONFIG } from '@/shared/config'
import type { BackfillJob, BackfillResponse, BackfillStatus } from '@/shared/types'
import { fadeInUp, formatDuration, formatTimeSince, Loading, PageHeader, staggerContainer } from '@/shared/ui'
import './Backfill.css'

function getStatusIcon(status: BackfillStatus) {
  switch (status) {
    case 'RUNNING':
      return <Loader2 size={18} className="spin" />
    case 'COMPLETED':
      return <CheckCircle2 size={18} />
    case 'FAILED':
      return <XCircle size={18} />
    case 'PAUSED':
      return <Pause size={18} />
    case 'CANCELLED':
      return <X size={18} />
  }
}

function getStatusClass(status: BackfillStatus) {
  switch (status) {
    case 'RUNNING':
      return 'status-running'
    case 'COMPLETED':
      return 'status-completed'
    case 'FAILED':
      return 'status-failed'
    case 'PAUSED':
      return 'status-paused'
    case 'CANCELLED':
      return 'status-cancelled'
  }
}

function formatEta(seconds: number | null): string {
  if (seconds === null || seconds === 0) return '-'
  return formatDuration(seconds)
}

function ActiveJobCard({ job, onPause, onResume, onCancel }: {
  job: BackfillJob
  onPause: (id: string) => void
  onResume: (id: string) => void
  onCancel: (id: string) => void
}) {
  const isRunning = job.status === 'RUNNING'
  const isPaused = job.status === 'PAUSED'

  return (
    <motion.div
      className={`job-card active ${getStatusClass(job.status)}`}
      variants={fadeInUp}
      layout
    >
      <div className="job-header">
        <div className="job-status-icon">
          {getStatusIcon(job.status)}
        </div>
        <div className="job-info">
          <h3 className="job-name">{job.name}</h3>
          <span className={`job-status-badge ${getStatusClass(job.status)}`}>
            {job.status}
          </span>
        </div>
      </div>

      <div className="job-progress-section">
        <div className="job-progress-header">
          <span className="job-progress-text">
            {job.processedCount.toLocaleString()} / {job.totalCount.toLocaleString()}
          </span>
          <span className="job-progress-percent mono">{job.progress}%</span>
        </div>
        <div className="job-progress-track">
          <motion.div
            className={`job-progress-fill ${getStatusClass(job.status)}`}
            initial={{ width: 0 }}
            animate={{ width: `${job.progress}%` }}
            transition={{ duration: 0.5 }}
          />
        </div>
      </div>

      <div className="job-stats">
        {job.throughputPerSec !== null && (
          <div className="job-stat">
            <Zap size={14} />
            <span className="mono">{job.throughputPerSec} rec/sec</span>
          </div>
        )}
        {job.etaSeconds !== null && (
          <div className="job-stat">
            <Clock size={14} />
            <span>ETA: {formatEta(job.etaSeconds)}</span>
          </div>
        )}
      </div>

      <div className="job-actions">
        {isRunning && (
          <button className="btn btn-secondary btn-sm" onClick={() => onPause(job.id)}>
            <Pause size={14} />
            Pause
          </button>
        )}
        {isPaused && (
          <button className="btn btn-primary btn-sm" onClick={() => onResume(job.id)}>
            <Play size={14} />
            Resume
          </button>
        )}
        <button className="btn btn-ghost btn-sm" onClick={() => onCancel(job.id)}>
          <X size={14} />
          Cancel
        </button>
      </div>
    </motion.div>
  )
}

function RecentJobCard({ job, onRetry }: {
  job: BackfillJob
  onRetry?: (id: string) => void
}) {
  return (
    <motion.div
      className={`job-card recent ${getStatusClass(job.status)}`}
      variants={fadeInUp}
    >
      <div className="job-header">
        <div className="job-status-icon">
          {getStatusIcon(job.status)}
        </div>
        <div className="job-info">
          <h3 className="job-name">{job.name}</h3>
          <span className={`job-status-badge ${getStatusClass(job.status)}`}>
            {job.status}
          </span>
        </div>
        <span className="job-time">{formatTimeSince(job.completedAt ?? job.createdAt)}</span>
      </div>

      <div className="job-summary">
        <span className="job-count mono">
          {job.processedCount.toLocaleString()}/{job.totalCount.toLocaleString()} ({job.progress}%)
        </span>
        {job.durationSeconds && (
          <span className="job-duration">
            Duration: {formatDuration(job.durationSeconds)}
          </span>
        )}
      </div>

      {job.errorMessage && (
        <div className="job-error">
          <XCircle size={14} />
          <span>{job.errorMessage}</span>
        </div>
      )}

      {job.status === 'FAILED' && onRetry && (
        <div className="job-actions">
          <button className="btn btn-secondary btn-sm" onClick={() => onRetry(job.id)}>
            <RefreshCw size={14} />
            Retry
          </button>
        </div>
      )}
    </motion.div>
  )
}

interface NewJobModalProps {
  isOpen: boolean
  onClose: () => void
  onCreate: (name: string) => void
}

function NewJobModal({ isOpen, onClose, onCreate }: NewJobModalProps) {
  const [jobName, setJobName] = useState('')

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (jobName.trim()) {
      onCreate(jobName.trim())
      setJobName('')
      onClose()
    }
  }

  if (!isOpen) return null

  return (
    <motion.div
      className="modal-overlay"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      onClick={onClose}
    >
      <motion.div
        className="modal-content"
        initial={{ scale: 0.95, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        exit={{ scale: 0.95, opacity: 0 }}
        onClick={e => e.stopPropagation()}
      >
        <h2 className="modal-title">New Backfill Job</h2>
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Job Name</label>
            <input
              type="text"
              className="form-input"
              value={jobName}
              onChange={e => setJobName(e.target.value)}
              placeholder="e.g., Product Full Reprocess"
              autoFocus
            />
          </div>
          <div className="modal-actions">
            <button type="button" className="btn btn-ghost" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={!jobName.trim()}>
              <Play size={16} />
              Create & Start
            </button>
          </div>
        </form>
      </motion.div>
    </motion.div>
  )
}

export function Backfill() {
  const [showNewJobModal, setShowNewJobModal] = useState(false)
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['backfill'],
    queryFn: () => fetchApi<BackfillResponse>('/backfill'),
    refetchInterval: QUERY_CONFIG.REALTIME_INTERVAL,
  })

  const pauseMutation = useMutation({
    mutationFn: (id: string) => postApi(`/backfill/${id}/pause`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['backfill'] }),
  })

  const resumeMutation = useMutation({
    mutationFn: (id: string) => postApi(`/backfill/${id}/resume`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['backfill'] }),
  })

  const cancelMutation = useMutation({
    mutationFn: (id: string) => postApi(`/backfill/${id}/cancel`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['backfill'] }),
  })

  const retryMutation = useMutation({
    mutationFn: (id: string) => postApi(`/backfill/${id}/retry`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['backfill'] }),
  })

  const createMutation = useMutation({
    mutationFn: (name: string) => postApi('/backfill', { name, scope: { type: 'ALL' } }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['backfill'] }),
  })

  if (isLoading) return <Loading />

  const activeJobs = data?.activeJobs ?? []
  const recentJobs = data?.recentJobs ?? []

  return (
    <div className="page-container">
      <PageHeader title="Backfill Jobs" subtitle="데이터 재처리 작업을 관리합니다" />

      <motion.button
        className="btn btn-primary new-job-btn"
        onClick={() => setShowNewJobModal(true)}
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        whileHover={{ scale: 1.02 }}
        whileTap={{ scale: 0.98 }}
      >
        <Plus size={18} />
        New Job
      </motion.button>

      {/* Active Jobs */}
      <motion.div
        className="backfill-section"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2 }}
      >
        <h2 className="section-title">
          Active Jobs
          {activeJobs.length > 0 && (
            <span className="section-count">{activeJobs.length}</span>
          )}
        </h2>

        <motion.div
          className="jobs-grid"
          variants={staggerContainer}
          initial="hidden"
          animate="show"
        >
          <AnimatePresence mode="popLayout">
            {activeJobs.length > 0 ? (
              activeJobs.map(job => (
                <ActiveJobCard
                  key={job.id}
                  job={job}
                  onPause={(id) => pauseMutation.mutate(id)}
                  onResume={(id) => resumeMutation.mutate(id)}
                  onCancel={(id) => cancelMutation.mutate(id)}
                />
              ))
            ) : (
              <motion.div className="jobs-empty" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
                <RefreshCw size={48} />
                <span>진행 중인 작업이 없습니다</span>
              </motion.div>
            )}
          </AnimatePresence>
        </motion.div>
      </motion.div>

      {/* Recent Jobs */}
      <motion.div
        className="backfill-section"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.3 }}
      >
        <h2 className="section-title">Recent Jobs</h2>

        <motion.div
          className="jobs-list"
          variants={staggerContainer}
          initial="hidden"
          animate="show"
        >
          {recentJobs.length > 0 ? (
            recentJobs.map(job => (
              <RecentJobCard
                key={job.id}
                job={job}
                onRetry={(id) => retryMutation.mutate(id)}
              />
            ))
          ) : (
            <div className="jobs-empty small">
              <span>최근 완료된 작업이 없습니다</span>
            </div>
          )}
        </motion.div>
      </motion.div>

      <AnimatePresence>
        {showNewJobModal && (
          <NewJobModal
            isOpen={showNewJobModal}
            onClose={() => setShowNewJobModal(false)}
            onCreate={(name) => createMutation.mutate(name)}
          />
        )}
      </AnimatePresence>
    </div>
  )
}
