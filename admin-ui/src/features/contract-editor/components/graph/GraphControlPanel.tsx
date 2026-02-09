import { Panel } from '@xyflow/react'
import { Button } from '@/shared/ui'

export type FilterMode = 'all' | 'affected'
export type DepthLevel = 1 | 2 | 3 | 'all'

interface GraphControlPanelProps {
  depth: DepthLevel
  filterMode: FilterMode
  onDepthChange: (depth: DepthLevel) => void
  onFilterModeChange: (mode: FilterMode) => void
}

export function GraphControlPanel({
  depth,
  filterMode,
  onDepthChange,
  onFilterModeChange,
}: GraphControlPanelProps) {
  return (
    <Panel position="top-left" className="impact-graph__panel">
      <div className="impact-graph__controls">
        <div className="impact-graph__control-group">
          <span className="impact-graph__control-label">Depth:</span>
          <div className="impact-graph__button-group">
            {([1, 2, 3, 'all'] as DepthLevel[]).map((d) => (
              <Button
                key={String(d)}
                variant={depth === d ? 'primary' : 'ghost'}
                size="sm"
                onClick={() => onDepthChange(d)}
              >
                {d === 'all' ? 'All' : d}
              </Button>
            ))}
          </div>
        </div>
        <div className="impact-graph__control-group">
          <span className="impact-graph__control-label">Filter:</span>
          <div className="impact-graph__button-group">
            <Button
              variant={filterMode === 'all' ? 'primary' : 'ghost'}
              size="sm"
              onClick={() => onFilterModeChange('all')}
            >
              All
            </Button>
            <Button
              variant={filterMode === 'affected' ? 'primary' : 'ghost'}
              size="sm"
              onClick={() => onFilterModeChange('affected')}
            >
              Affected
            </Button>
          </div>
        </div>
      </div>
    </Panel>
  )
}
