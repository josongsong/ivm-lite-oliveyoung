import { memo } from 'react'
import { Layers } from 'lucide-react'
import { BaseNode } from './BaseNode'
import type { WorkflowNodeData } from '../../model/types'

interface SliceNodeProps {
  data: WorkflowNodeData
  selected?: boolean
}

export const SliceNode = memo(function SliceNode({ data, selected }: SliceNodeProps) {
  return <BaseNode data={data} selected={selected} nodeType="slice" icon={<Layers size={20} />} />
})
