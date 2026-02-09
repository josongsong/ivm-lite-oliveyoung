import { Cloud, Database, GitBranch, Layers, Monitor, X } from 'lucide-react'
import { IconButton } from '@/shared/ui'
import type { WorkflowNode, WorkflowNodeData } from '../../model/types'
import { NODE_COLORS } from '../../model/constants'

const NODE_ICONS: Record<string, React.ReactNode> = {
  rawdata: <Database size={18} />,
  slice: <Layers size={18} />,
  view: <Monitor size={18} />,
  sink: <Cloud size={18} />,
  ruleset: <GitBranch size={18} />,
  viewdef: <GitBranch size={18} />,
  sinkrule: <GitBranch size={18} />,
}

interface DetailPanelHeaderProps {
  node: WorkflowNode
  nodeData: WorkflowNodeData
  statusColor: string
  onClose: () => void
}

export function DetailPanelHeader({ node, nodeData, statusColor, onClose }: DetailPanelHeaderProps) {
  const nodeType = node?.type || 'rawdata'
  const nodeColor = NODE_COLORS[nodeType] || NODE_COLORS.rawdata

  return (
    <div className="panel-header" style={{ borderLeftColor: nodeColor?.border }}>
      <div className="panel-title-row">
        <span className="panel-icon" style={{ color: nodeColor?.border }}>
          {NODE_ICONS[node.type || 'rawdata']}
        </span>
        <div className="panel-title-info">
          <h3 className="panel-title">{nodeData.label}</h3>
          <span className="panel-type">{node.type}</span>
        </div>
        <div className="panel-status-dot" style={{ backgroundColor: statusColor }} />
      </div>
      <IconButton icon={X} iconSize={18} variant="ghost" className="panel-close" onClick={onClose} aria-label="Close" />
    </div>
  )
}
