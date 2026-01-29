import { motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import {
  Activity,
  ArrowDownRight,
  ArrowUpRight,
  Cloud,
  Database,
  ExternalLink,
  GitBranch,
  Layers,
  Monitor,
  X
} from 'lucide-react'
import type { NodeDetailResponse, WorkflowNode } from '../model/types'
import { NODE_COLORS, STATUS_COLORS } from '../model/constants'
import './WorkflowDetailPanel.css'

interface WorkflowDetailPanelProps {
  node: WorkflowNode
  detail?: NodeDetailResponse | null
  isLoading?: boolean
  onClose: () => void
}

function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toString()
}

function formatTime(timestamp: string): string {
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  if (diff < 60000) return '방금 전'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}분 전`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}시간 전`
  return date.toLocaleDateString('ko-KR')
}

const nodeIcons: Record<string, React.ReactNode> = {
  rawdata: <Database size={18} />,
  slice: <Layers size={18} />,
  view: <Monitor size={18} />,
  sink: <Cloud size={18} />,
  ruleset: <GitBranch size={18} />,
  viewdef: <GitBranch size={18} />,
  sinkrule: <GitBranch size={18} />
}

export function WorkflowDetailPanel({ node, detail, isLoading, onClose }: WorkflowDetailPanelProps) {
  const statusColor = STATUS_COLORS[node.data.status]
  const nodeColor = NODE_COLORS[node.type || 'rawdata']

  return (
    <motion.div
      className="detail-panel"
      initial={{ x: 400, opacity: 0 }}
      animate={{ x: 0, opacity: 1 }}
      exit={{ x: 400, opacity: 0 }}
      transition={{ type: 'spring', damping: 25 }}
    >
      {/* 헤더 */}
      <div className="panel-header" style={{ borderLeftColor: nodeColor?.border }}>
        <div className="panel-title-row">
          <span className="panel-icon" style={{ color: nodeColor?.border }}>
            {nodeIcons[node.type || 'rawdata']}
          </span>
          <div className="panel-title-info">
            <h3 className="panel-title">{node.data.label}</h3>
            <span className="panel-type">{node.type}</span>
          </div>
          <div
            className="panel-status-dot"
            style={{ backgroundColor: statusColor }}
          />
        </div>
        <button className="panel-close" onClick={onClose}>
          <X size={18} />
        </button>
      </div>

      {/* 메인 콘텐츠 */}
      <div className="panel-content">
        {/* 기본 정보 */}
        <section className="panel-section">
          <h4 className="section-title">기본 정보</h4>
          <div className="info-grid">
            {node.data.entityType && (
              <div className="info-item">
                <span className="info-label">Entity Type</span>
                <span className="info-value">{node.data.entityType}</span>
              </div>
            )}
            <div className="info-item">
              <span className="info-label">상태</span>
              <span className="info-value status-value" style={{ color: statusColor }}>
                {node.data.status.toUpperCase()}
              </span>
            </div>
            {node.data.contractId && (
              <div className="info-item">
                <span className="info-label">Contract ID</span>
                <span className="info-value mono">{node.data.contractId}</span>
              </div>
            )}
          </div>
        </section>

        {/* 통계 */}
        {node.data.stats && (
          <section className="panel-section">
            <h4 className="section-title">
              <Activity size={14} />
              실시간 통계
            </h4>
            <div className="stats-grid">
              <div className="stat-card">
                <div className="stat-value">
                  {formatNumber(node.data.stats.recordCount)}
                </div>
                <div className="stat-label">Total Records</div>
              </div>
              <div className="stat-card">
                <div className="stat-value">
                  {node.data.stats.throughput.toFixed(1)}
                  <span className="stat-unit">/min</span>
                </div>
                <div className="stat-label">Throughput</div>
              </div>
              {node.data.stats.latencyP99Ms !== undefined && (
                <div className="stat-card">
                  <div className="stat-value">
                    {node.data.stats.latencyP99Ms}
                    <span className="stat-unit">ms</span>
                  </div>
                  <div className="stat-label">P99 Latency</div>
                </div>
              )}
              <div className={`stat-card ${node.data.stats.errorCount > 0 ? 'error' : ''}`}>
                <div className="stat-value">
                  {node.data.stats.errorCount}
                </div>
                <div className="stat-label">Errors</div>
              </div>
            </div>
          </section>
        )}

        {/* 연결 */}
        {detail && (
          <section className="panel-section">
            <h4 className="section-title">연결된 노드</h4>
            {detail.upstreamNodes.length > 0 && (
              <div className="connection-group">
                <div className="connection-label">
                  <ArrowDownRight size={12} />
                  Upstream ({detail.upstreamNodes.length})
                </div>
                <div className="connection-nodes">
                  {detail.upstreamNodes.map(nodeId => (
                    <span key={nodeId} className="connection-tag">
                      {nodeId}
                    </span>
                  ))}
                </div>
              </div>
            )}
            {detail.downstreamNodes.length > 0 && (
              <div className="connection-group">
                <div className="connection-label">
                  <ArrowUpRight size={12} />
                  Downstream ({detail.downstreamNodes.length})
                </div>
                <div className="connection-nodes">
                  {detail.downstreamNodes.map(nodeId => (
                    <span key={nodeId} className="connection-tag">
                      {nodeId}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </section>
        )}

        {/* 관련 Contract */}
        {detail?.relatedContracts && detail.relatedContracts.length > 0 && (
          <section className="panel-section">
            <h4 className="section-title">관련 Contract</h4>
            <div className="contract-list">
              {detail.relatedContracts.map(contract => (
                <Link
                  key={contract.id}
                  to={`/contracts/${contract.kind}/${encodeURIComponent(contract.id)}`}
                  className="contract-link"
                >
                  <span className="contract-kind">{contract.kind}</span>
                  <span className="contract-id">{contract.id}</span>
                  <ExternalLink size={12} />
                </Link>
              ))}
            </div>
          </section>
        )}

        {/* 최근 활동 */}
        {detail?.recentActivity && detail.recentActivity.length > 0 && (
          <section className="panel-section">
            <h4 className="section-title">최근 활동</h4>
            <div className="activity-list">
              {detail.recentActivity.slice(0, 5).map((activity, idx) => (
                <div key={idx} className="activity-item">
                  <span className="activity-time">
                    {formatTime(activity.timestamp)}
                  </span>
                  <span className="activity-action">{activity.action}</span>
                  <span className="activity-details">{activity.details}</span>
                </div>
              ))}
            </div>
          </section>
        )}

        {isLoading && (
          <div className="panel-loading">
            <div className="loading-spinner" />
            <span>상세 정보 로딩 중...</span>
          </div>
        )}
      </div>

      {/* 액션 버튼 */}
      <div className="panel-actions">
        {node.data.contractId && (
          <Link
            to={`/contracts`}
            className="action-btn primary"
          >
            <ExternalLink size={14} />
            Contract 보기
          </Link>
        )}
        <Link
          to={`/pipeline${node.data.entityType ? `?entity=${node.data.entityType}` : ''}`}
          className="action-btn secondary"
        >
          Pipeline 보기
        </Link>
      </div>
    </motion.div>
  )
}
