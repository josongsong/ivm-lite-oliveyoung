import { memo, type ReactNode } from 'react'
import { Handle, Position } from '@xyflow/react'
import { NODE_COLORS, NODE_DIMENSIONS, STATUS_COLORS } from '../../model/constants'
import type { WorkflowNodeData, WorkflowNodeType } from '../../model/types'
import './BaseNode.css'

interface BaseNodeProps {
  data: WorkflowNodeData
  selected?: boolean
  nodeType: WorkflowNodeType
  icon: ReactNode
  children?: ReactNode
}

function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toFixed(0)
}

export const BaseNode = memo(function BaseNode({
  data,
  nodeType,
  icon,
  selected,
  children
}: BaseNodeProps) {
  const colors = NODE_COLORS[nodeType]
  const statusColor = STATUS_COLORS[data.status]
  const dimensions = NODE_DIMENSIONS[nodeType]

  const isRuleNode = ['ruleset', 'viewdef', 'sinkrule'].includes(nodeType)

  return (
    <div
      className={`workflow-node ${nodeType} ${selected ? 'selected' : ''} status-${data.status}`}
      style={{
        width: dimensions.width,
        minHeight: dimensions.height,
        background: colors.bg,
        borderColor: selected ? statusColor : colors.border,
        boxShadow: selected
          ? `0 0 15px ${colors.glow}`
          : `0 0 8px ${colors.glow}`
      }}
    >
      <div
        className="node-status-indicator"
        style={{ backgroundColor: statusColor }}
      />

      <Handle
        type="target"
        position={Position.Top}
        className="node-handle"
      />

      <div className="node-content">
        <div className="node-icon">{icon}</div>
        <div className="node-label">{data.label}</div>
        {!isRuleNode && data.stats && (
          <div className="node-stats">
            <span className="stat-value">
              {formatNumber(data.stats.throughput)}/min
            </span>
          </div>
        )}
        {children}
      </div>

      <Handle
        type="source"
        position={Position.Bottom}
        className="node-handle"
      />
    </div>
  )
})
