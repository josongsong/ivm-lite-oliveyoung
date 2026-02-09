import type { WorkflowNodeData } from '../../model/types'
import { InfoRow } from '@/shared/ui'

interface BasicInfoSectionProps {
  nodeData: WorkflowNodeData
  status: string
  statusColor: string
}

export function BasicInfoSection({ nodeData, status, statusColor }: BasicInfoSectionProps) {
  return (
    <section className="panel-section">
      <h4 className="section-title">기본 정보</h4>
      <div className="info-grid">
        {nodeData.entityType ? <InfoRow label="Entity Type" value={nodeData.entityType} /> : null}
        <InfoRow
          label="상태"
          value={status.toUpperCase()}
          badge
          badgeColor={statusColor}
        />
        {nodeData.contractId ? (
          <InfoRow label="Contract ID" value={nodeData.contractId} mono copyable />
        ) : null}
      </div>
    </section>
  )
}
