import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import {
  AlertCircle,
  AlertTriangle,
  Bell,
  BellOff,
  Check,
  ChevronDown,
  ChevronUp,
  Clock,
  Info,
  Mail,
  MessageSquare,
  Phone,
  Repeat
} from 'lucide-react'
import { fetchApi, postApi } from '@/shared/api'
import { QUERY_CONFIG } from '@/shared/config'
import type { Alert, AlertRule, AlertSeverity, AlertsResponse } from '@/shared/types'
import { ApiError, fadeInLeft, formatTimeSince, Loading, PageHeader, staggerContainer } from '@/shared/ui'
import './Alerts.css'

function getSeverityIcon(severity: AlertSeverity) {
  switch (severity) {
    case 'CRITICAL':
      return <AlertTriangle size={20} />
    case 'WARNING':
      return <AlertCircle size={20} />
    case 'INFO':
      return <Info size={20} />
  }
}

function getSeverityClass(severity: AlertSeverity) {
  switch (severity) {
    case 'CRITICAL':
      return 'severity-critical'
    case 'WARNING':
      return 'severity-warning'
    case 'INFO':
      return 'severity-info'
  }
}

function getChannelIcon(channel: string) {
  switch (channel.toLowerCase()) {
    case 'slack':
      return <MessageSquare size={14} />
    case 'email':
      return <Mail size={14} />
    case 'pagerduty':
      return <Phone size={14} />
    default:
      return <Bell size={14} />
  }
}

function AlertCard({ alert, onAcknowledge, onSilence }: {
  alert: Alert
  onAcknowledge: (id: string) => void
  onSilence: (id: string, minutes: number) => void
}) {
  return (
    <motion.div
      className={`alert-card ${getSeverityClass(alert.severity)} ${alert.status.toLowerCase()}`}
      variants={fadeInLeft}
      layout
    >
      <div className="alert-card-header">
        <div className={`alert-severity ${getSeverityClass(alert.severity)}`}>
          {getSeverityIcon(alert.severity)}
          <span>{alert.severity}</span>
        </div>
        {alert.status === 'SILENCED' && alert.silencedUntil && (
          <span className="alert-silenced-badge">
            <BellOff size={12} />
            Until {new Date(alert.silencedUntil).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}
          </span>
        )}
        {alert.status === 'ACKNOWLEDGED' && (
          <span className="alert-ack-badge">
            <Check size={12} />
            Acknowledged
          </span>
        )}
      </div>

      <h3 className="alert-name">{alert.name}</h3>
      <p className="alert-message">{alert.message}</p>

      <div className="alert-details">
        <span className="alert-value mono">
          Current: {alert.currentValue} (threshold: {alert.threshold})
        </span>
        <div className="alert-meta">
          <span className="alert-time">
            <Clock size={14} />
            {formatTimeSince(alert.firedAt)}
          </span>
          <span className="alert-occurrences">
            <Repeat size={14} />
            {alert.occurrences}x
          </span>
        </div>
      </div>

      {alert.status === 'ACTIVE' && (
        <div className="alert-actions">
          <button
            className="btn btn-secondary btn-sm"
            onClick={() => onAcknowledge(alert.id)}
          >
            <Check size={14} />
            Acknowledge
          </button>
          <div className="silence-dropdown">
            <button className="btn btn-ghost btn-sm">
              <BellOff size={14} />
              Silence
            </button>
            <div className="silence-options">
              <button onClick={() => onSilence(alert.id, 30)}>30 mins</button>
              <button onClick={() => onSilence(alert.id, 60)}>1 hour</button>
              <button onClick={() => onSilence(alert.id, 240)}>4 hours</button>
            </div>
          </div>
        </div>
      )}
    </motion.div>
  )
}

function RuleCard({ rule }: { rule: AlertRule }) {
  return (
    <div className={`rule-card ${rule.enabled ? 'enabled' : 'disabled'}`}>
      <div className="rule-status">
        {rule.enabled ? (
          <Check size={16} className="rule-enabled" />
        ) : (
          <span className="rule-disabled-dot" />
        )}
      </div>
      <div className="rule-info">
        <span className="rule-name">{rule.name}</span>
        <span className={`rule-severity ${getSeverityClass(rule.severity)}`}>
          {rule.severity}
        </span>
      </div>
      <div className="rule-channels">
        {rule.channels.map(channel => (
          <span key={channel} className="rule-channel">
            {getChannelIcon(channel)}
          </span>
        ))}
      </div>
    </div>
  )
}

