/**
 * Explorer 상태 관리 훅 (SRP 준수)
 * - 목록/상세 뷰 모드 관리
 * - 검색, 페이지네이션, 탭 상태
 * - URL 동기화
 */
import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import type { ExplorerTab } from '@/shared/types'
import { EXPLORER_DEFAULTS } from '@/shared/config'

export type ListType = 'rawdata' | 'slices' | 'views'

export interface SearchState {
  tenant: string
  entityId: string
  version?: number | 'latest'
}

export interface ExplorerState {
  viewMode: 'list' | 'detail'
  activeTab: ExplorerTab
  searchState: SearchState | null
  showCreateMode: boolean
  listType: ListType
  searchFilter: string
  page: number
  pageSize: number
  selectedSliceType: string
  isRefreshing: boolean
}

export function useExplorerState() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { entityId: urlEntityId } = useParams<{ entityId?: string }>()
  const [searchParams, setSearchParams] = useSearchParams()

  const { DEFAULT_TENANT, DEFAULT_PAGE_SIZE } = EXPLORER_DEFAULTS

  // 뷰 모드: list (목록) | detail (상세)
  const [viewMode, setViewMode] = useState<'list' | 'detail'>('list')
  const [activeTab, setActiveTab] = useState<ExplorerTab>('rawdata')
  const [searchState, setSearchState] = useState<SearchState | null>(null)
  const [showCreateMode, setShowCreateMode] = useState(false)
  const [isRefreshing, setIsRefreshing] = useState(false)

  // URL에서 탭 상태 읽기
  const urlTab = searchParams.get('tab') as ListType | null
  const [listType, setListType] = useState<ListType>(
    urlTab && ['rawdata', 'slices', 'views'].includes(urlTab) ? urlTab : 'rawdata'
  )

  // 목록 상태
  const [searchFilter, setSearchFilter] = useState('')
  const [page, setPageRaw] = useState(1)
  const [pageSize, setPageSizeRaw] = useState<number>(DEFAULT_PAGE_SIZE)
  const [selectedSliceType, setSelectedSliceType] = useState('')

  // 페이지 setter (경계값 검증)
  const setPage = useCallback((newPage: number | ((prev: number) => number)) => {
    setPageRaw((prev) => {
      const value = typeof newPage === 'function' ? newPage(prev) : newPage
      return Math.max(1, value) // 최소 1페이지
    })
  }, [])

  // 페이지 크기 setter (유효값 검증)
  const setPageSize = useCallback((size: number) => {
    const validSizes = [10, 20, 50, 100]
    if (validSizes.includes(size)) {
      setPageSizeRaw(size)
      setPageRaw(1) // 페이지 크기 변경 시 1페이지로 리셋
    }
  }, [])

  // URL entityId 변경 시 상세 뷰로 이동
  useEffect(() => {
    if (urlEntityId === 'new') {
      setShowCreateMode(true)
      setSearchState({ tenant: DEFAULT_TENANT, entityId: '' })
      setActiveTab('create')
      setViewMode('detail')
    } else if (urlEntityId) {
      setShowCreateMode(false)
      setSearchState({ tenant: DEFAULT_TENANT, entityId: urlEntityId })
      setViewMode('detail')
      setActiveTab('rawdata')
    } else {
      setViewMode('list')
      setShowCreateMode(false)
      setSearchState(null)
    }
  }, [urlEntityId, DEFAULT_TENANT])

  // 탭 변경 시 URL 업데이트
  const handleTabChange = useCallback((newTab: ListType) => {
    setListType(newTab)
    setPageRaw(1) // 페이지 리셋
    setSearchParams({ tab: newTab })
  }, [setSearchParams])

  const handleSearch = useCallback((entityId: string) => {
    if (!entityId.trim()) return
    navigate(`/explorer/${encodeURIComponent(entityId.trim())}`)
  }, [navigate])

  const handleSelectItem = useCallback((entityId: string, type: ListType) => {
    navigate(`/explorer/${encodeURIComponent(entityId)}`)
    setSearchState({ tenant: DEFAULT_TENANT, entityId })
    if (type === 'rawdata') setActiveTab('rawdata')
    else if (type === 'slices') setActiveTab('slices')
    else setActiveTab('view')
    setViewMode('detail')
  }, [navigate, DEFAULT_TENANT])

  const handleBackToList = useCallback(() => {
    navigate('/explorer')
    setViewMode('list')
    setSearchState(null)
    setShowCreateMode(false)
  }, [navigate])

  const handleRefresh = useCallback(async (refetchFns: {
    refetchRaw?: () => Promise<unknown>
    refetchSlices?: () => Promise<unknown>
    refetchViews?: () => Promise<unknown>
  }) => {
    setIsRefreshing(true)
    if (viewMode === 'list') {
      if (listType === 'rawdata' && refetchFns.refetchRaw) await refetchFns.refetchRaw()
      else if (listType === 'slices' && refetchFns.refetchSlices) await refetchFns.refetchSlices()
      else if (refetchFns.refetchViews) await refetchFns.refetchViews()
    } else {
      await queryClient.invalidateQueries({
        predicate: (query) => ['rawdata', 'slices', 'view', 'lineage'].includes(query.queryKey[0] as string),
      })
    }
    setIsRefreshing(false)
  }, [viewMode, listType, queryClient])

  const handleCreateSuccess = useCallback((entityId: string) => {
    setSearchState({ tenant: DEFAULT_TENANT, entityId })
    setActiveTab('rawdata')
    setViewMode('detail')
    setShowCreateMode(false)
    navigate(`/explorer/${encodeURIComponent(entityId)}`)
  }, [navigate, DEFAULT_TENANT])

  const openCreateMode = useCallback(() => {
    setShowCreateMode(true)
    setSearchState({ tenant: DEFAULT_TENANT, entityId: '' })
    setActiveTab('create')
    setViewMode('detail')
  }, [DEFAULT_TENANT])

  return {
    // State
    viewMode,
    activeTab,
    searchState,
    showCreateMode,
    listType,
    searchFilter,
    page,
    pageSize,
    selectedSliceType,
    isRefreshing,
    urlEntityId,

    // Setters
    setActiveTab,
    setSearchFilter,
    setPage,
    setPageSize,
    setSelectedSliceType,

    // Handlers
    handleTabChange,
    handleSearch,
    handleSelectItem,
    handleBackToList,
    handleRefresh,
    handleCreateSuccess,
    openCreateMode,

    // Constants
    DEFAULT_TENANT,
  }
}
