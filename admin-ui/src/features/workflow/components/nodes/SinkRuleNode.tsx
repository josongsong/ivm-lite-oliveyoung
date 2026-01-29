import { memo } from 'react'
import { ArrowRight } from 'lucide-react'
import { BaseNode } from './BaseNode'
import type { WorkflowNodeData } from '../../model/types'

interface SinkRuleNodeProps {
  data: WorkflowNodeData
  selected?: boolean
}

export const SinkRuleNode = memo(function SinkRuleNode({ data, selected }: SinkRuleNodeProps) {
  return <BaseNode data={data} selected={selected} nodeType="sinkrule" icon={<ArrowRight size={14} />} />
})
