import { memo } from 'react'
import { Cloud } from 'lucide-react'
import { BaseNode } from './BaseNode'
import type { WorkflowNodeData } from '../../model/types'

interface SinkNodeProps {
  data: WorkflowNodeData
  selected?: boolean
}

export const SinkNode = memo(function SinkNode({ data, selected }: SinkNodeProps) {
  return <BaseNode data={data} selected={selected} nodeType="sink" icon={<Cloud size={20} />} />
})
