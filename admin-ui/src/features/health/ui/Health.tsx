import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Cloud,
  Database,
  Inbox,
  Search,
  Server,
  XCircle,
  Zap
} from 'lucide-react'
import { fetchApi } from '@/shared/api'
import { QUERY_CONFIG } from '@/shared/config'
import type { ComponentHealth, HealthResponse, HealthStatus } from '@/shared/types'
import { ApiError, fadeInUp, formatUptime, Loading, PageHeader, staggerContainer } from '@/shared/ui'
import './Health.css'

const componentIcons: Record<string, typeof Database> = {
  'Postgres': Database,
  'PostgreSQL': Database,
  'Worker': Zap,
  'Outbox': Inbox,
  'DynamoDB': Cloud,
  'Kafka': Activity,
  'OpenSearch': Search,
}

function getStatusIcon(status: HealthStatus) {
  switch (status) {
    case 'HEALTHY':
      return <CheckCircle2 size={24} />
    case 'DEGRADED':
    case 'UNKNOWN':
      return <AlertTriangle size={24} />
    case 'UNHEALTHY':
      return <XCircle size={24} />
  }
}

function getStatusClass(status: HealthStatus): string {
  switch (status) {
    case 'HEALTHY':
      return 'status-healthy'
    case 'DEGRADED':
    case 'UNKNOWN':
      return 'status-degraded'
    case 'UNHEALTHY':
      return 'status-unhealthy'
  }
}

function ComponentCard({ component }: { component: ComponentHealth }) {
  const IconComponent = componentIcons[component.name] || Server

  return (
    <motion.div
      className={`health-card ${getStatusClass(component.status)}`}
      variants={fadeInUp}
      whileHover={{ scale: 1.02 }}
      transition={{ type: 'spring', stiffness: 300 }}
    >
      <div className="health-card-header">
        <div className={`health-status-dot ${getStatusClass(component.status)}`}>
          {getStatusIcon(component.status)}
        </div>
      </div>
      <div className="health-card-icon">
        <IconComponent size={32} />
      </div>
      <div className="health-card-content">
        <span className="health-card-name">{component.name}</span>
        {component.latencyMs !== null && (
          <span className="health-card-latency">{component.latencyMs}ms</span>
        )}
        <div className="health-card-details">
          {Object.entries(component.details).slice(0, 2).map(([key, value]) => (
            <span key={key} className="health-card-detail">
              {String(value)}
            </span>
          ))}
        </div>
      </div>
    </motion.div>
  )
}

export function Health() {
  const { data: health, isLoading, isError, refetch } = useQuery({
    queryKey: ['health'],
    queryFn: () => fetchApi<HealthResponse>('/health', {
      // Health API는 503(Service Unavailable)도 정상 응답으로 처리
      // (시스템이 UNHEALTHY 상태일 때도 유효한 JSON 응답을 반환)
      allowedStatusCodes: [503],
    }),
    refetchInterval: QUERY_CONFIG.DASHBOARD_INTERVAL,
    retry: 1,
  })

  if (isLoading) return <Loading />

  if (isError) {
    return (
      <div className="page-container">
        <PageHeader title="System Health" subtitle="시스템 전체 상태를 실시간으로 확인합니다" />
        <ApiError
          title="서버 연결 불가"
          message="Admin 서버가 실행되면 헬스 체크가 표시됩니다"
          onRetry={refetch}
        />
      </div>
    )
  }

  const overall = health?.overall ?? 'UNHEALTHY'
  const uptime = health?.uptime ?? 0
  const components = health?.components ?? []
  const version = health?.version ?? '-'

  return (
    <div className="page-container">
      <PageHeader title="System Health" subtitle="시스템 전체 상태를 실시간으로 확인합니다" />

      {/* Overall Status Banner */}
      <motion.div
        className={`health-banner ${getStatusClass(overall)}`}
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
      >
        <div className="health-banner-status">
          <motion.div
            className="health-banner-icon"
            animate={overall === 'HEALTHY' ? { scale: [1, 1.1, 1] } : {}}
            transition={{ repeat: Infinity, duration: 2 }}
          >
            {getStatusIcon(overall)}
          </motion.div>
          <div className="health-banner-text">
            <span className="health-banner-label">Overall Status</span>
            <span className="health-banner-value">{overall}</span>
          </div>
        </div>
        <div className="health-banner-uptime">
          <span className="uptime-label">Uptime</span>
          <span className="uptime-value">{formatUptime(uptime)}</span>
        </div>
      </motion.div>

      {/* Components Grid */}
      <motion.div
        className="health-section"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2 }}
      >
        <h2 className="section-title">Components</h2>
        <motion.div
          className="health-grid"
          variants={staggerContainer}
          initial="hidden"
          animate="show"
        >
          {components.map((component) => (
            <ComponentCard key={component.name} component={component} />
          ))}
        </motion.div>
      </motion.div>

      {/* Version Info */}
      <motion.div
        className="health-version"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.4 }}
      >
        <span className="version-text">
          Version: {version} | Updated: {health?.timestamp ? new Date(health.timestamp).toLocaleString('ko-KR') : '-'}
        </span>
      </motion.div>
    </div>
  )
}
