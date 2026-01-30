import { useMemo } from 'react'
import { motion } from 'framer-motion'
import {
  AlertCircle,
  ArrowRight,
  Database,
  Eye,
  FileCode2,
  Layers,
  Send
} from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { explorerApi } from '@/shared/api'
import type { LineageNode, LineageNodeType } from '@/shared/types'
import { Loading } from '@/shared/ui'
import './LineageGraph.css'

interface LineageGraphProps {
  tenant: string
  entityId: string
}

const nodeIcons: Record<LineageNodeType, React.ElementType> = {
  rawdata: Database,
  ruleset: FileCode2,
  slice: Layers,
  viewdef: FileCode2,
  view: Eye,
  sink: Send,
}

const nodeColors: Record<LineageNodeType, string> = {
  rawdata: 'cyan',
  ruleset: 'orange',
  slice: 'purple',
  viewdef: 'orange',
  view: 'green',
  sink: 'pink',
}

export function LineageGraph({ tenant, entityId }: LineageGraphProps) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['lineage', tenant, entityId],
    queryFn: () => explorerApi.getLineage(tenant, entityId),
    enabled: !!tenant && !!entityId,
  })

  // 노드를 레벨별로 그룹핑
  const levels = useMemo(() => {
    if (!data?.nodes) return []

    const typeOrder: LineageNodeType[] = ['rawdata', 'ruleset', 'slice', 'viewdef', 'view', 'sink']
    const grouped = new Map<LineageNodeType, LineageNode[]>()

    for (const node of data.nodes) {
      const existing = grouped.get(node.type) || []
      grouped.set(node.type, [...existing, node])
    }

    return typeOrder
      .filter(type => grouped.has(type))
      .map(type => ({
        type,
        nodes: grouped.get(type)!,
      }))
  }, [data?.nodes])

  if (isLoading) return <Loading />

  if (error) {
    return (
      <div className="lineage-graph error">
        <AlertCircle size={32} />
        <p>Lineage를 불러오는 중 오류가 발생했습니다.</p>
      </div>
    )
  }

  if (!data?.nodes.length) {
    return (
      <div className="lineage-graph empty">
        <Database size={48} />
        <p>Lineage 데이터가 없습니다.</p>
      </div>
    )
  }

  return (
    <div className="lineage-graph">
      <div className="lineage-header">
        <div className="lineage-entity">
          <Database size={16} />
          <span>{entityId}</span>
        </div>
        <div className="lineage-legend">
          {Object.entries(nodeColors).map(([type, color]) => (
            <div key={type} className="legend-item">
              <span className={`legend-dot ${color}`} />
              <span>{type}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="lineage-flow">
        {levels.map((level, levelIdx) => (
          <div key={level.type} className="lineage-level">
            <div className="level-label">{level.type}</div>
            <div className="level-nodes">
              {level.nodes.map((node, nodeIdx) => {
                const Icon = nodeIcons[node.type]
                const color = nodeColors[node.type]
                return (
                  <motion.div
                    key={node.id}
                    className={`lineage-node ${color} ${node.status}`}
                    initial={{ opacity: 0, scale: 0.8 }}
                    animate={{ opacity: 1, scale: 1 }}
                    transition={{ delay: levelIdx * 0.1 + nodeIdx * 0.05 }}
                  >
                    <div className="node-icon">
                      <Icon size={18} />
                    </div>
                    <div className="node-content">
                      <span className="node-label">{node.label}</span>
                      {node.version && (
                        <span className="node-version">v{node.version}</span>
                      )}
                    </div>
                    <span className={`node-status ${node.status}`} />
                  </motion.div>
                )
              })}
            </div>

            {/* 화살표 (마지막 레벨 제외) */}
            {levelIdx < levels.length - 1 && (
              <div className="level-arrow">
                <ArrowRight size={20} />
              </div>
            )}
          </div>
        ))}
      </div>

      {/* 엣지 정보 */}
      {data.edges.length > 0 && (
        <div className="lineage-edges">
          <span className="edges-label">Transformations:</span>
          {data.edges.map((edge, idx) => (
            <span key={idx} className="edge-item">
              {edge.source.split('-').pop()} → {edge.target.split('-').pop()}
              {edge.label && <span className="edge-label">({edge.label})</span>}
            </span>
          ))}
        </div>
      )}
    </div>
  )
}
