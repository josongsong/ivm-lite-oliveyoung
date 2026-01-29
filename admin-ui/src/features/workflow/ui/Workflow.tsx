import { useCallback, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import {
  AlertCircle,
  AlertTriangle,
  CheckCircle,
  Filter,
  GitMerge,
  Minus,
  RefreshCw
} from 'lucide-react'
import { fetchApi } from '@/shared/api'
import { Loading, PageHeader } from '@/shared/ui'
import { WorkflowCanvas } from './WorkflowCanvas'
import { WorkflowDetailPanel } from './WorkflowDetailPanel'
import type { NodeDetailResponse, WorkflowGraphResponse, WorkflowNode } from '../model/types'
import './Workflow.css'

export function Workflow() {
  const [selectedNode, setSelectedNode] = useState<WorkflowNode | null>(null)
  const [entityFilter, setEntityFilter] = useState<string | null>(null)

  // 워크플로우 그래프 조회 (BE API)
  const { data, isLoading, refetch, isFetching } = useQuery({
    queryKey: ['workflow-graph', entityFilter],
    queryFn: async () => {
      const params = entityFilter ? `?entityType=${entityFilter}` : ''
      return fetchApi<WorkflowGraphResponse>(`/workflow/graph${params}`)
    },
    refetchInterval: 30000
  })

  // 노드 상세 정보 조회 (BE API)
  const { data: nodeDetail, isLoading: isDetailLoading } = useQuery({
    queryKey: ['workflow-node', selectedNode?.id],
    queryFn: async () => {
      if (!selectedNode) return null
      return fetchApi<NodeDetailResponse>(`/workflow/nodes/${selectedNode.id}`)
    },
    enabled: !!selectedNode
  })

  const handleNodeClick = useCallback((node: WorkflowNode) => {
    setSelectedNode(node)
  }, [])

  const handleClosePanel = useCallback(() => {
    setSelectedNode(null)
  }, [])

  if (isLoading) return <Loading />

  const { nodes = [], edges = [], metadata } = data ?? {}

  return (
    <div className="workflow-page">
      {/* 헤더 */}
      <div className="workflow-header">
        <PageHeader
          title="Workflow Canvas"
          subtitle="데이터 파이프라인을 시각적으로 탐색합니다"
        />

        {/* 상태 요약 */}
        {metadata && (
          <div className="health-summary">
            <div className="health-item healthy">
              <CheckCircle size={14} />
              <span>{metadata.healthSummary.healthy}</span>
            </div>
            <div className="health-item warning">
              <AlertTriangle size={14} />
              <span>{metadata.healthSummary.warning}</span>
            </div>
            <div className="health-item error">
              <AlertCircle size={14} />
              <span>{metadata.healthSummary.error}</span>
            </div>
            <div className="health-item inactive">
              <Minus size={14} />
              <span>{metadata.healthSummary.inactive}</span>
            </div>
          </div>
        )}
      </div>

      {/* 툴바 */}
      <motion.div
        className="workflow-toolbar"
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
      >
        {/* 엔티티 필터 */}
        <div className="filter-group">
          <Filter size={14} />
          <select
            value={entityFilter ?? ''}
            onChange={(e) => setEntityFilter(e.target.value || null)}
            className="entity-select"
          >
            <option value="">전체 엔티티</option>
            {metadata?.entityTypes.map(type => (
              <option key={type} value={type}>{type}</option>
            ))}
          </select>
        </div>

        {/* 액션 버튼 */}
        <div className="action-group">
          <button
            className="toolbar-btn"
            onClick={() => refetch()}
            disabled={isFetching}
          >
            <RefreshCw size={14} className={isFetching ? 'spinning' : ''} />
            새로고침
          </button>
        </div>

        {/* 통계 */}
        <div className="stats-group">
          <span className="stat">
            <GitMerge size={12} />
            {metadata?.totalNodes ?? 0} nodes
          </span>
          <span className="stat">
            {metadata?.totalEdges ?? 0} edges
          </span>
        </div>
      </motion.div>

      {/* 캔버스 영역 */}
      <div className="workflow-content">
        <motion.div
          className="canvas-container"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.1 }}
        >
          <WorkflowCanvas
            nodes={nodes}
            edges={edges}
            onNodeClick={handleNodeClick}
            selectedNodeId={selectedNode?.id}
          />
        </motion.div>

        {/* 상세 패널 */}
        <AnimatePresence>
          {selectedNode && (
            <WorkflowDetailPanel
              node={selectedNode}
              detail={nodeDetail}
              isLoading={isDetailLoading}
              onClose={handleClosePanel}
            />
          )}
        </AnimatePresence>
      </div>
    </div>
  )
}
