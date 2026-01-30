import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import {
  Activity,
  Box,
  CheckCircle2,
  Clock,
  Cloud,
  Cpu,
  Database,
  ExternalLink,
  GitBranch,
  GitCommit,
  Globe,
  HardDrive,
  Link as LinkIcon,
  Play,
  RefreshCw,
  Server,
  Settings,
  Sparkles,
  Square,
  User,
  XCircle,
  Zap,
} from 'lucide-react'
import { fetchApi } from '@/shared/api'
import { QUERY_CONFIG } from '@/shared/config'
import type { AppInstance, DatabaseInfo, EnvironmentResponse, GitInfo } from '@/shared/types'
import { ApiError, fadeInUp, Loading, PageHeader, staggerContainer } from '@/shared/ui'
import { useAppStore } from '@/shared/store'
import './Environment.css'

// ==================== App Instance Card ====================
function AppInstanceCard({ instance, index }: { instance: AppInstance; index: number }) {
  const getIcon = () => {
    switch (instance.type) {
      case 'Backend':
        return <Server size={22} />
      case 'Frontend':
        return <Globe size={22} />
      case 'Worker':
        return <RefreshCw size={22} />
      default:
        return <Box size={22} />
    }
  }

  const getStatusConfig = () => {
    switch (instance.status) {
      case 'running':
        return { 
          icon: <Play size={12} />, 
          label: 'Running', 
          color: 'var(--accent-green)',
          bgColor: 'rgba(34, 197, 94, 0.1)',
          pulse: true
        }
      case 'stopped':
        return { 
          icon: <Square size={12} />, 
          label: 'Stopped', 
          color: 'var(--text-muted)',
          bgColor: 'rgba(128, 128, 128, 0.1)',
          pulse: false
        }
      default:
        return { 
          icon: <XCircle size={12} />, 
          label: 'Unknown', 
          color: 'var(--accent-yellow)',
          bgColor: 'rgba(234, 179, 8, 0.1)',
          pulse: false
        }
    }
  }

  const getTypeColor = () => {
    switch (instance.type) {
      case 'Backend':
        return 'var(--accent-cyan)'
      case 'Frontend':
        return 'var(--accent-purple)'
      case 'Worker':
        return 'var(--accent-yellow)'
      default:
        return 'var(--text-muted)'
    }
  }

  const status = getStatusConfig()
  const typeColor = getTypeColor()

  return (
    <motion.div 
      className={`instance-card ${instance.status}`}
      variants={fadeInUp}
      initial="hidden"
      animate="show"
      custom={index}
      whileHover={{ scale: 1.02, y: -4 }}
      transition={{ type: 'spring', stiffness: 300 }}
    >
      {/* 글로우 효과 */}
      {instance.status === 'running' && (
        <div className="instance-glow" style={{ background: `radial-gradient(circle at 50% 0%, ${typeColor}15 0%, transparent 70%)` }} />
      )}
      
      {/* 헤더 */}
      <div className="instance-header">
        <div className="instance-icon" style={{ color: typeColor }}>
          {getIcon()}
        </div>
        <div className="instance-title">
          <h3 className="instance-name">{instance.name}</h3>
          <span className="instance-type" style={{ color: typeColor }}>{instance.type}</span>
        </div>
        <div 
          className={`instance-status ${status.pulse ? 'pulse' : ''}`}
          style={{ background: status.bgColor, color: status.color }}
        >
          {status.icon}
          <span>{status.label}</span>
        </div>
      </div>

      {/* 메트릭스 */}
      <div className="instance-metrics">
        {instance.port && (
          <div className="metric-item">
            <span className="metric-label">Port</span>
            <span className="metric-value">:{instance.port}</span>
          </div>
        )}
        {instance.uptime && (
          <div className="metric-item">
            <Clock size={12} />
            <span className="metric-label">Uptime</span>
            <span className="metric-value">{instance.uptime}</span>
          </div>
        )}
        {instance.memoryUsageMb !== undefined && instance.memoryUsageMb !== null && (
          <div className="metric-item">
            <HardDrive size={12} />
            <span className="metric-label">Memory</span>
            <span className="metric-value">{instance.memoryUsageMb} MB</span>
          </div>
        )}
        {instance.cpuPercent !== undefined && instance.cpuPercent !== null && (
          <div className="metric-item">
            <Cpu size={12} />
            <span className="metric-label">CPU</span>
            <span className="metric-value">{instance.cpuPercent.toFixed(1)}%</span>
          </div>
        )}
      </div>

      {/* 버전 */}
      <div className="instance-version">
        <Sparkles size={12} />
        <span>{instance.version}</span>
      </div>

      {/* 피처 태그 */}
      {instance.features.length > 0 && (
        <div className="instance-features">
          {instance.features.slice(0, 4).map((feature, idx) => (
            <motion.span 
              key={feature} 
              className="feature-tag"
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ delay: 0.1 + idx * 0.05 }}
            >
              {feature}
            </motion.span>
          ))}
          {instance.features.length > 4 && (
            <span className="feature-tag more">+{instance.features.length - 4}</span>
          )}
        </div>
      )}

      {/* 헬스 엔드포인트 링크 */}
      {instance.healthEndpoint && instance.status === 'running' && (
        <a 
          href={`http://localhost:${instance.port}${instance.healthEndpoint}`}
          target="_blank"
          rel="noopener noreferrer"
          className="instance-health-link"
        >
          <ExternalLink size={12} />
          <span>Health Check</span>
        </a>
      )}
    </motion.div>
  )
}

