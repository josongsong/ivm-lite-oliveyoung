/**
 * @deprecated Use DataTable from '@/shared/ui/recipes' instead
 * This file is kept for backward compatibility only
 * 
 * Explorer-specific wrapper for DataTable recipe
 */
import { useState } from 'react'
import { Database, Eye, Layers } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { explorerApi } from '@/shared/api'
import { DataTable as DataTableRecipe, type DataTableItem } from '@/shared/ui/recipes'
import type { RawDataListEntry } from '@/shared/types'

export type DataTableType = 'rawdata' | 'slices' | 'views'

interface ExplorerDataTableProps {
  tenant: string
  type: DataTableType
  onSelect: (entityId: string, type: DataTableType) => void
  onCreateNew?: () => void
}

// RawData를 DataTableItem으로 변환
function toDataTableItem(entry: RawDataListEntry): DataTableItem {
  return {
    entityId: entry.entityId,
    version: entry.version,
    schemaRef: entry.schemaRef,
    updatedAt: entry.updatedAt,
  }
}

// search API는 RawDataListEntry를 반환하므로 sliceToDataTableItem은 사용하지 않음

export function DataTable({ tenant, type, onSelect, onCreateNew }: ExplorerDataTableProps) {
  const [searchFilter, setSearchFilter] = useState('')
  const [page, setPage] = useState(0)
  const limit = 20

  // RawData 목록
  const { data: rawDataList, isLoading: loadingRaw, isError: errorRaw, refetch: refetchRaw } = useQuery({
    queryKey: ['rawdata-list', tenant, searchFilter, page],
    queryFn: () => explorerApi.listRawData(tenant, searchFilter || undefined, limit),
    enabled: type === 'rawdata',
    retry: false,
  })

  // Slice 목록 (search API 사용 - RawDataListResponse 반환)
  const { data: sliceList, isLoading: loadingSlices, isError: errorSlices, refetch: refetchSlices } = useQuery({
    queryKey: ['slices-list', tenant, page],
    queryFn: () => explorerApi.search({ tenant, limit }),
    enabled: type === 'slices',
    retry: false,
  })

  const isLoading = type === 'rawdata' ? loadingRaw : loadingSlices
  const isError = type === 'rawdata' ? errorRaw : errorSlices
  const items = type === 'rawdata' 
    ? (rawDataList?.entries.map(toDataTableItem) || [])
    : (sliceList?.entries.map(toDataTableItem) || []) // search API는 RawDataListEntry 반환
  const total = type === 'rawdata' ? (rawDataList?.total || 0) : (sliceList?.total || 0)
  const hasMore = type === 'rawdata' ? (rawDataList?.hasMore || false) : (sliceList?.hasMore || false)

  const icon = type === 'rawdata' ? <Database size={18} /> : type === 'slices' ? <Layers size={18} /> : <Eye size={18} />
  const title = type === 'rawdata' ? 'RawData' : type === 'slices' ? 'Slices' : 'Views'

  return (
    <DataTableRecipe
      title={title}
      icon={icon}
      items={items}
      total={total}
      hasMore={hasMore}
      page={page}
      onPageChange={setPage}
      onSelect={(item) => onSelect(item.entityId, type)}
      searchFilter={searchFilter}
      onSearchFilterChange={setSearchFilter}
      onRefresh={() => {
        if (type === 'rawdata') refetchRaw()
        else refetchSlices()
      }}
      onCreateNew={onCreateNew && type === 'rawdata' ? onCreateNew : undefined}
      isLoading={isLoading}
      isError={isError}
    />
  )
}
