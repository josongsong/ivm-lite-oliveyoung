/**
 * Explorer 테이블 컴포넌트 (SRP 준수)
 * - 테이블 렌더링만 담당
 * - 상태 관리는 부모에서 처리
 */
import { motion } from 'framer-motion'
import { Database, Eye, Plus } from 'lucide-react'
import {
  type ColumnDef,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table'
import { Button, Loading } from '@/shared/ui'
import type { ListType } from '../hooks/useExplorerState'

interface ExplorerTableProps<T> {
  data: T[]
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  columns: ColumnDef<T, any>[]
  isLoading: boolean
  listType: ListType
  onSelectItem: (id: string, type: ListType) => void
  onCreateNew?: () => void
  getRowId: (row: T) => string
}

export function ExplorerTable<T>({
  data,
  columns,
  isLoading,
  listType,
  onSelectItem,
  onCreateNew,
  getRowId,
}: ExplorerTableProps<T>) {
  const table = useReactTable({
    data,
    columns,
    getCoreRowModel: getCoreRowModel(),
  })

  if (isLoading) {
    return (
      <div className="table-loading">
        <Loading />
      </div>
    )
  }

  if (data.length === 0) {
    return (
      <div className="table-empty">
        {listType === 'views' ? <Eye size={48} /> : <Database size={48} />}
        <h3>{listType === 'views' ? 'ViewDefinition이 없습니다' : '데이터가 없습니다'}</h3>
        <p>
          {listType === 'views'
            ? 'Contract에서 ViewDefinition을 추가하세요.'
            : '검색 조건을 변경하거나 새 데이터를 등록해보세요.'}
        </p>
        {listType === 'rawdata' && onCreateNew ? <Button variant="primary" onClick={onCreateNew} icon={<Plus size={16} />}>
            새 RawData 등록
          </Button> : null}
      </div>
    )
  }

  return (
    <table className="data-table">
      <thead>
        {table.getHeaderGroups().map((headerGroup) => (
          <tr key={headerGroup.id}>
            {headerGroup.headers.map((header) => (
              <th key={header.id} className={`col-${header.column.id}`}>
                {header.isPlaceholder
                  ? null
                  : flexRender(header.column.columnDef.header, header.getContext())}
              </th>
            ))}
          </tr>
        ))}
      </thead>
      <tbody>
        {table.getRowModel().rows.map((row, idx) => (
          <motion.tr
            key={row.id}
            initial={{ opacity: 0, y: 5 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: idx * 0.015 }}
            onClick={() => onSelectItem(getRowId(row.original), listType)}
            className="table-row"
          >
            {row.getVisibleCells().map((cell) => (
              <td key={cell.id} className={`col-${cell.column.id}`}>
                {flexRender(cell.column.columnDef.cell, cell.getContext())}
              </td>
            ))}
          </motion.tr>
        ))}
      </tbody>
    </table>
  )
}
