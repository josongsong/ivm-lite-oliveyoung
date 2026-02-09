import { Panel } from '@xyflow/react'
import { NODE_COLORS, STATUS_COLORS } from '../model/constants'

const LEGEND_NODE_TYPES = [
  { type: 'rawdata', label: 'RawData' },
  { type: 'slice', label: 'Slice' },
  { type: 'view', label: 'View' },
  { type: 'sink', label: 'Sink' },
  { type: 'ruleset', label: 'Rule' },
]

export function WorkflowLegend() {
  return (
    <Panel position="bottom-left" className="workflow-legend">
      <div className="legend-section">
        <div className="legend-title">Node Types</div>
        <div className="legend-items">
          {LEGEND_NODE_TYPES.map(({ type, label }) => (
            <div key={type} className="legend-item">
              <span className="legend-dot" style={{ backgroundColor: NODE_COLORS[type]?.border }} />
              <span className="legend-label">{label}</span>
            </div>
          ))}
        </div>
      </div>
      <div className="legend-section">
        <div className="legend-title">Status</div>
        <div className="legend-items">
          {Object.entries(STATUS_COLORS).map(([status, color]) => (
            <div key={status} className="legend-item">
              <span className="legend-ring" style={{ borderColor: color }} />
              <span className="legend-label">{status}</span>
            </div>
          ))}
        </div>
      </div>
    </Panel>
  )
}
