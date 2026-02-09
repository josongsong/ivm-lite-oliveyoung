import { Panel } from '@xyflow/react'

const LEGEND_ITEMS = [
  { color: '#ef4444', label: 'Changed' },
  { color: '#f59e0b', label: 'Affected' },
  { color: '#64748b', label: 'Normal' },
]

export function GraphLegend() {
  return (
    <Panel position="bottom-left" className="impact-graph__legend">
      {LEGEND_ITEMS.map(({ color, label }) => (
        <div key={label} className="impact-graph__legend-item">
          <span className="impact-graph__legend-color" style={{ background: color }} />
          <span>{label}</span>
        </div>
      ))}
    </Panel>
  )
}
