import type { NodeDetailResponse, NodeStats, WorkflowNodeData } from '../../model/types'
import { BasicInfoSection } from './BasicInfoSection'
import { ConnectionsSection } from './ConnectionsSection'
import { RecentActivitySection } from './RecentActivitySection'
import { RelatedContractsSection } from './RelatedContractsSection'
import { SchemaFieldsSection } from './SchemaFieldsSection'
import { StatsSection } from './StatsSection'

interface DetailPanelContentProps {
  nodeData: WorkflowNodeData
  nodeType: string
  status: string
  statusColor: string
  detail?: NodeDetailResponse | null
  isLoading?: boolean
}

export function DetailPanelContent({
  nodeData,
  nodeType,
  status,
  statusColor,
  detail,
  isLoading,
}: DetailPanelContentProps) {
  const rawSchemaFields = nodeData?.metadata?.schemaFields
  const schemaFields = Array.isArray(rawSchemaFields) ? (rawSchemaFields as string[]) : []

  return (
    <div className="panel-content">
      <BasicInfoSection nodeData={nodeData} status={status} statusColor={statusColor} />

      {nodeType === 'slice' && <SchemaFieldsSection schemaFields={schemaFields} />}

      {nodeData.stats ? <StatsSection stats={nodeData.stats as NodeStats} /> : null}

      {detail ? <ConnectionsSection detail={detail} /> : null}

      {Array.isArray(detail?.relatedContracts) && detail.relatedContracts.length > 0 && (
        <RelatedContractsSection contracts={detail.relatedContracts} />
      )}

      {Array.isArray(detail?.recentActivity) && detail.recentActivity.length > 0 && (
        <RecentActivitySection activities={detail.recentActivity} />
      )}

      {isLoading ? (
        <div className="panel-loading">
          <div className="loading-spinner" />
          <span>상세 정보 로딩 중...</span>
        </div>
      ) : null}
    </div>
  )
}
