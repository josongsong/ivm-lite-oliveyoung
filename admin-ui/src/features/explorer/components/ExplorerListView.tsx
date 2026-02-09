/**
 * ExplorerListView Component
 *
 * DataExplorer의 목록 보기 뷰
 */
import { motion } from 'framer-motion'
import type { RawDataListResponse, SliceListByTypeResponse, ViewDefinitionListResponse } from '@/shared/types'
import { ErrorBoundary, Pagination } from '@/shared/ui'
import { ExplorerFilterBar } from './ExplorerFilterBar'
import { ExplorerTable } from './ExplorerTable'
import { RawDataEditor } from './RawDataEditor'
import { rawDataColumns, sliceColumns, viewColumns } from '../constants/columns'

export type ListType = 'rawdata' | 'slices' | 'views'

interface SliceTypeInfo {
  type: string
  count: number
}

interface ExplorerListViewProps {
  listType: ListType
  showCreateMode: boolean
  tenant: string
  page: number
  pageSize: number
  totalItems: number
  rawDataList?: RawDataListResponse
  sliceList?: SliceListByTypeResponse
  viewDefList?: ViewDefinitionListResponse
  loadingRaw: boolean
  loadingSlices: boolean
  loadingViews: boolean
  selectedSliceType: string
  sliceTypes?: SliceTypeInfo[]
  onSelectItem: (entityId: string, type: ListType) => void
  onCreateNew: () => void
  onCreateSuccess: (entityId: string) => void
  onSliceTypeChange: (type: string) => void
  onPageSizeChange: (size: number) => void
  onPageChange: (page: number) => void
}

export function ExplorerListView({
  listType,
  showCreateMode,
  tenant,
  page,
  pageSize,
  totalItems,
  rawDataList,
  sliceList,
  viewDefList,
  loadingRaw,
  loadingSlices,
  loadingViews,
  selectedSliceType,
  sliceTypes,
  onSelectItem,
  onCreateNew,
  onCreateSuccess,
  onSliceTypeChange,
  onPageSizeChange,
  onPageChange,
}: ExplorerListViewProps) {
  const totalPages = Math.ceil(totalItems / pageSize)

  if (showCreateMode) {
    return (
      <motion.div
        key="create"
        className="create-view"
        initial={{ opacity: 0, scale: 0.98 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.98 }}
      >
        <ErrorBoundary resetKeys={[tenant]}>
          <RawDataEditor defaultTenant={tenant} onSuccess={onCreateSuccess} />
        </ErrorBoundary>
      </motion.div>
    )
  }

  return (
    <motion.div key="list" className="list-view" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
      <ExplorerFilterBar
        listType={listType}
        tenant={tenant}
        totalItems={totalItems}
        pageSize={pageSize}
        selectedSliceType={selectedSliceType}
        sliceTypes={sliceTypes}
        onSliceTypeChange={onSliceTypeChange}
        onPageSizeChange={onPageSizeChange}
      />

      <div className="table-container">
        {listType === 'rawdata' && (
          <ExplorerTable
            data={rawDataList?.entries || []}
            columns={rawDataColumns}
            isLoading={loadingRaw}
            listType={listType}
            onSelectItem={onSelectItem}
            onCreateNew={onCreateNew}
            getRowId={(row) => row.entityId}
          />
        )}
        {listType === 'slices' && (
          <ExplorerTable
            data={sliceList?.entries || []}
            columns={sliceColumns}
            isLoading={loadingSlices}
            listType={listType}
            onSelectItem={onSelectItem}
            getRowId={(row) => row.entityId}
          />
        )}
        {listType === 'views' && (
          <ExplorerTable
            data={viewDefList?.entries || []}
            columns={viewColumns}
            isLoading={loadingViews}
            listType={listType}
            onSelectItem={onSelectItem}
            getRowId={(row) => row.id}
          />
        )}
      </div>

      {totalPages > 0 && (
        <Pagination page={page} totalPages={totalPages} totalItems={totalItems} pageSize={pageSize} onPageChange={onPageChange} />
      )}
    </motion.div>
  )
}
