import { memo } from 'react'
import { Eye } from 'lucide-react'
import { BaseNode } from './BaseNode'
import type { WorkflowNodeData } from '../../model/types'

interface ViewDefNodeProps {
  data: WorkflowNodeData
  selected?: boolean
}

export const ViewDefNode = memo(function ViewDefNode({ data, selected }: ViewDefNodeProps) {
  return <BaseNode data={data} selected={selected} nodeType="viewdef" icon={<Eye size={14} />} />
})
