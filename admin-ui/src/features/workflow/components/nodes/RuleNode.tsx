import { memo } from 'react'
import { ArrowRight, Eye, GitBranch } from 'lucide-react'
import { BaseNode } from './BaseNode'
import type { WorkflowNodeData, WorkflowNodeType } from '../../model/types'

interface RuleNodeProps {
  data: WorkflowNodeData
  selected?: boolean
  type?: string
}

const iconMap: Record<string, React.ReactNode> = {
  ruleset: <GitBranch size={14} />,
  viewdef: <Eye size={14} />,
  sinkrule: <ArrowRight size={14} />
}

export const RuleNode = memo(function RuleNode({ data, selected, type }: RuleNodeProps) {
  const nodeType = (type || 'ruleset') as WorkflowNodeType
  return <BaseNode data={data} selected={selected} nodeType={nodeType} icon={iconMap[nodeType] || <GitBranch size={14} />} />
})
