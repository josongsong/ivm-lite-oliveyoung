import { motion } from 'framer-motion'
import { AlertTriangle, CheckCircle2 } from 'lucide-react'
import type { TraceSummary } from '@/shared/types'
import { formatDuration, formatTime } from '@/shared/ui/formatters'
import './TraceList.css'

interface TraceListProps {
  traces: TraceSummary[]
  selectedTraceId: string | null
  onTraceSelect: (traceId: string) => void
  onLoadMore: () => void
  hasMore: boolean
}

export function TraceList({
  traces,
  selectedTraceId,
  onTraceSelect,
  onLoadMore,
  hasMore,
}: TraceListProps) {
  const getStatusIcon = (trace: TraceSummary) => {
    if (trace.hasError || trace.hasFault) {
      return <AlertTriangle size={16} className="status-icon error" />
    }
    if (trace.hasThrottle) {
      return <AlertTriangle size={16} className="status-icon warning" />
    }
    return <CheckCircle2 size={16} className="status-icon success" />
  }

  const getStatusColor = (trace: TraceSummary) => {
    if (trace.hasError || trace.hasFault) return 'var(--status-error)'
    if (trace.hasThrottle) return 'var(--status-warning)'
    return 'var(--status-success)'
  }

  return (
    <div className="trace-list">
      {traces.length === 0 ? (
        <div className="empty-state">
          <p className="empty-state-text">트레이스가 없습니다</p>
        </div>
      ) : (
        <>
          {traces.map((trace, index) => (
            <motion.div
              key={trace.traceId}
              className={`trace-item ${selectedTraceId === trace.traceId ? 'selected' : ''}`}
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: index * 0.02 }}
              onClick={() => onTraceSelect(trace.traceId)}
            >
              <div className="trace-item-header">
                {getStatusIcon(trace)}
                <span className="trace-id-short">{trace.traceId.substring(0, 8)}...</span>
                <span className="trace-time">{formatTime(trace.startTime)}</span>
              </div>

              <div className="trace-item-body">
                <div className="trace-operation">
                  {trace.http?.method || 'Unknown'} {trace.http?.url || trace.serviceIds[0] || 'N/A'}
                </div>
                <div className="trace-services">
                  {trace.serviceIds.slice(0, 3).map((service, serviceIndex) => (
                    <span key={`${trace.traceId}-service-${serviceIndex}`} className="service-chip">
                      {service}
                    </span>
                  ))}
                  {trace.serviceIds.length > 3 && (
                    <span className="service-chip-more">+{trace.serviceIds.length - 3}</span>
                  )}
                </div>
              </div>

              <div className="trace-item-footer">
                <div className="duration-bar-container">
                  <div
                    className="duration-bar"
                    style={{
                      width: `${Math.min((trace.duration / 1000) * 100, 100)}%`,
                      backgroundColor: getStatusColor(trace),
                    }}
                  />
                </div>
                <span className="trace-duration">{formatDuration(trace.duration)}</span>
              </div>
            </motion.div>
          ))}

          {hasMore && (
            <button className="load-more-btn" onClick={onLoadMore}>
              더 보기
            </button>
          )}
        </>
      )}
    </div>
  )
}
