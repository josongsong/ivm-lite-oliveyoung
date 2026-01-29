import { memo } from 'react'
import { Monitor } from 'lucide-react'
import { BaseNode } from './BaseNode'
import type { WorkflowNodeData } from '../../model/types'

interface ViewNodeProps {
  data: WorkflowNodeData
  selected?: boolean
}

export const ViewNode = memo(function ViewNode({ data, selected }: ViewNodeProps) {
  return <BaseNode data={data} selected={selected} nodeType="view" icon={<Monitor size={20} />} />
})
