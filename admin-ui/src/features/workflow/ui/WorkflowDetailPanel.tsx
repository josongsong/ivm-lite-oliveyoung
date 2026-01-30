import { AnimatePresence, motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import { useState } from 'react'
import {
  Activity,
  ArrowDownRight,
  ArrowUpRight,
  ChevronDown,
  Cloud,
  Database,
  ExternalLink,
  GitBranch,
  Hash,
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
  const [isFieldsExpanded, setIsFieldsExpanded] = useState(true)
  
  // 안전한 데이터 접근
  const nodeData = node?.data
  const status = nodeData?.status || 'inactive'
  const statusColor = STATUS_COLORS[status] || STATUS_COLORS.inactive
  const nodeType = node?.type || 'rawdata'
  const nodeColor = NODE_COLORS[nodeType] || NODE_COLORS.rawdata
  
  // 슬라이스 필드 정보 추출 (metadata에서) - 배열인지 확인
  const rawSchemaFields = nodeData?.metadata?.schemaFields
  const schemaFields = Array.isArray(rawSchemaFields) ? rawSchemaFields as string[] : []
  
  // node가 없으면 렌더링하지 않음
  if (!node || !nodeData) {
    return null
  }

  return (
    <motion.div
      className="detail-panel"
      initial={{ x: 320, opacity: 0 }}
      animate={{ x: 0, opacity: 1 }}
      exit={{ x: 320, opacity: 0 }}
      transition={{ 
        type: 'spring', 
        stiffness: 400, 
        damping: 35,
        mass: 0.8
      }}
    >
      {/* 헤더 */}
      <div className="panel-header" style={{ borderLeftColor: nodeColor?.border }}>
        <div className="panel-title-row">
          <span className="panel-icon" style={{ color: nodeColor?.border }}>
            {nodeIcons[node.type || 'rawdata']}
          </span>
          <div className="panel-title-info">
            <h3 className="panel-title">{nodeData.label}</h3>
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
            {nodeData.entityType && (
              <div className="info-item">
                <span className="info-label">Entity Type</span>
                <span className="info-value">{nodeData.entityType}</span>
              </div>
            )}
            <div className="info-item">
              <span className="info-label">상태</span>
              <span className="info-value status-value" style={{ color: statusColor }}>
                {status.toUpperCase()}
              </span>
            </div>
            {nodeData.contractId && (
              <div className="info-item">
                <span className="info-label">Contract ID</span>
                <span className="info-value mono">{nodeData.contractId}</span>
              </div>
            )}
          </div>
        </section>

        {/* 스키마 필드 (슬라이스 노드용) */}
        {nodeType === 'slice' && schemaFields.length > 0 && (
          <section className="panel-section schema-fields-section">
            <button 
              className="section-title-toggle"
              onClick={() => setIsFieldsExpanded(!isFieldsExpanded)}
            >
              <div className="section-title-left">
                <Hash size={14} />
                <span>스키마 필드</span>
                <span className="field-count">{schemaFields.length}</span>
              </div>
              <ChevronDown 
                size={14} 
                className={`chevron ${isFieldsExpanded ? 'expanded' : ''}`}
              />
            </button>
            <AnimatePresence initial={false}>
              {isFieldsExpanded && (
                <motion.div
                  initial={{ height: 0, opacity: 0 }}
                  animate={{ height: 'auto', opacity: 1 }}
                  exit={{ height: 0, opacity: 0 }}
                  transition={{ duration: 0.2, ease: 'easeInOut' }}
                  className="fields-container"
                >
                  <div className="fields-grid">
                    {schemaFields.map((field, index) => (
                      <motion.div
                        key={field}
                        initial={{ opacity: 0, x: -10 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: index * 0.03 }}
                        className={`field-tag ${field === '*' ? 'wildcard' : ''}`}
                      >
                        <span className="field-icon">
                          {field === '*' ? '✦' : '•'}
                        </span>
                        <span className="field-name">
                          {field === '*' ? 'All Fields' : field}
                        </span>
                      </motion.div>
                    ))}
                  </div>
                  {schemaFields.includes('*') && (
                    <div className="wildcard-hint">
                      <span>✦</span> 모든 필드가 슬라이스에 포함됩니다
                    </div>
                  )}
                </motion.div>
              )}
            </AnimatePresence>
          </section>
        )}

        {/* 통계 */}
        {nodeData.stats && (
          <section className="panel-section">
            <h4 className="section-title">
              <Activity size={14} />
              실시간 통계
            </h4>
            <div className="stats-grid">
              <div className="stat-card">
                <div className="stat-value">
                  {formatNumber(nodeData.stats.recordCount ?? 0)}
                </div>
                <div className="stat-label">Total Records</div>
              </div>
              <div className="stat-card">
                <div className="stat-value">
                  {typeof nodeData.stats.throughput === 'number' 
                    ? nodeData.stats.throughput.toFixed(1) 
                    : '0.0'}
                  <span className="stat-unit">/min</span>
                </div>
                <div className="stat-label">Throughput</div>
              </div>
              {typeof nodeData.stats.latencyP99Ms === 'number' && (
                <div className="stat-card">
                  <div className="stat-value">
                    {nodeData.stats.latencyP99Ms}
                    <span className="stat-unit">ms</span>
                  </div>
                  <div className="stat-label">P99 Latency</div>
                </div>
              )}
              <div className={`stat-card ${(nodeData.stats.errorCount ?? 0) > 0 ? 'error' : ''}`}>
                <div className="stat-value">
                  {nodeData.stats.errorCount ?? 0}
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
            {Array.isArray(detail.upstreamNodes) && detail.upstreamNodes.length > 0 && (
              <div className="connection-group">
                <div className="connection-label">
                  <ArrowDownRight size={12} />
                  Upstream ({detail.upstreamNodes.length})
                </div>
                <div className="connection-nodes">
                  {detail.upstreamNodes.map((nodeId, idx) => (
                    <span key={nodeId || `upstream-${idx}`} className="connection-tag">
                      {nodeId || 'Unknown'}
                    </span>
                  ))}
                </div>
              </div>
            )}
            {Array.isArray(detail.downstreamNodes) && detail.downstreamNodes.length > 0 && (
              <div className="connection-group">
                <div className="connection-label">
                  <ArrowUpRight size={12} />
                  Downstream ({detail.downstreamNodes.length})
                </div>
                <div className="connection-nodes">
                  {detail.downstreamNodes.map((nodeId, idx) => (
                    <span key={nodeId || `downstream-${idx}`} className="connection-tag">
                      {nodeId || 'Unknown'}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </section>
        )}

        {/* 관련 Contract */}
        {Array.isArray(detail?.relatedContracts) && detail.relatedContracts.length > 0 && (
          <section className="panel-section">
            <h4 className="section-title">관련 Contract</h4>
            <div className="contract-list">
              {detail.relatedContracts.map((contract, idx) => (
                <Link
                  key={contract?.id || `contract-${idx}`}
                  to={`/contracts/${contract?.kind || 'unknown'}/${encodeURIComponent(contract?.id || '')}`}
                  className="contract-link"
                >
                  <span className="contract-kind">{contract?.kind || 'Unknown'}</span>
                  <span className="contract-id">{contract?.id || 'N/A'}</span>
                  <ExternalLink size={12} />
                </Link>
              ))}
            </div>
          </section>
        )}

        {/* 최근 활동 */}
        {Array.isArray(detail?.recentActivity) && detail.recentActivity.length > 0 && (
          <section className="panel-section">
            <h4 className="section-title">최근 활동</h4>
            <div className="activity-list">
              {detail.recentActivity.slice(0, 5).map((activity, idx) => {
                if (!activity) return null
                return (
                  <div key={idx} className="activity-item">
                    <span className="activity-time">
                      {activity.timestamp ? formatTime(activity.timestamp) : 'N/A'}
                    </span>
                    <span className="activity-action">{activity.action || 'Unknown'}</span>
                    <span className="activity-details">{activity.details || ''}</span>
                  </div>
                )
              })}
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
        {nodeData.contractId && (
          <Link
            to={`/contracts`}
            className="action-btn primary"
          >
            <ExternalLink size={14} />
            Contract 보기
          </Link>
        )}
        <Link
          to={`/pipeline${nodeData.entityType ? `?entity=${nodeData.entityType}` : ''}`}
          className="action-btn secondary"
        >
          Pipeline 보기
        </Link>
      </div>
    </motion.div>
  )
}
