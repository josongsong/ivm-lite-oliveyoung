/**
 * ContractNode Component (Phase 4: Impact Graph)
 *
 * Impact Graph용 Contract 노드.
 */
import { memo } from 'react'
import { Handle, type NodeProps, Position } from '@xyflow/react'
import type { ContractNodeData } from './types'
import './ContractNode.css'

type ContractNodeProps = NodeProps & {
  data: ContractNodeData
}

export const ContractNode = memo(function ContractNode(props: ContractNodeProps) {
  const { data, selected } = props
  const kindClass = `contract-node--${data.kind.toLowerCase().replace('_', '-')}`
  const impactClass = data.impact ? `contract-node--${data.impact}` : ''

  return (
    <div
      className={`contract-node ${kindClass} ${impactClass} ${selected ? 'contract-node--selected' : ''}`}
    >
      <Handle type="target" position={Position.Top} />
      <div className="contract-node__kind">{formatKind(data.kind)}</div>
      <div className="contract-node__id" title={data.id}>
        {truncateId(data.id)}
      </div>
      {data.impact ? <div className="contract-node__impact-badge">
          {data.impact === 'changed' ? 'CHANGED' : 'AFFECTED'}
        </div> : null}
      <Handle type="source" position={Position.Bottom} />
    </div>
  )
})

function formatKind(kind: string): string {
  const mapping: Record<string, string> = {
    ENTITY_SCHEMA: 'Schema',
    RULESET: 'RuleSet',
    VIEW_DEFINITION: 'View',
    SINKRULE: 'Sink',
    JOIN_SPEC: 'Join',
    CHANGESET: 'ChangeSet',
  }
  return mapping[kind] || kind
}

function truncateId(id: string, maxLength = 20): string {
  if (id.length <= maxLength) return id
  return `${id.slice(0, maxLength - 3)}...`
}
