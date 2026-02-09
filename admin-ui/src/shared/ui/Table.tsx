/**
 * Table Component
 *
 * SOTA-level generic table with:
 * - Type-safe columns with render functions
 * - Loading state
 * - Empty state
 * - Row click handlers
 * - Row actions slot
 * - Framer Motion row animations
 *
 * NOTE: This is a "presentation component".
 * - Sorting, filtering, pagination should be handled by parent
 * - For complex tables with built-in features, use ExplorerTable (TanStack Table)
 *
 * @example
 * ```tsx
 * const columns: TableColumn<User>[] = [
 *   { key: 'id', header: 'ID', width: '100px' },
 *   { key: 'name', header: 'Name' },
 *   {
 *     key: 'status',
 *     header: 'Status',
 *     render: (user) => <StatusBadge status={user.status} />
 *   }
 * ]
 *
 * <Table
 *   data={users}
 *   columns={columns}
 *   keyExtractor={(user) => user.id}
 *   loading={isLoading}
 *   rowActions={(user) => (
 *     <IconButton icon={Trash2} onClick={() => delete(user.id)} />
 *   )}
 * />
 * ```
 */
import { type ReactNode } from 'react'
import { motion } from 'framer-motion'
import { Loading } from './Loading'
import './Table.css'

export interface TableColumn<T> {
  /** Column key (must match data property or be custom key for render) */
  key: keyof T | string
  /** Column header text */
  header: string
  /** Column width (CSS value) */
  width?: string
  /** Custom render function */
  render?: (item: T, index: number) => ReactNode
  /** Additional className for the column cells */
  className?: string
  /** Header alignment */
  align?: 'left' | 'center' | 'right'
}

export interface TableProps<T> {
  /** Data array to display */
  data: T[]
  /** Column definitions */
  columns: TableColumn<T>[]
  /** Function to extract unique key from each item */
  keyExtractor: (item: T) => string
  /** Shows loading state */
  loading?: boolean
  /** Message to show when data is empty */
  emptyMessage?: string
  /** Callback when row is clicked */
  onRowClick?: (item: T) => void
  /** Render function for row action buttons */
  rowActions?: (item: T) => ReactNode
  /** Content to show above the table (e.g., info banners) */
  headerContent?: ReactNode
  /** Additional className for table container */
  className?: string
  /** Enable striped rows */
  striped?: boolean
  /** Enable row hover effect */
  hoverable?: boolean
  /** Compact size */
  compact?: boolean
}

export function Table<T>({
  data,
  columns,
  keyExtractor,
  loading = false,
  emptyMessage = '데이터가 없습니다',
  onRowClick,
  rowActions,
  headerContent,
  className = '',
  striped = false,
  hoverable = true,
  compact = false,
}: TableProps<T>) {
  // Loading state
  if (loading) {
    return (
      <div className="table-loading">
        <Loading size="md" fullPage={false} />
      </div>
    )
  }

  // Build columns including actions column if provided
  const allColumns = rowActions
    ? [...columns, { key: '__actions__' as keyof T | string, header: '', width: 'auto', align: 'right' as const }]
    : columns

  // Build container class
  const containerClass = [
    'table-container',
    compact ? 'table-container--compact' : '',
    className,
  ].filter(Boolean).join(' ')

  // Build table class
  const tableClass = [
    'table',
    striped ? 'table--striped' : '',
    hoverable ? 'table--hoverable' : '',
  ].filter(Boolean).join(' ')

  return (
    <div className={containerClass}>
      {headerContent}
      <table className={tableClass}>
        <thead>
          <tr>
            {allColumns.map((col) => (
              <th
                key={String(col.key)}
                style={col.width ? { width: col.width } : undefined}
                className={col.className}
                data-align={col.align}
              >
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.length === 0 ? (
            <tr>
              <td colSpan={allColumns.length} className="table-empty">
                {emptyMessage}
              </td>
            </tr>
          ) : (
            data.map((item, index) => (
              <motion.tr
                key={keyExtractor(item)}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: index * 0.02, duration: 0.2 }}
                onClick={onRowClick ? () => onRowClick(item) : undefined}
                className={onRowClick ? 'table-row--clickable' : ''}
              >
                {columns.map((col) => (
                  <td
                    key={String(col.key)}
                    className={col.className}
                    data-align={col.align}
                  >
                    {col.render
                      ? col.render(item, index)
                      : String(item[col.key as keyof T] ?? '')}
                  </td>
                ))}
                {rowActions ? (
                  <td className="table-actions" data-align="right">
                    {rowActions(item)}
                  </td>
                ) : null}
              </motion.tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}
