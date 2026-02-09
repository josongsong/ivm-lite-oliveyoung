import { AlertCircle, AlertTriangle, CheckCircle, Minus } from 'lucide-react'
import { PageHeader } from '@/shared/ui'
import type { GraphMetadata } from '../model/types'

interface WorkflowHeaderProps {
  metadata?: GraphMetadata
}

export function WorkflowHeader({ metadata }: WorkflowHeaderProps) {
  return (
    <div className="workflow-header">
      <PageHeader title="Workflow Canvas" subtitle="데이터 파이프라인을 시각적으로 탐색합니다" />

      {metadata ? (
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
      ) : null}
    </div>
  )
}
