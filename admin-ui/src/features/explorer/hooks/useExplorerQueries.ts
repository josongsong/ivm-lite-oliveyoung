/**
 * useExplorerQueries Hook
 *
 * DataExplorer의 데이터 조회 쿼리들
 */
import { useQuery } from '@tanstack/react-query'
import { explorerApi } from '@/shared/api'

export type ListType = 'rawdata' | 'slices' | 'views'
export type ViewMode = 'list' | 'detail'

interface UseExplorerQueriesOptions {
  viewMode: ViewMode
  listType: ListType
  tenant: string
  searchFilter: string
  page: number
  pageSize: number
  selectedSliceType: string
}

export function useExplorerQueries({
  viewMode,
  listType,
  tenant,
  searchFilter,
  page,
  pageSize,
  selectedSliceType,
}: UseExplorerQueriesOptions) {
  const rawDataQuery = useQuery({
    queryKey: ['rawdata-list', tenant, searchFilter, page, pageSize],
    queryFn: () => explorerApi.listRawData(tenant, searchFilter || undefined, pageSize),
    enabled: viewMode === 'list' && listType === 'rawdata',
  })

  const sliceTypesQuery = useQuery({
    queryKey: ['slice-types', tenant],
    queryFn: () => explorerApi.getSliceTypes(tenant),
    enabled: viewMode === 'list' && listType === 'slices',
  })

  const sliceListQuery = useQuery({
    queryKey: ['slices-list', tenant, selectedSliceType, page, pageSize],
    queryFn: () => explorerApi.listSlicesByType(tenant, selectedSliceType, pageSize),
    enabled: viewMode === 'list' && listType === 'slices' && !!selectedSliceType,
  })

  const viewDefQuery = useQuery({
    queryKey: ['views-list'],
    queryFn: () => explorerApi.getViewDefinitions(),
    enabled: viewMode === 'list' && listType === 'views',
  })

  const refetchAll = () => {
    rawDataQuery.refetch()
    sliceListQuery.refetch()
    viewDefQuery.refetch()
  }

  return {
    rawDataList: rawDataQuery.data,
    loadingRaw: rawDataQuery.isLoading,
    refetchRaw: rawDataQuery.refetch,
    sliceTypes: sliceTypesQuery.data,
    sliceList: sliceListQuery.data,
    loadingSlices: sliceListQuery.isLoading,
    refetchSlices: sliceListQuery.refetch,
    viewDefList: viewDefQuery.data,
    loadingViews: viewDefQuery.isLoading,
    refetchViews: viewDefQuery.refetch,
    refetchAll,
  }
}
