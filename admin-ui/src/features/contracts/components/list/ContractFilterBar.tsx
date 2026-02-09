import { motion } from 'framer-motion'
import {
  ArrowRight,
  Database,
  Eye,
  FileCode2,
  GitBranch,
  Grid3X3,
  Layers,
  Search
} from 'lucide-react'
import { IconButton, Tabs, TabsList, TabsTrigger } from '@/shared/ui'

type ViewMode = 'grid' | 'graph'

const contractTabs = [
  { key: 'all', label: '전체', icon: FileCode2 },
  { key: 'ENTITY_SCHEMA', label: 'Schema', icon: Database },
  { key: 'RULESET', label: 'RuleSet', icon: Layers },
  { key: 'VIEW_DEFINITION', label: 'View', icon: Eye },
  { key: 'SINKRULE', label: 'Sink', icon: ArrowRight },
]

interface ContractFilterBarProps {
  searchTerm: string
  selectedKind: string
  viewMode: ViewMode
  onSearchChange: (value: string) => void
  onKindChange: (kind: string) => void
  onViewModeChange: (mode: ViewMode) => void
}

export function ContractFilterBar({
  searchTerm,
  selectedKind,
  viewMode,
  onSearchChange,
  onKindChange,
  onViewModeChange,
}: ContractFilterBarProps) {
  return (
    <motion.div
      className="filter-bar"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.1 }}
    >
      <div className="search-box">
        <Search size={16} />
        <input
          type="text"
          placeholder="Search contracts..."
          value={searchTerm}
          onChange={(e) => onSearchChange(e.target.value)}
        />
      </div>

      <div className="filter-bar-right">
        {viewMode === 'grid' && (
          <Tabs value={selectedKind} onValueChange={onKindChange}>
            <TabsList className="kind-tabs compact">
              {contractTabs.map(({ key, label, icon: Icon }) => (
                <TabsTrigger key={key} value={key} icon={<Icon size={14} />}>
                  {label}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
        )}

        <div className="view-toggle">
          <IconButton
            icon={Grid3X3}
            size="sm"
            variant={viewMode === 'grid' ? 'primary' : 'ghost'}
            onClick={() => onViewModeChange('grid')}
            tooltip="Grid View"
          />
          <IconButton
            icon={GitBranch}
            size="sm"
            variant={viewMode === 'graph' ? 'primary' : 'ghost'}
            onClick={() => onViewModeChange('graph')}
            tooltip="Dependency Graph"
          />
        </div>
      </div>
    </motion.div>
  )
}