// ==================== App Instances Section ====================
function AppInstancesSection({ instances }: { instances: AppInstance[] }) {
  const runningCount = instances.filter(i => i.status === 'running').length
  
  return (
    <motion.div 
      className="env-section instances-section"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
    >
      <div className="section-header-enhanced">
        <h2 className="section-title">
          <Zap size={20} />
          Running Instances
        </h2>
        <div className="instance-summary">
          <span className="summary-running">
            <span className="pulse-dot" />
            {runningCount} running
          </span>
          <span className="summary-total">/ {instances.length} total</span>
        </div>
      </div>
      
      <motion.div
        className="instances-grid"
        variants={staggerContainer}
        initial="hidden"
        animate="show"
      >
        {instances.map((instance, index) => (
          <AppInstanceCard key={instance.name} instance={instance} index={index} />
        ))}
      </motion.div>
    </motion.div>
  )
}

// ==================== Database Card ====================
function DatabaseCard({ db }: { db: DatabaseInfo }) {
  const getStatusIcon = () => {
    switch (db.status) {
      case 'connected':
        return <CheckCircle2 size={20} className="status-icon connected" />
      case 'disconnected':
        return <XCircle size={20} className="status-icon disconnected" />
      default:
        return <XCircle size={20} className="status-icon unknown" />
    }
  }

  const getIcon = () => {
    switch (db.type) {
      case 'PostgreSQL':
        return <Database size={24} />
      case 'DynamoDB':
        return <Cloud size={24} />
      case 'Kafka':
        return <Activity size={24} />
      default:
        return <Server size={24} />
    }
  }

  return (
    <motion.div className="env-card" variants={fadeInUp} whileHover={{ scale: 1.02 }}>
      <div className="env-card-header">
        <div className="env-card-icon">{getIcon()}</div>
        <div className="env-card-title">
          <span className="env-card-name">{db.name}</span>
          <span className="env-card-type">{db.type}</span>
        </div>
        {getStatusIcon()}
      </div>
      <div className="env-card-content">
        {db.host && (
          <div className="env-card-row">
            <span className="env-card-label">Host:</span>
            <span className="env-card-value">{db.host}</span>
          </div>
        )}
        {db.port && (
          <div className="env-card-row">
            <span className="env-card-label">Port:</span>
            <span className="env-card-value">{db.port}</span>
          </div>
        )}
        {db.database && (
          <div className="env-card-row">
            <span className="env-card-label">Database:</span>
            <span className="env-card-value">{db.database}</span>
          </div>
        )}
        {db.region && (
          <div className="env-card-row">
            <span className="env-card-label">Region:</span>
            <span className="env-card-value">{db.region}</span>
          </div>
        )}
        {db.latencyMs !== undefined && (
          <div className="env-card-row">
            <span className="env-card-label">Latency:</span>
            <span className="env-card-value">{db.latencyMs}ms</span>
          </div>
        )}
      </div>
    </motion.div>
  )
}

