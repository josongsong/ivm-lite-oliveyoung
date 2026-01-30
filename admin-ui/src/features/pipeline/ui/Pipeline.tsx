import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import { 
  ArrowRight, 
  Database, 
  Eye, 
  Layers, 
  Rocket,
  Scissors,
  Search,
  Send
} from 'lucide-react'
import { fetchApi } from '@/shared/api'
import type {
  EntityFlowResponse,
  PipelineOverviewResponse,
  RawDataDetailResponse,
  SliceDetailResponse,
} from '@/shared/types'
import { ApiError, Loading, PageHeader } from '@/shared/ui'
import './Pipeline.css'

// Stage별 아이콘 매핑
const stageIcons: Record<string, React.ReactNode> = {
  'RawData': <Database size={20} />,
  'Slicing': <Scissors size={20} />,
  'View': <Eye size={20} />,
  'Sink': <Rocket size={20} />,
}

export function Pipeline() {
  const [searchParams] = useSearchParams()
  const [searchKey, setSearchKey] = useState('')
  const [selectedStage, setSelectedStage] = useState<string | null>(null)
  const [highlightedSlice, setHighlightedSlice] = useState<string | null>(null)
  const sliceStatsRef = useRef<HTMLDivElement>(null)

  // URL 파라미터에서 slice 타입 읽기
  useEffect(() => {
    const sliceParam = searchParams.get('slice')
    if (sliceParam) {
      setHighlightedSlice(sliceParam.toUpperCase())
      // 슬라이스 섹션으로 스크롤
      setTimeout(() => {
        sliceStatsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      }, 300)
    }
  }, [searchParams])

  const { data: overview, isLoading, isError, refetch } = useQuery({
    queryKey: ['pipeline-overview'],
    queryFn: () => fetchApi<PipelineOverviewResponse>('/pipeline/overview'),
    retry: 1,
  })

  const { data: rawDataDetail } = useQuery({
    queryKey: ['pipeline-rawdata'],
    queryFn: () => fetchApi<RawDataDetailResponse>('/pipeline/rawdata'),
  })

  const { data: sliceDetail } = useQuery({
    queryKey: ['pipeline-slices'],
    queryFn: () => fetchApi<SliceDetailResponse>('/pipeline/slices'),
  })

  const { data: entityFlow, refetch: searchEntity } = useQuery({
    queryKey: ['entity-flow', searchKey],
    queryFn: () => fetchApi<EntityFlowResponse>(`/pipeline/flow/${encodeURIComponent(searchKey)}`),
    enabled: searchKey.length > 0,
  })

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    if (searchKey) {
      searchEntity()
    }
  }

  if (isLoading) return <Loading />

  if (isError) {
    return (
      <div className="page-container">
        <PageHeader title="Pipeline" subtitle="데이터 흐름을 시각화하고 각 단계를 모니터링합니다" />
        <ApiError onRetry={refetch} />
      </div>
    )
  }

  const stages = overview?.stages ?? []

  return (
    <div className="page-container">
      <PageHeader title="Pipeline" subtitle="데이터 흐름을 시각화하고 각 단계를 모니터링합니다" />

      {/* Pipeline Flow Visualization */}
      <motion.div 
        className="pipeline-visualization"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <div className="pipeline-stages">
          {stages.map((stage, index) => (
            <motion.div
              key={stage.name}
              className={`pipeline-stage-card ${selectedStage === stage.name ? 'active' : ''}`}
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: index * 0.1 }}
              onClick={() => setSelectedStage(selectedStage === stage.name ? null : stage.name)}
            >
              <div className="stage-header">
                <span className="stage-icon">{stageIcons[stage.name] || <Database size={20} />}</span>
                <span className={`stage-status ${stage.status.toLowerCase()}`}>
                  {stage.status}
                </span>
              </div>
              
              <h3 className="stage-title">{stage.name}</h3>
              <p className="stage-desc">{stage.description}</p>
              
              <div className="stage-count-box">
                <motion.span 
                  className="stage-count"
                  key={stage.count}
                  initial={{ scale: 1.2 }}
                  animate={{ scale: 1 }}
                >
                  {stage.count.toLocaleString()}
                </motion.span>
                <span className="stage-label">
                  {stage.name === 'View' ? 'definitions' : 'records'}
                </span>
              </div>

              {index < stages.length - 1 && (
                <div className="stage-arrow">
                  <motion.div
                    animate={{ x: [0, 5, 0] }}
                    transition={{ repeat: Infinity, duration: 1.5 }}
                  >
                    <ArrowRight size={24} />
                  </motion.div>
                </div>
              )}
            </motion.div>
          ))}
        </div>
      </motion.div>

      {/* Entity Search */}
      <motion.div 
        className="entity-search-section"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.3 }}
      >
        <h2 className="section-title">Entity Flow 추적</h2>
        <form className="entity-search-form" onSubmit={handleSearch}>
          <div className="search-input-wrapper">
            <Search size={18} />
            <input
              type="text"
              placeholder={`Entity Key를 입력하세요 (예: ${rawDataDetail?.recent?.[0]?.entityKey ?? sliceDetail?.recent?.[0]?.entityKey ?? 'PRODUCT:SKU-001'})`}
              value={searchKey}
              onChange={(e) => setSearchKey(e.target.value)}
            />
          </div>
          <button type="submit" className="btn btn-primary">
            추적
          </button>
        </form>

        {/* Entity Flow Results */}
        {entityFlow && (
          <motion.div 
            className="entity-flow-results"
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
          >
            <h3 className="flow-title">
              <span className="flow-key">{entityFlow.entityKey}</span>
              의 데이터 흐름
            </h3>

            <div className="flow-timeline">
              {/* Raw Data */}
              <div className="flow-section">
                <div className="flow-section-header">
                  <Database size={20} />
                  <span>Raw Data</span>
                  <span className="flow-count">{entityFlow.rawData.length}</span>
                </div>
                {entityFlow.rawData.length > 0 ? (
                  <div className="flow-items">
                    {entityFlow.rawData.map((item, i) => (
                      <div key={i} className="flow-item">
                        <span className="item-version">v{item.version}</span>
                        <span className="item-schema">{item.schemaId}</span>
                        <span className="item-time">{item.createdAt ? new Date(item.createdAt).toLocaleString('ko-KR') : '-'}</span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="flow-empty">데이터 없음</div>
                )}
              </div>

              <div className="flow-connector">
                <ArrowRight size={20} />
              </div>

              {/* Slices */}
              <div className="flow-section">
                <div className="flow-section-header">
                  <Layers size={20} />
                  <span>Slices</span>
                  <span className="flow-count">{entityFlow.slices.length}</span>
                </div>
                {entityFlow.slices.length > 0 ? (
                  <div className="flow-items">
                    {entityFlow.slices.map((item, i) => (
                      <div key={i} className="flow-item">
                        <span className="item-type badge-info">{item.sliceType}</span>
                        <span className="item-version">v{item.version}</span>
                        <span className="item-hash mono">{item.hash.slice(0, 12)}...</span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="flow-empty">슬라이스 없음</div>
                )}
              </div>

              <div className="flow-connector">
                <ArrowRight size={20} />
              </div>

              {/* Outbox */}
              <div className="flow-section">
                <div className="flow-section-header">
                  <Send size={20} />
                  <span>Outbox</span>
                  <span className="flow-count">{entityFlow.outbox.length}</span>
                </div>
                {entityFlow.outbox.length > 0 ? (
                  <div className="flow-items">
                    {entityFlow.outbox.map((item, i) => (
                      <div key={i} className="flow-item">
                        <span className={`item-status badge-${item.status.toLowerCase()}`}>{item.status}</span>
                        <span className="item-event">{item.eventType}</span>
                        <span className="item-time">{item.createdAt ? new Date(item.createdAt).toLocaleString('ko-KR') : '-'}</span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="flow-empty">Outbox 엔트리 없음</div>
                )}
              </div>
            </div>
          </motion.div>
        )}
      </motion.div>

      {/* Stats Details */}
      <div className="pipeline-stats-grid">
        {/* Raw Data Stats */}
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
            {rawDataDetail?.stats?.bySchema && Object.keys(rawDataDetail.stats.bySchema).length > 0 && (
              <div className="stats-breakdown">
                <h4>By Schema</h4>
                {Object.entries(rawDataDetail.stats.bySchema).map(([schema, count]) => (
                  <div key={schema} className="breakdown-item">
                    <span className="breakdown-key">{schema}</span>
                    <span className="breakdown-value">{count.toLocaleString()}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </motion.div>

        {/* Slice Stats */}
        <motion.div 
          ref={sliceStatsRef}
          className="stats-card"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.5 }}
        >
          <div className="stats-card-header">
            <Layers size={20} />
            <h3>Slice 통계</h3>
            {highlightedSlice && (
              <span className="highlight-badge">{highlightedSlice}</span>
            )}
          </div>
          <div className="stats-content">
            <div className="stats-total">
              <span className="total-value">{sliceDetail?.stats?.total?.toLocaleString() ?? 0}</span>
              <span className="total-label">Total Slices</span>
            </div>
            {sliceDetail?.byType && sliceDetail.byType.length > 0 && (
              <div className="stats-breakdown">
                <h4>By Type</h4>
                {sliceDetail.byType.map((item) => (
                  <div 
                    key={item.type} 
                    className={`breakdown-item ${highlightedSlice === item.type ? 'highlighted' : ''}`}
                    onClick={() => setHighlightedSlice(highlightedSlice === item.type ? null : item.type)}
                  >
                    <span className="breakdown-key">{item.type}</span>
                    <span className="breakdown-value">{item.count.toLocaleString()}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </motion.div>

        {/* Outbox Stats */}
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
              <div className="outbox-stat pending">
                <span className="stat-val">{overview?.outbox?.pending ?? 0}</span>
                <span className="stat-lbl">Pending</span>
              </div>
              <div className="outbox-stat processing">
                <span className="stat-val">{overview?.outbox?.processing ?? 0}</span>
                <span className="stat-lbl">Processing</span>
              </div>
              <div className="outbox-stat shipped">
                <span className="stat-val">{overview?.outbox?.shipped ?? 0}</span>
                <span className="stat-lbl">Shipped</span>
              </div>
              <div className="outbox-stat failed">
                <span className="stat-val">{overview?.outbox?.failed ?? 0}</span>
                <span className="stat-lbl">Failed</span>
              </div>
            </div>
          </div>
        </motion.div>
      </div>

      {/* Recent Items */}
      <motion.div 
        className="recent-section"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.7 }}
      >
        <h2 className="section-title">최근 Slice</h2>
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>Entity Key</th>
                <th>Slice Type</th>
                <th>Version</th>
                <th>Hash</th>
                <th>Created At</th>
              </tr>
            </thead>
            <tbody>
              {sliceDetail?.recent?.map((slice, i) => (
                <tr key={i}>
                  <td className="mono truncate" style={{ maxWidth: '200px' }}>{slice.entityKey}</td>
                  <td><span className="badge badge-info">{slice.sliceType}</span></td>
                  <td className="mono">v{slice.version}</td>
                  <td className="mono text-muted">{slice.hash.slice(0, 16)}...</td>
                  <td className="text-secondary">{slice.createdAt ? new Date(slice.createdAt).toLocaleString('ko-KR') : '-'}</td>
                </tr>
              ))}
              {(!sliceDetail?.recent || sliceDetail.recent.length === 0) && (
                <tr>
                  <td colSpan={5} className="text-muted" style={{ textAlign: 'center', padding: '2rem' }}>
                    데이터가 없습니다
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </motion.div>
    </div>
  )
}
