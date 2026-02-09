import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import {
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  Clock,
  Database,
  Eye,
  Filter,
  GitBranch,
  Layers,
  Plus,
  RefreshCw,
  Search,
  X
} from 'lucide-react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import type { ExplorerTab } from '@/shared/types'
import { Loading, Tabs, TabsList, TabsTrigger } from '@/shared/ui'
import { explorerApi } from '@/shared/api'
import {
  RawDataEditor,
  RawDataPanel,
  SliceList,
  ViewPreview,
} from '../components'
import { LineageGraph } from '@/shared/ui'
import './DataExplorer.css'

interface SearchState {
  tenant: string
  entityId: string
  version?: number | 'latest'
}

type ListType = 'rawdata' | 'slices' | 'views'

const DEFAULT_TENANT = 'oliveyoung'

export function DataExplorer() {
  const queryClient = useQueryClient()
  const { entityId: urlEntityId } = useParams<{ entityId?: string }>()
  const navigate = useNavigate()

  // 뷰 모드: list (목록) | detail (상세)
  const [viewMode, setViewMode] = useState<'list' | 'detail'>('list')
  const [activeTab, setActiveTab] = useState<ExplorerTab>('rawdata')
  const [searchState, setSearchState] = useState<SearchState | null>(null)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [showCreateMode, setShowCreateMode] = useState(false)

  // URL entityId 변경 시 상세 뷰로 이동
  useEffect(() => {
    if (urlEntityId) {
      setSearchState({ tenant: DEFAULT_TENANT, entityId: urlEntityId })
      setViewMode('detail')
      setActiveTab('rawdata')
    }
  }, [urlEntityId])

  // 목록 상태
  const [listType, setListType] = useState<ListType>('rawdata')
  const [searchFilter, setSearchFilter] = useState('')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [selectedSliceType, setSelectedSliceType] = useState<string>('')

  // RawData 목록 조회
  const { data: rawDataList, isLoading: loadingRaw, refetch: refetchRaw } = useQuery({
    queryKey: ['rawdata-list', DEFAULT_TENANT, searchFilter, page, pageSize],
    queryFn: () => explorerApi.listRawData(DEFAULT_TENANT, searchFilter || undefined, pageSize),
    enabled: viewMode === 'list' && listType === 'rawdata',
  })

  // 슬라이스 타입 목록 조회
  const { data: sliceTypes } = useQuery({
    queryKey: ['slice-types', DEFAULT_TENANT],
    queryFn: () => explorerApi.getSliceTypes(DEFAULT_TENANT),
    enabled: viewMode === 'list' && listType === 'slices',
  })

  // 슬라이스 타입이 선택되면 첫 번째 타입으로 설정
  useEffect(() => {
    if (sliceTypes?.types?.length && !selectedSliceType) {
      setSelectedSliceType(sliceTypes.types[0].type)
    }
  }, [sliceTypes, selectedSliceType])

  // Slice 목록 조회 (타입별)
  const { data: sliceList, isLoading: loadingSlices, refetch: refetchSlices } = useQuery({
    queryKey: ['slices-list', DEFAULT_TENANT, selectedSliceType, page, pageSize],
    queryFn: () => explorerApi.listSlicesByType(DEFAULT_TENANT, selectedSliceType, pageSize),
    enabled: viewMode === 'list' && listType === 'slices' && !!selectedSliceType,
  })

  // ViewDefinition 목록 조회
  const { data: viewDefList, isLoading: loadingViews, refetch: refetchViews } = useQuery({
    queryKey: ['views-list'],
    queryFn: () => explorerApi.getViewDefinitions(),
    enabled: viewMode === 'list' && listType === 'views',
  })

  const isLoading = listType === 'rawdata' ? loadingRaw : listType === 'slices' ? loadingSlices : loadingViews
  const totalItems = listType === 'views' ? (viewDefList?.total || 0) : (listType === 'rawdata' ? rawDataList : sliceList)?.total || 0
  const totalPages = Math.ceil(totalItems / pageSize)

  const handleSearch = useCallback((entityId: string) => {
    if (!entityId.trim()) return
    navigate(`/explorer/${encodeURIComponent(entityId.trim())}`)
  }, [navigate])

  const handleSelectItem = (entityId: string, type: ListType) => {
    navigate(`/explorer/${encodeURIComponent(entityId)}`)
    setSearchState({ tenant: DEFAULT_TENANT, entityId })
    if (type === 'rawdata') setActiveTab('rawdata')
    else if (type === 'slices') setActiveTab('slices')
    else setActiveTab('view')
    setViewMode('detail')
  }

  const handleBackToList = () => {
    navigate('/explorer')
    setViewMode('list')
    setSearchState(null)
    setShowCreateMode(false)
  }

  const handleRefresh = async () => {
    setIsRefreshing(true)
    if (viewMode === 'list') {
      if (listType === 'rawdata') await refetchRaw()
      else if (listType === 'slices') await refetchSlices()
      else await refetchViews()
    } else {
      await queryClient.invalidateQueries({
        predicate: (query) => ['rawdata', 'slices', 'view', 'lineage'].includes(query.queryKey[0] as string),
      })
    }
    setIsRefreshing(false)
  }

  const renderDetailContent = () => {
    if (!searchState) return null

    switch (activeTab) {
      case 'rawdata':
        return <RawDataPanel tenant={searchState.tenant} entityId={searchState.entityId} initialVersion={searchState.version} />
      case 'slices':
        return <SliceList tenant={searchState.tenant} entityId={searchState.entityId} />
      case 'view':
        return <ViewPreview tenant={searchState.tenant} entityId={searchState.entityId} />
      case 'lineage':
        return <LineageGraph tenant={searchState.tenant} entityId={searchState.entityId} />
      case 'create':
        return (
          <RawDataEditor
            defaultTenant={searchState.tenant}
            onSuccess={(entityId) => {
              setSearchState({ tenant: searchState.tenant, entityId })
              setActiveTab('rawdata')
            }}
          />
        )
      default:
        return null
    }
  }

  return (
    <div className="data-explorer-v2">
      {/* 상단 GNB */}
      <div className="explorer-gnb">
        {/* 좌측: 뒤로가기 또는 타입 탭 */}
        <div className="gnb-left">
          {viewMode === 'detail' ? (
            <button className="back-btn" onClick={handleBackToList}>
              <ChevronLeft size={16} />
              <span>목록</span>
            </button>
          ) : (
            <Tabs value={listType} onValueChange={(v) => { setListType(v as ListType); setPage(1) }}>
              <TabsList variant="segmented" responsive="iconsOnlyMd">
                <TabsTrigger value="rawdata" icon={<Database size={16} />}>RawData</TabsTrigger>
                <TabsTrigger value="slices" icon={<Layers size={16} />}>Slices</TabsTrigger>
                <TabsTrigger value="views" icon={<Eye size={16} />}>Views</TabsTrigger>
              </TabsList>
            </Tabs>
          )}
        </div>

        {/* 중앙: 검색 또는 엔티티 정보 */}
        <div className="gnb-center">
          {viewMode === 'detail' && searchState ? (
            <div className="current-entity-bar">
              <div className="entity-tabs">
                {[
                  { id: 'rawdata', label: 'RawData', icon: Database },
                  { id: 'slices', label: 'Slices', icon: Layers },
                  { id: 'view', label: 'View', icon: Eye },
                  { id: 'lineage', label: 'Lineage', icon: GitBranch },
                ].map(({ id, label, icon: Icon }) => (
                  <button
                    key={id}
                    className={`entity-tab ${activeTab === id ? 'active' : ''}`}
                    onClick={() => setActiveTab(id as ExplorerTab)}
                  >
                    <Icon size={14} />
                    <span>{label}</span>
                  </button>
                ))}
              </div>
              <div className="entity-info">
                <span className="entity-id">{searchState.entityId}</span>
                {searchState.version && <span className="entity-version">v{searchState.version}</span>}
              </div>
            </div>
          ) : (
            <div className="search-bar-inline">
              <Search size={16} className="search-icon" />
              <input
                type="text"
                placeholder="Entity ID 검색... (예: SKU-12345)"
                value={searchFilter}
                onChange={(e) => setSearchFilter(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && searchFilter) {
                    handleSearch(searchFilter)
                  }
                }}
              />
              {searchFilter && (
                <button className="clear-btn" onClick={() => setSearchFilter('')}>
                  <X size={14} />
                </button>
              )}
            </div>
          )}
        </div>

        {/* 우측: 액션 버튼 */}
        <div className="gnb-right">
          <button
            className={`icon-btn ${isRefreshing ? 'spinning' : ''}`}
            onClick={handleRefresh}
            disabled={isRefreshing}
            title="새로고침"
          >
            <RefreshCw size={16} />
          </button>
          {viewMode === 'list' && listType === 'rawdata' && (
            <button
              className="primary-btn"
              onClick={() => {
                setShowCreateMode(true)
                setSearchState({ tenant: DEFAULT_TENANT, entityId: '' })
                setActiveTab('create')
                setViewMode('detail')
              }}
            >
              <Plus size={16} />
              <span>New</span>
            </button>
          )}
        </div>
      </div>

      {/* 메인 콘텐츠 */}
      <div className="explorer-main">
        <AnimatePresence mode="wait">
          {viewMode === 'detail' ? (
            <motion.div
              key="detail"
              className="detail-view"
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -20 }}
            >
              {renderDetailContent()}
            </motion.div>
          ) : showCreateMode ? (
            <motion.div
              key="create"
              className="create-view"
              initial={{ opacity: 0, scale: 0.98 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.98 }}
            >
              <RawDataEditor
                defaultTenant={DEFAULT_TENANT}
                onSuccess={(entityId) => {
                  setSearchState({ tenant: DEFAULT_TENANT, entityId })
                  setActiveTab('rawdata')
                  setViewMode('detail')
                  setShowCreateMode(false)
                }}
              />
            </motion.div>
          ) : (
            <motion.div
              key="list"
              className="list-view"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
            >
              {/* 서브 필터 바 */}
              <div className="sub-filter-bar">
                <div className="filter-left">
                  <div className="filter-item">
                    <Filter size={14} />
                    <span className="filter-label">Tenant:</span>
                    <span className="filter-value">{DEFAULT_TENANT}</span>
                  </div>
                  {listType === 'slices' && sliceTypes?.types?.length ? (
                    <div className="filter-item">
                      <Layers size={14} />
                      <span className="filter-label">Type:</span>
                      <select
                        className="slice-type-select"
                        value={selectedSliceType}
                        onChange={(e) => { setSelectedSliceType(e.target.value); setPage(1) }}
                      >
                        {sliceTypes.types.map((t) => (
                          <option key={t.type} value={t.type}>
                            {t.type} ({t.count})
                          </option>
                        ))}
                      </select>
                    </div>
                  ) : null}
                  <div className="filter-item">
                    <span className="filter-label">Total:</span>
                    <span className="filter-value accent">{totalItems.toLocaleString()}</span>
                  </div>
                </div>
                <div className="filter-right">
                  <select
                    className="page-size-select"
                    value={pageSize}
                    onChange={(e) => { setPageSize(Number(e.target.value)); setPage(1) }}
                  >
                    <option value={10}>10개씩</option>
                    <option value={20}>20개씩</option>
                    <option value={50}>50개씩</option>
                    <option value={100}>100개씩</option>
                  </select>
                </div>
              </div>

              {/* 테이블 */}
              <div className="table-container">
                {isLoading ? (
                  <div className="table-loading">
                    <Loading />
                  </div>
                ) : listType === 'views' ? (
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th className="col-entity">View Definition ID</th>
                        <th className="col-version">Version</th>
                        <th className="col-schema">Required Slices</th>
                        <th className="col-updated">Status</th>
                        <th className="col-action"></th>
                      </tr>
                    </thead>
                    <tbody>
                      {viewDefList?.entries?.map((entry, idx) => (
                        <motion.tr
                          key={`${entry.id}-${entry.version}-${idx}`}
                          initial={{ opacity: 0, y: 5 }}
                          animate={{ opacity: 1, y: 0 }}
                          transition={{ delay: idx * 0.015 }}
                          className="table-row"
                        >
                          <td className="col-entity">
                            <div className="cell-flex">
                              <Eye size={14} />
                              <span className="entity-text">{entry.id}</span>
                            </div>
                          </td>
                          <td className="col-version">
                            <span className="version-badge">{entry.version}</span>
                          </td>
                          <td className="col-schema">
                            <span className="schema-text">{entry.requiredSlices?.join(', ') || '-'}</span>
                          </td>
                          <td className="col-updated">
                            <span className={`status-badge ${entry.status?.toLowerCase()}`}>{entry.status}</span>
                          </td>
                          <td className="col-action">
                            <ChevronRight size={14} />
                          </td>
                        </motion.tr>
                      ))}
                    </tbody>
                  </table>
                ) : listType === 'slices' ? (
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th className="col-entity">Entity ID</th>
                        <th className="col-version">Version</th>
                        <th className="col-schema">RuleSet</th>
                        <th className="col-updated">Updated</th>
                        <th className="col-action"></th>
                      </tr>
                    </thead>
                    <tbody>
                      {sliceList?.entries?.map((entry, idx) => (
                        <motion.tr
                          key={`${entry.entityId}-${entry.version}-${idx}`}
                          initial={{ opacity: 0, y: 5 }}
                          animate={{ opacity: 1, y: 0 }}
                          transition={{ delay: idx * 0.015 }}
                          onClick={() => handleSelectItem(entry.entityId, listType)}
                          className="table-row"
                        >
                          <td className="col-entity">
                            <div className="cell-flex">
                              <Layers size={14} />
                              <span className="entity-text">{entry.entityId}</span>
                            </div>
                          </td>
                          <td className="col-version">
                            <span className="version-badge">v{entry.version}</span>
                          </td>
                          <td className="col-schema">
                            <span className="schema-text">{entry.ruleSetId || '-'}</span>
                          </td>
                          <td className="col-updated">
                            <div className="cell-flex">
                              <Clock size={12} />
                              <span>{entry.updatedAt ? new Date(entry.updatedAt).toLocaleDateString('ko-KR') : '-'}</span>
                            </div>
                          </td>
                          <td className="col-action">
                            <ChevronRight size={14} />
                          </td>
                        </motion.tr>
                      ))}
                    </tbody>
                  </table>
                ) : (
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th className="col-entity">Entity ID</th>
                        <th className="col-version">Version</th>
                        <th className="col-schema">Schema</th>
                        <th className="col-updated">Updated</th>
                        <th className="col-action"></th>
                      </tr>
                    </thead>
                    <tbody>
                      {rawDataList?.entries?.map((entry, idx) => (
                        <motion.tr
                          key={`${entry.entityId}-${entry.version}-${idx}`}
                          initial={{ opacity: 0, y: 5 }}
                          animate={{ opacity: 1, y: 0 }}
                          transition={{ delay: idx * 0.015 }}
                          onClick={() => handleSelectItem(entry.entityId, listType)}
                          className="table-row"
                        >
                          <td className="col-entity">
                            <div className="cell-flex">
                              <Database size={14} />
                              <span className="entity-text">{entry.entityId}</span>
                            </div>
                          </td>
                          <td className="col-version">
                            <span className="version-badge">v{entry.version}</span>
                          </td>
                          <td className="col-schema">
                            <span className="schema-text">{entry.schemaRef?.split('/').pop() || '-'}</span>
                          </td>
                          <td className="col-updated">
                            <div className="cell-flex">
                              <Clock size={12} />
                              <span>{entry.updatedAt ? new Date(entry.updatedAt).toLocaleDateString('ko-KR') : '-'}</span>
                            </div>
                          </td>
                          <td className="col-action">
                            <ChevronRight size={14} />
                          </td>
                        </motion.tr>
                      ))}
                    </tbody>
                  </table>
                )}

                {/* Empty State */}
                {!isLoading && (
                  (listType === 'rawdata' && (!rawDataList?.entries || rawDataList.entries.length === 0)) ||
                  (listType === 'slices' && (!sliceList?.entries || sliceList.entries.length === 0)) ||
                  (listType === 'views' && (!viewDefList?.entries || viewDefList.entries.length === 0))
                ) && (
                  <div className="table-empty">
                    {listType === 'views' ? <Eye size={48} /> : <Database size={48} />}
                    <h3>{listType === 'views' ? 'ViewDefinition이 없습니다' : '데이터가 없습니다'}</h3>
                    <p>{listType === 'views' ? 'Contract에서 ViewDefinition을 추가하세요.' : '검색 조건을 변경하거나 새 데이터를 등록해보세요.'}</p>
                    {listType === 'rawdata' && (
                      <button
                        className="empty-action-btn"
                        onClick={() => {
                          setShowCreateMode(true)
                          setSearchState({ tenant: DEFAULT_TENANT, entityId: '' })
                          setActiveTab('create')
                          setViewMode('detail')
                        }}
                      >
                        <Plus size={16} />
                        새 RawData 등록
                      </button>
                    )}
                  </div>
                )}
              </div>

              {/* 페이지네이션 */}
              {totalPages > 0 && (
                <div className="pagination-bar">
                  <div className="pagination-info">
                    <span>
                      {((page - 1) * pageSize) + 1} - {Math.min(page * pageSize, totalItems)} of {totalItems.toLocaleString()}
                    </span>
                  </div>
                  <div className="pagination-controls">
                    <button
                      className="page-btn"
                      disabled={page === 1}
                      onClick={() => setPage(1)}
                      title="첫 페이지"
                    >
                      <ChevronsLeft size={16} />
                    </button>
                    <button
                      className="page-btn"
                      disabled={page === 1}
                      onClick={() => setPage(p => Math.max(1, p - 1))}
                      title="이전 페이지"
                    >
                      <ChevronLeft size={16} />
                    </button>
                    <div className="page-numbers">
                      {generatePageNumbers(page, totalPages).map((p, i) => (
                        p === '...' ? (
                          <span key={`ellipsis-${i}`} className="page-ellipsis">...</span>
                        ) : (
                          <button
                            key={p}
                            className={`page-num ${page === p ? 'active' : ''}`}
                            onClick={() => setPage(p as number)}
                          >
                            {p}
                          </button>
                        )
                      ))}
                    </div>
                    <button
                      className="page-btn"
                      disabled={page === totalPages}
                      onClick={() => setPage(p => Math.min(totalPages, p + 1))}
                      title="다음 페이지"
                    >
                      <ChevronRight size={16} />
                    </button>
                    <button
                      className="page-btn"
                      disabled={page === totalPages}
                      onClick={() => setPage(totalPages)}
                      title="마지막 페이지"
                    >
                      <ChevronsRight size={16} />
                    </button>
                  </div>
                </div>
              )}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  )
}

/** 페이지 번호 생성 (1 2 3 ... 8 9 10 형태) */
function generatePageNumbers(current: number, total: number): (number | '...')[] {
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i + 1)
  }

  const pages: (number | '...')[] = []

  if (current <= 4) {
    pages.push(1, 2, 3, 4, 5, '...', total)
  } else if (current >= total - 3) {
    pages.push(1, '...', total - 4, total - 3, total - 2, total - 1, total)
  } else {
    pages.push(1, '...', current - 1, current, current + 1, '...', total)
  }

  return pages
}