function ConfigSection({ config }: { config: EnvironmentResponse['config'] }) {
  return (
    <motion.div className="env-section" variants={fadeInUp}>
      <h2 className="section-title">
        <Settings size={20} />
        Environment Configuration
      </h2>
      <div className="config-grid">
        <div className="config-group">
          <h3 className="config-group-title">Database</h3>
          <div className="config-item">
            <span className="config-label">URL:</span>
            <span className="config-value">{config.database.url}</span>
          </div>
          <div className="config-item">
            <span className="config-label">User:</span>
            <span className="config-value">{config.database.user}</span>
          </div>
          <div className="config-item">
            <span className="config-label">Max Pool Size:</span>
            <span className="config-value">{config.database.maxPoolSize}</span>
          </div>
          <div className="config-item">
            <span className="config-label">Min Idle:</span>
            <span className="config-value">{config.database.minIdle}</span>
          </div>
        </div>

        <div className="config-group">
          <h3 className="config-group-title">DynamoDB</h3>
          <div className="config-item">
            <span className="config-label">Region:</span>
            <span className="config-value">{config.dynamodb.region}</span>
          </div>
          <div className="config-item">
            <span className="config-label">Table:</span>
            <span className="config-value">{config.dynamodb.tableName}</span>
          </div>
          {config.dynamodb.endpoint && (
            <div className="config-item">
              <span className="config-label">Endpoint:</span>
              <span className="config-value">{config.dynamodb.endpoint}</span>
            </div>
          )}
        </div>

        <div className="config-group">
          <h3 className="config-group-title">Kafka</h3>
          <div className="config-item">
            <span className="config-label">Bootstrap Servers:</span>
            <span className="config-value">{config.kafka.bootstrapServers}</span>
          </div>
          <div className="config-item">
            <span className="config-label">Consumer Group:</span>
            <span className="config-value">{config.kafka.consumerGroup}</span>
          </div>
          <div className="config-item">
            <span className="config-label">Topic Prefix:</span>
            <span className="config-value">{config.kafka.topicPrefix}</span>
          </div>
        </div>

        <div className="config-group">
          <h3 className="config-group-title">Observability</h3>
          <div className="config-item">
            <span className="config-label">Metrics Enabled:</span>
            <span className="config-value">{config.observability.metricsEnabled ? 'Yes' : 'No'}</span>
          </div>
          <div className="config-item">
            <span className="config-label">Tracing Enabled:</span>
            <span className="config-value">{config.observability.tracingEnabled ? 'Yes' : 'No'}</span>
          </div>
          <div className="config-item">
            <span className="config-label">OTLP Endpoint:</span>
            <span className="config-value">{config.observability.otlpEndpoint}</span>
          </div>
        </div>

        <div className="config-group">
          <h3 className="config-group-title">Worker</h3>
          <div className="config-item">
            <span className="config-label">Enabled:</span>
            <span className="config-value">{config.worker.enabled ? 'Yes' : 'No'}</span>
          </div>
          <div className="config-item">
            <span className="config-label">Poll Interval:</span>
            <span className="config-value">{config.worker.pollIntervalMs}ms</span>
          </div>
          <div className="config-item">
            <span className="config-label">Batch Size:</span>
            <span className="config-value">{config.worker.batchSize}</span>
          </div>
        </div>
      </div>
    </motion.div>
  )
}

function GitSection({ git }: { git: GitInfo }) {
  return (
    <motion.div className="env-section" variants={fadeInUp}>
      <h2 className="section-title">
        <GitBranch size={20} />
        Git Information
      </h2>
      <div className="git-info">
        <div className="git-row">
          <div className="git-item">
            <GitBranch size={16} />
            <span className="git-label">Branch:</span>
            <span className="git-value">{git.branch}</span>
          </div>
          {git.isDirty && (
            <span className="git-dirty-badge">Modified</span>
          )}
        </div>
        <div className="git-item">
          <GitCommit size={16} />
          <span className="git-label">Commit:</span>
          <span className="git-value git-commit">{git.commit.substring(0, 7)}</span>
        </div>
        <div className="git-item">
          <span className="git-label">Message:</span>
          <span className="git-value">{git.commitMessage}</span>
        </div>
        <div className="git-item">
          <User size={16} />
          <span className="git-label">Author:</span>
          <span className="git-value">{git.author}</span>
        </div>
        <div className="git-item">
          <Clock size={16} />
          <span className="git-label">Date:</span>
          <span className="git-value">{new Date(git.commitDate).toLocaleString('ko-KR')}</span>
        </div>
        {git.remoteUrl && (
          <div className="git-item">
            <LinkIcon size={16} />
            <span className="git-label">Remote:</span>
            <span className="git-value git-remote">{git.remoteUrl}</span>
          </div>
        )}
      </div>
    </motion.div>
  )
}

export function Environment() {
  const { environment } = useAppStore()
  
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['environment', environment],
    queryFn: () => fetchApi<EnvironmentResponse>(`/environment?env=${environment}`),
    refetchInterval: QUERY_CONFIG.DASHBOARD_INTERVAL,
    retry: 1,
  })

  if (isLoading) return <Loading />

  if (isError) {
    return (
      <div className="page-container">
        <PageHeader title="Environment" subtitle={`${environment} 환경의 설정 및 연결 정보를 확인합니다`} />
        <ApiError onRetry={() => refetch()} />
      </div>
    )
  }

  const databases = data?.databases ?? []
  const config = data?.config
  const git = data?.git
  const appInstances = data?.appInstances ?? []

  return (
    <div className="page-container">
      <PageHeader 
        title="Environment" 
        subtitle={`${environment} 환경의 설정 및 연결 정보를 확인합니다`}
      />

      {/* App Instances Section */}
      {appInstances.length > 0 && <AppInstancesSection instances={appInstances} />}

      {/* Databases Section */}
      <motion.div
        className="env-section"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <h2 className="section-title">
          <Database size={20} />
          Connected Databases
        </h2>
        <motion.div
          className="databases-grid"
          variants={staggerContainer}
          initial="hidden"
          animate="show"
        >
          {databases.map((db) => (
            <DatabaseCard key={db.name} db={db} />
          ))}
        </motion.div>
      </motion.div>

      {/* Configuration Section */}
      {config && <ConfigSection config={config} />}

      {/* Git Section */}
      {git && <GitSection git={git} />}

      {/* Timestamp */}
      {data?.timestamp && (
        <motion.div
          className="env-timestamp"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.4 }}
        >
          <span className="timestamp-text">
            Last updated: {new Date(data.timestamp).toLocaleString('ko-KR')}
          </span>
        </motion.div>
      )}
    </div>
  )
}
