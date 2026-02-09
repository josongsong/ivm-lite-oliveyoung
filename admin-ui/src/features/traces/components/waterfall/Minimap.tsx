import type { NormalizedSpan } from './spanUtils'
import { getSpanColor } from './spanUtils'

interface MinimapProps {
  spans: NormalizedSpan[]
  totalDuration: number
  selectedSpanId: string | null
  onSpanSelect: (spanId: string) => void
}

export function Minimap({ spans, totalDuration, selectedSpanId, onSpanSelect }: MinimapProps) {
  return (
    <div className="waterfall-minimap">
      {spans.map((span) => {
        const color = getSpanColor(span)
        const left = (span.relativeStart / totalDuration) * 100
        const width = Math.max(
          ((span.relativeEnd - span.relativeStart) / totalDuration) * 100,
          0.5
        )

        return (
          <div
            role="button"
            tabIndex={0}
            key={span.spanId}
            className={`minimap-bar ${selectedSpanId === span.spanId ? 'selected' : ''}`}
            style={{
              left: `${left}%`,
              width: `${width}%`,
              backgroundColor: color.border,
            }}
            onClick={() => onSpanSelect(span.spanId)}
            onKeyDown={(e) => e.key === 'Enter' && onSpanSelect(span.spanId)}
          />
        )
      })}
    </div>
  )
}
