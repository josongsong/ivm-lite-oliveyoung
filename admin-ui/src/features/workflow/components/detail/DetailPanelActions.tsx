import { Link } from 'react-router-dom'
import { ExternalLink } from 'lucide-react'
import type { WorkflowNodeData } from '../../model/types'

interface DetailPanelActionsProps {
  nodeData: WorkflowNodeData
}

export function DetailPanelActions({ nodeData }: DetailPanelActionsProps) {
  return (
    <div className="panel-actions">
      {nodeData.contractId ? (
        <Link to="/contracts" className="action-btn primary">
          <ExternalLink size={14} />
          Contract 보기
        </Link>
      ) : null}
      <Link
        to={`/pipeline${nodeData.entityType ? `?entity=${encodeURIComponent(nodeData.entityType)}` : ''}`}
        className="action-btn secondary"
      >
        Pipeline 보기
      </Link>
    </div>
  )
}
