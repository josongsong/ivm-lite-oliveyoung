import { memo } from 'react'
import { Database } from 'lucide-react'
import { BaseNode } from './BaseNode'
import type { WorkflowNodeData } from '../../model/types'

interface RawDataNodeProps {
  data: WorkflowNodeData
  selected?: boolean
}

export const RawDataNode = memo(function RawDataNode({ data, selected }: RawDataNodeProps) {
  return (
    <BaseNode data={data} selected={selected} nodeType="rawdata" icon={<Database size={24} />}>
      {data.entityType && (
        <span className="entity-badge">{data.entityType}</span>
      )}
    </BaseNode>
  )
})
