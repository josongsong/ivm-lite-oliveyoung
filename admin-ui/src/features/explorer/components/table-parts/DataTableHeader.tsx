import { Database, Eye, Layers, Plus, RefreshCw, Search as SearchIcon } from 'lucide-react'
import { Button, IconButton } from '@/shared/ui'
import type { DataTableType } from './types'

interface DataTableHeaderProps {
  type: DataTableType
  count?: number
  searchFilter: string
  onSearchChange: (value: string) => void
  onRefresh: () => void
  onCreateNew?: () => void
}

const typeConfig = {
  rawdata: { icon: Database, label: 'RawData' },
  slices: { icon: Layers, label: 'Slices' },
  views: { icon: Eye, label: 'Views' },
}

export function DataTableHeader({
  type,
  count,
  searchFilter,
  onSearchChange,
  onRefresh,
  onCreateNew,
}: DataTableHeaderProps) {
  const { icon: Icon, label } = typeConfig[type]

  return (
    <div className="table-header">
      <div className="table-title">
        <Icon size={18} />
        <h3>{label}</h3>
        {count !== undefined && <span className="table-count">{count}</span>}
      </div>

      <div className="table-actions">
        <div className="search-filter">
          <SearchIcon size={14} />
          <input
            type="text"
            placeholder="Entity ID로 필터..."
            value={searchFilter}
            onChange={(e) => onSearchChange(e.target.value)}
          />
        </div>
        <IconButton icon={RefreshCw} iconSize={14} variant="ghost" size="sm" onClick={onRefresh} aria-label="Refresh" />
        {onCreateNew && type === 'rawdata' ? (
          <Button variant="primary" size="sm" onClick={onCreateNew}>
            <Plus size={14} />
            New
          </Button>
        ) : null}
      </div>
    </div>
  )
}
