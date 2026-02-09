import { useMemo } from 'react'
import { formatDuration } from '@/shared/ui/formatters'

interface TimelineScaleProps {
  totalDuration: number
}

export function TimelineScale({ totalDuration }: TimelineScaleProps) {
  const scaleMarkers = useMemo(() => {
    if (totalDuration <= 0) return []

    const markerCount = 5
    return Array.from({ length: markerCount + 1 }, (_, i) => {
      const percent = (i / markerCount) * 100
      const time = (totalDuration * i) / markerCount
      return { percent, time }
    })
  }, [totalDuration])

  return (
    <div className="timeline-scale">
      <div className="scale-label-area" />
      <div className="scale-track">
        {scaleMarkers.map(({ percent, time }) => (
          <div key={percent} className="scale-marker" style={{ left: `${percent}%` }}>
            <span className="scale-time">{formatDuration(time)}</span>
            <div className="scale-line" />
          </div>
        ))}
      </div>
    </div>
  )
}