type FilterTab = 'all' | 'active' | 'acknowledged' | 'silenced'

export function Alerts() {
  const [activeTab, setActiveTab] = useState<FilterTab>('all')
  const [showRules, setShowRules] = useState(false)
  const queryClient = useQueryClient()

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['alerts'],
    queryFn: () => fetchApi<AlertsResponse>('/alerts'),
    refetchInterval: QUERY_CONFIG.DASHBOARD_INTERVAL,
    retry: 1,
  })

  const acknowledgeMutation = useMutation({
    mutationFn: (id: string) => postApi(`/alerts/${id}/acknowledge`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['alerts'] })
    },
  })

  const silenceMutation = useMutation({
    mutationFn: ({ id, minutes }: { id: string; minutes: number }) =>
      postApi(`/alerts/${id}/silence`, { minutes }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['alerts'] })
    },
  })

  if (isLoading) return <Loading />

  if (isError) {
    return (
      <div className="page-container">
        <PageHeader title="Alerts" subtitle="시스템 알림을 관리하고 대응합니다" />
        <ApiError onRetry={() => refetch()} />
      </div>
    )
  }

  const summary = data?.summary ?? { active: 0, acknowledged: 0, silenced: 0 }
  const allAlerts = data?.alerts ?? []
  const rules = data?.rules ?? []

  const filteredAlerts = allAlerts.filter(alert => {
    if (activeTab === 'all') return alert.status !== 'RESOLVED'
    if (activeTab === 'active') return alert.status === 'ACTIVE'
    if (activeTab === 'acknowledged') return alert.status === 'ACKNOWLEDGED'
    if (activeTab === 'silenced') return alert.status === 'SILENCED'
    return true
  })

  return (
    <div className="page-container">
      <PageHeader title="Alerts" subtitle="시스템 알림을 관리하고 대응합니다" />

      {/* Summary Tabs */}
      <motion.div
        className="alert-tabs"
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <button
          className={`alert-tab ${activeTab === 'all' ? 'active' : ''}`}
          onClick={() => setActiveTab('all')}
        >
          All
          <span className="tab-count">{summary.active + summary.acknowledged + summary.silenced}</span>
        </button>
        <button
          className={`alert-tab active-alerts ${activeTab === 'active' ? 'active' : ''}`}
          onClick={() => setActiveTab('active')}
        >
          <span className="tab-dot active" />
          Active
          <span className="tab-count">{summary.active}</span>
        </button>
        <button
          className={`alert-tab ${activeTab === 'acknowledged' ? 'active' : ''}`}
          onClick={() => setActiveTab('acknowledged')}
        >
          <Check size={14} />
          Acknowledged
          <span className="tab-count">{summary.acknowledged}</span>
        </button>
        <button
          className={`alert-tab ${activeTab === 'silenced' ? 'active' : ''}`}
          onClick={() => setActiveTab('silenced')}
        >
          <BellOff size={14} />
          Silenced
          <span className="tab-count">{summary.silenced}</span>
        </button>
      </motion.div>

      {/* Alerts List */}
      <motion.div
        className="alerts-list"
        variants={staggerContainer}
        initial="hidden"
        animate="show"
      >
        <AnimatePresence mode="popLayout">
          {filteredAlerts.length > 0 ? (
            filteredAlerts.map(alert => (
              <AlertCard
                key={alert.id}
                alert={alert}
                onAcknowledge={(id) => acknowledgeMutation.mutate(id)}
                onSilence={(id, minutes) => silenceMutation.mutate({ id, minutes })}
              />
            ))
          ) : (
            <motion.div
              className="alerts-empty"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
            >
              <Bell size={48} />
              <span>알림이 없습니다</span>
            </motion.div>
          )}
        </AnimatePresence>
      </motion.div>

      {/* Alert Rules Section */}
      <motion.div
        className="rules-section"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.3 }}
      >
        <button
          className="rules-toggle"
          onClick={() => setShowRules(!showRules)}
        >
          <span>Alert Rules</span>
          <span className="rules-count">{rules.length}</span>
          {showRules ? <ChevronUp size={20} /> : <ChevronDown size={20} />}
        </button>

        <AnimatePresence>
          {showRules && (
            <motion.div
              className="rules-list"
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              transition={{ duration: 0.2 }}
            >
              {rules.map(rule => (
                <RuleCard key={rule.id} rule={rule} />
              ))}
            </motion.div>
          )}
        </AnimatePresence>
      </motion.div>
    </div>
  )
}
