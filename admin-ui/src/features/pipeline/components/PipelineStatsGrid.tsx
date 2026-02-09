import { forwardRef } from 'react'
import { motion } from 'framer-motion'
import { Database, Layers, Send } from 'lucide-react'
import type { PipelineOverviewResponse, RawDataDetailResponse, SliceDetailResponse } from '@/shared/types'

interface PipelineStatsGridProps {
  rawDataDetail: RawDataDetailResponse | undefined
  sliceDetail: SliceDetailResponse | undefined
  overview: PipelineOverviewResponse | undefined
  highlightedSlice: string | null
  onSliceClick: (sliceType: string | null) => void
}

function RawDataStatsCard({ rawDataDetail }: { rawDataDetail: RawDataDetailResponse | undefined }) {
  const bySchema = rawDataDetail?.stats?.bySchema
  const hasSchemas = bySchema && Object.keys(bySchema).length > 0

  return (
    <motion.div
      className="stats-card"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.4 }}
    >
      <div className="stats-card-header">
        <Database size={20} />
        <h3>Raw Data 통계</h3>
      </div>
      <div className="stats-content">
        <div className="stats-total">
          <span className="total-value">{rawDataDetail?.stats?.total?.toLocaleString() ?? 0}</span>
          <span className="total-label">Total Records</span>
        </div>
        {hasSchemas ? (
          <div className="stats-breakdown">
            <h4>By Schema</h4>
            {Object.entries(bySchema).map(([schema, count]) => (
              <div key={schema} className="breakdown-item">
                <span className="breakdown-key">{schema}</span>
                <span className="breakdown-value">{count.toLocaleString()}</span>
              </div>
            ))}
          </div>
        ) : null}
      </div>
    </motion.div>
  )
}

interface SliceStatsCardProps {
  sliceDetail: SliceDetailResponse | undefined
  highlightedSlice: string | null
  onSliceClick: (sliceType: string | null) => void
}

const SliceStatsCard = forwardRef<HTMLDivElement, SliceStatsCardProps>(
  ({ sliceDetail, highlightedSlice, onSliceClick }, ref) => {
    const hasTypes = sliceDetail?.byType && sliceDetail.byType.length > 0

    return (
      <motion.div
        ref={ref}
        className="stats-card"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.5 }}
      >
        <div className="stats-card-header">
          <Layers size={20} />
          <h3>Slice 통계</h3>
          {highlightedSlice ? <span className="highlight-badge">{highlightedSlice}</span> : null}
        </div>
        <div className="stats-content">
          <div className="stats-total">
            <span className="total-value">{sliceDetail?.stats?.total?.toLocaleString() ?? 0}</span>
            <span className="total-label">Total Slices</span>
          </div>
          {hasTypes ? (
            <div className="stats-breakdown">
              <h4>By Type</h4>
              {sliceDetail.byType.map((item) => (
                <div
                  key={item.type}
                  className={`breakdown-item ${highlightedSlice === item.type ? 'highlighted' : ''}`}
                  onClick={() => onSliceClick(highlightedSlice === item.type ? null : item.type)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      onSliceClick(highlightedSlice === item.type ? null : item.type)
                    }
                  }}
                  role="button"
                  tabIndex={0}
                >
                  <span className="breakdown-key">{item.type}</span>
                  <span className="breakdown-value">{item.count.toLocaleString()}</span>
                </div>
              ))}
            </div>
          ) : null}
        </div>
      </motion.div>
    )
  }
)
SliceStatsCard.displayName = 'SliceStatsCard'

function OutboxStatsCard({ overview }: { overview: PipelineOverviewResponse | undefined }) {
  return (
    <motion.div
      className="stats-card"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.6 }}
    >
      <div className="stats-card-header">
        <Send size={20} />
        <h3>Outbox 통계</h3>
      </div>
      <div className="stats-content">
        <div className="outbox-stats-grid">
          <OutboxStat label="Pending" value={overview?.outbox?.pending ?? 0} status="pending" />
          <OutboxStat label="Processing" value={overview?.outbox?.processing ?? 0} status="processing" />
          <OutboxStat label="Shipped" value={overview?.outbox?.shipped ?? 0} status="shipped" />
          <OutboxStat label="Failed" value={overview?.outbox?.failed ?? 0} status="failed" />
        </div>
      </div>
    </motion.div>
  )
}

export const PipelineStatsGrid = forwardRef<HTMLDivElement, PipelineStatsGridProps>(
  ({ rawDataDetail, sliceDetail, overview, highlightedSlice, onSliceClick }, ref) => {
    return (
      <div className="pipeline-stats-grid">
        <RawDataStatsCard rawDataDetail={rawDataDetail} />
        <SliceStatsCard
          ref={ref}
          sliceDetail={sliceDetail}
          highlightedSlice={highlightedSlice}
          onSliceClick={onSliceClick}
        />
        <OutboxStatsCard overview={overview} />
      </div>
    )
  }
)

PipelineStatsGrid.displayName = 'PipelineStatsGrid'

function OutboxStat({ label, value, status }: { label: string; value: number; status: string }) {
  return (
    <div className={`outbox-stat ${status}`}>
      <span className="stat-val">{value}</span>
      <span className="stat-lbl">{label}</span>
    </div>
  )
}
