import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import { 
  Activity, 
  AlertTriangle, 
  Clock, 
  RefreshCw,
  Search,
  TrendingUp,
  Zap
} from 'lucide-react'
import { tracesApi } from '@/shared/api'
import type { TraceFilterOptions } from '@/shared/types'
import { ApiError, fadeInUp, Loading, PageHeader, staggerContainer } from '@/shared/ui'
import { TraceList } from '../components/TraceList'
import { TraceFilters } from '../components/TraceFilters'
import { WaterfallTimeline } from '../components/WaterfallTimeline'
import { SpanDetails } from '../components/SpanDetails'
import './TracesPage.css'

export function TracesPage() {
  const [selectedTrace, setSelectedTrace] = useState<string | null>(null)
  const [selectedSpan, setSelectedSpan] = useState<string | null>(null)
  const [filters, setFilters] = useState<TraceFilterOptions>({
    limit: 100,
  })

  const { data, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: ['traces', filters],
    queryFn: () => tracesApi.getTraces(filters),
    refetchInterval: 30000,
    retry: 1,
  })

  const { data: traceDetail, isLoading: loadingDetail } = useQuery({
    queryKey: ['trace-detail', selectedTrace],
    queryFn: () => tracesApi.getTraceDetail(selectedTrace!),
    enabled: !!selectedTrace,
  })

  // 통계 계산
  const stats = useMemo(() => {
    if (!data?.traces) return null
    
    const traces = data.traces
    const totalCount = traces.length
    const errorCount = traces.filter(t => t.hasError || t.hasFault).length
    const throttleCount = traces.filter(t => t.hasThrottle).length
    const avgDuration = traces.reduce((sum, t) => sum + t.duration, 0) / (totalCount || 1)
    const p99Duration = traces.length > 0 
      ? [...traces].sort((a, b) => b.duration - a.duration)[Math.floor(traces.length * 0.01)]?.duration || 0
      : 0
    
    return {
      totalCount,
      errorCount,
      throttleCount,
      avgDuration,
      p99Duration,
      errorRate: totalCount > 0 ? (errorCount / totalCount) * 100 : 0
    }
  }, [data?.traces])

  const handleTraceSelect = (traceId: string) => {
    setSelectedTrace(traceId)
    setSelectedSpan(null)
  }

  const handleSpanSelect = (spanId: string) => {
    setSelectedSpan(spanId)
  }

  const handleFilterChange = (newFilters: TraceFilterOptions) => {
    setFilters(newFilters)
    setSelectedTrace(null)
    setSelectedSpan(null)
  }

  const selectedSpanData = useMemo(() => {
    if (!selectedSpan || !traceDetail?.segments) return null
    return traceDetail.segments.find(s => s.spanId === selectedSpan)
  }, [selectedSpan, traceDetail])

  if (isLoading) return <Loading />

  if (isError) {
    return (
      <div className="traces-page">
        <PageHeader title="Distributed Tracing" subtitle="실시간 트레이스 분석 및 성능 모니터링" />
        <ApiError onRetry={() => refetch()} />
      </div>
    )
  }

  return (
    <div className="traces-page">
      <PageHeader
        title="Distributed Tracing"
        subtitle="실시간 트레이스 분석 및 성능 모니터링"
      />

      {/* 통계 카드 */}
      {stats && (
        <motion.div 
          className="traces-stats"
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
        >
          <div className="stat-card">
            <div className="stat-icon">
              <Activity size={18} />
            </div>
            <div className="stat-content">
              <span className="stat-value">{stats.totalCount.toLocaleString()}</span>
              <span className="stat-label">Total Traces</span>
            </div>
          </div>
          
          <div className="stat-card error">
            <div className="stat-icon">
              <AlertTriangle size={18} />
            </div>
            <div className="stat-content">
              <span className="stat-value">{stats.errorCount}</span>
              <span className="stat-label">Errors ({stats.errorRate.toFixed(1)}%)</span>
            </div>
          </div>
          
          <div className="stat-card warning">
            <div className="stat-icon">
              <Zap size={18} />
            </div>
            <div className="stat-content">
              <span className="stat-value">{stats.throttleCount}</span>
              <span className="stat-label">Throttled</span>
            </div>
          </div>
          
          <div className="stat-card">
            <div className="stat-icon">
              <Clock size={18} />
            </div>
            <div className="stat-content">
              <span className="stat-value">{stats.avgDuration.toFixed(1)}ms</span>
              <span className="stat-label">Avg Duration</span>
            </div>
          </div>
          
          <div className="stat-card accent">
            <div className="stat-icon">
              <TrendingUp size={18} />
            </div>
            <div className="stat-content">
              <span className="stat-value">{stats.p99Duration.toFixed(1)}ms</span>
              <span className="stat-label">P99 Latency</span>
            </div>
          </div>
        </motion.div>
      )}

      {/* 필터 섹션 */}
      <motion.div
        className="traces-filters"
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
      >
        <TraceFilters
          filters={filters}
          onChange={handleFilterChange}
          onRefresh={() => refetch()}
        />
        {isFetching && (
          <div className="refresh-indicator">
            <RefreshCw size={14} className="spinning" />
            <span>새로고침 중...</span>
          </div>
        )}
      </motion.div>

      {/* 메인 레이아웃 */}
      <motion.div
        className="traces-layout"
        variants={staggerContainer}
        initial="hidden"
        animate="show"
      >
        {/* 왼쪽: 트레이스 목록 */}
        <motion.div variants={fadeInUp} className="traces-list-panel">
          <div className="panel-header">
            <h3 className="panel-title">
              <Clock size={16} />
              Recent Traces
            </h3>
            <span className="panel-count">
              {data?.approximateCount?.toLocaleString() ?? 0}
            </span>
          </div>
          <TraceList
            traces={data?.traces ?? []}
            selectedTraceId={selectedTrace}
            onTraceSelect={handleTraceSelect}
            onLoadMore={() => {
              if (data?.nextToken) {
                setFilters({ ...filters, nextToken: data.nextToken })
              }
            }}
            hasMore={!!data?.nextToken}
          />
        </motion.div>

        {/* 오른쪽: 타임라인 + 상세 */}
        <motion.div variants={fadeInUp} className="traces-detail-panel">
          <AnimatePresence mode="wait">
            {selectedTrace && traceDetail ? (
              <motion.div 
                key={selectedTrace}
                className="detail-content"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
              >
                {/* 트레이스 헤더 */}
                <div className="trace-header">
                  <div className="trace-header-left">
                    <span className={`trace-status ${traceDetail.segments?.some(s => s.hasError) ? 'error' : 'success'}`} />
                    <span className="trace-id-label">Trace</span>
                    <span className="trace-id-value">{selectedTrace}</span>
                  </div>
                  <div className="trace-header-right">
                    <span className="trace-duration-label">Duration</span>
                    <span className="trace-duration-value">
                      {traceDetail.duration.toFixed(2)}ms
                    </span>
                  </div>
                </div>

                {/* Waterfall Timeline */}
                <div className="timeline-container">
                  {loadingDetail ? (
                    <div className="loading-overlay">
                      <RefreshCw size={24} className="spinning" />
                    </div>
                  ) : (
                    <WaterfallTimeline
                      trace={traceDetail}
                      selectedSpanId={selectedSpan}
                      onSpanSelect={handleSpanSelect}
                    />
                  )}
                </div>

                {/* Span Details */}
                <AnimatePresence>
                  {selectedSpanData && (
                    <SpanDetails
                      span={selectedSpanData}
                      onClose={() => setSelectedSpan(null)}
                    />
                  )}
                </AnimatePresence>
              </motion.div>
            ) : (
              <motion.div 
                className="empty-state"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
              >
                <div className="empty-state-icon-wrapper">
                  <Search size={40} />
                </div>
                <p className="empty-state-title">트레이스를 선택하세요</p>
                <p className="empty-state-description">
                  왼쪽 목록에서 트레이스를 클릭하면<br />
                  상세 워터폴 타임라인을 볼 수 있습니다
                </p>
              </motion.div>
            )}
          </AnimatePresence>
        </motion.div>
      </motion.div>
    </div>
  )
}
