import { motion } from 'framer-motion'
import { Filter, GitMerge, RefreshCw } from 'lucide-react'
import { Button, Select } from '@/shared/ui'
import type { GraphMetadata } from '../model/types'

interface WorkflowToolbarProps {
  metadata?: GraphMetadata
  entityFilter: string | null
  onEntityFilterChange: (filter: string | null) => void
  onRefresh: () => void
  isFetching: boolean
}

export function WorkflowToolbar({
  metadata,
  entityFilter,
  onEntityFilterChange,
  onRefresh,
  isFetching,
}: WorkflowToolbarProps) {
  return (
    <motion.div className="workflow-toolbar" initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}>
      <div className="filter-group">
        <Select
          value={entityFilter ?? ''}
          onChange={(v) => onEntityFilterChange(v || null)}
          options={[
            { value: '', label: '전체 엔티티' },
            ...(metadata?.entityTypes.map((type) => ({
              value: type,
              label: type,
            })) ?? []),
          ]}
          size="sm"
          icon={<Filter size={14} />}
          className="entity-select"
        />
      </div>

      <div className="action-group">
        <Button variant="secondary" size="sm" onClick={onRefresh} disabled={isFetching}>
          <RefreshCw size={14} className={isFetching ? 'spinning' : ''} />
          새로고침
        </Button>
      </div>

      <div className="stats-group">
        <span className="stat">
          <GitMerge size={12} />
          {metadata?.totalNodes ?? 0} nodes
        </span>
        <span className="stat">{metadata?.totalEdges ?? 0} edges</span>
      </div>
    </motion.div>
  )
}
