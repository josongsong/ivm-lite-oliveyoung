import { AnimatePresence, motion } from 'framer-motion'
import { RefreshCw, Search } from 'lucide-react'
import type { SpanDetail, TraceDetail } from '@/shared/types'
import { EmptyState } from '@/shared/ui'
import { SpanDetails } from './SpanDetails'
import { WaterfallTimeline } from './WaterfallTimeline'

interface TraceDetailPanelProps {
  selectedTrace: string | null
  traceDetail: TraceDetail | undefined
  loadingDetail: boolean
  selectedSpan: string | null
  selectedSpanData: SpanDetail | null
  onSpanSelect: (spanId: string) => void
  onCloseSpan: () => void
}

export function TraceDetailPanel({
  selectedTrace,
  traceDetail,
  loadingDetail,
  selectedSpan,
  selectedSpanData,
  onSpanSelect,
  onCloseSpan,
}: TraceDetailPanelProps) {
  return (
    <AnimatePresence mode="wait">
      {selectedTrace && traceDetail ? (
        <motion.div
          key={selectedTrace}
          className="detail-content"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
        >
          <div className="trace-header">
            <div className="trace-header-left">
              <span
                className={`trace-status ${traceDetail.segments?.some((s) => s.hasError) ? 'error' : 'success'}`}
              />
              <span className="trace-id-label">Trace</span>
              <span className="trace-id-value">{selectedTrace}</span>
            </div>
            <div className="trace-header-right">
              <span className="trace-duration-label">Duration</span>
              <span className="trace-duration-value">{traceDetail.duration.toFixed(2)}ms</span>
            </div>
          </div>

          <div className="timeline-container">
            {loadingDetail ? (
              <div className="loading-overlay">
                <RefreshCw size={24} className="spinning" />
              </div>
            ) : (
              <WaterfallTimeline
                trace={traceDetail}
                selectedSpanId={selectedSpan}
                onSpanSelect={onSpanSelect}
              />
            )}
          </div>

          <AnimatePresence>
            {selectedSpanData ? <SpanDetails span={selectedSpanData} onClose={onCloseSpan} /> : null}
          </AnimatePresence>
        </motion.div>
      ) : (
        <EmptyState
          icon={<Search size={40} />}
          title="트레이스를 선택하세요"
          description="왼쪽 목록에서 트레이스를 클릭하면 상세 워터폴 타임라인을 볼 수 있습니다"
          variant="card"
        />
      )}
    </AnimatePresence>
  )
}
