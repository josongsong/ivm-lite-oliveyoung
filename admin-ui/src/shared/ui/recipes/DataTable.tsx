/**
 * DataTable Component (Recipe)
 *
 * 범용 데이터 테이블 패턴 컴포넌트
 * - 검색 필터
 * - 페이지네이션
 * - 로딩/에러 상태
 * - 빈 상태 처리
 */

import { motion } from 'framer-motion'
import {
  ChevronRight,
  Clock,
  Plus,
  RefreshCw,
} from 'lucide-react'
import { Loading, SearchFilter, TableHeader } from '@/shared/ui'
import './DataTable.css'

export interface DataTableItem {
  entityId: string
  version: number
  schemaRef?: string
  updatedAt?: string
  [key: string]: unknown
}

export interface DataTableProps<T extends DataTableItem> {
  /** 테이블 제목 */
  title: string
  /** 아이콘 (ReactNode) */
  icon?: React.ReactNode
  /** 데이터 목록 */
  items: T[]
  /** 전체 개수 */
  total: number
  /** 더 많은 데이터가 있는지 */
  hasMore: boolean
  /** 현재 페이지 */
  page: number
  /** 페이지 변경 핸들러 */
  onPageChange: (page: number) => void
  /** 아이템 선택 핸들러 */
  onSelect: (item: T) => void
  /** 검색 필터 값 */
  searchFilter?: string
  /** 검색 필터 변경 핸들러 */
  onSearchFilterChange?: (filter: string) => void
  /** 새로고침 핸들러 */
  onRefresh?: () => void
  /** 새로 만들기 핸들러 */
  onCreateNew?: () => void
  /** 로딩 상태 */
  isLoading?: boolean
  /** 에러 상태 */
  isError?: boolean
  /** 에러 메시지 */
  errorMessage?: string
  /** 빈 상태 메시지 */
  emptyMessage?: string
  /** 커스텀 렌더링 함수 */
  renderRow?: (item: T, index: number) => React.ReactNode
  /** 기본 렌더링 사용 여부 */
  useDefaultRender?: boolean
  className?: string
}

export function DataTable<T extends DataTableItem>({
  title,
  icon,
  items,
  total,
  hasMore,
  page,
  onPageChange,
  onSelect,
  searchFilter = '',
  onSearchFilterChange,
  onRefresh,
  onCreateNew,
  isLoading = false,
  isError = false,
  errorMessage = 'API 연결 대기 중...',
  emptyMessage = '데이터가 없습니다',
  renderRow,
  useDefaultRender = true,
  className = '',
}: DataTableProps<T>) {
  const handleRefresh = () => {
    onRefresh?.()
  }

  const defaultRenderRow = (item: T, index: number) => (
    <motion.tr
      key={`${item.entityId}-${item.version}`}
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.02 }}
      onClick={() => onSelect(item)}
    >
      <td className="entity-cell">
        {icon}
        <span>{item.entityId}</span>
      </td>
      <td className="version-cell">
        <span className="version-badge">v{item.version}</span>
      </td>
      <td className="schema-cell">{item.schemaRef?.split('/').pop() || '-'}</td>
      <td className="time-cell">
        <Clock size={12} />
        {item.updatedAt ? new Date(item.updatedAt).toLocaleDateString() : '-'}
      </td>
      <td className="action-cell">
        <ChevronRight size={14} />
      </td>
    </motion.tr>
  )

  const rowRenderer = renderRow || (useDefaultRender ? defaultRenderRow : undefined)

  return (
    <div className={`data-table ${className}`}>
      {/* 헤더 */}
      <TableHeader
        icon={icon}
        title={title}
        count={total}
        actions={
          <>
            {onSearchFilterChange && (
              <SearchFilter
                placeholder="Entity ID로 필터..."
                value={searchFilter}
                onChange={(e) => onSearchFilterChange(e.target.value)}
              />
            )}
            {onRefresh && (
              <button className="action-btn" onClick={handleRefresh}>
                <RefreshCw size={14} />
              </button>
            )}
            {onCreateNew && (
              <button className="action-btn primary" onClick={onCreateNew}>
                <Plus size={14} />
                New
              </button>
            )}
          </>
        }
      />

      {/* 테이블 */}
      {isLoading ? (
        <div className="table-loading">
          <Loading />
        </div>
      ) : isError ? (
        <div className="table-error">
          <p>{errorMessage}</p>
          <span>백엔드 서버가 실행되면 데이터가 표시됩니다</span>
          {onCreateNew && (
            <button className="create-btn" onClick={onCreateNew}>
              <Plus size={14} />
              새 항목 등록
            </button>
          )}
        </div>
      ) : (
        <div className="table-content">
          <table>
            <thead>
              <tr>
                <th>Entity ID</th>
                <th>Version</th>
                <th>Schema / Type</th>
                <th>Updated</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {items.map((item, index) => rowRenderer?.(item, index))}
            </tbody>
          </table>

          {/* Empty State */}
          {items.length === 0 && (
            <div className="table-empty">
              <p>{emptyMessage}</p>
              {onCreateNew && (
                <button onClick={onCreateNew}>
                  <Plus size={14} />
                  새 항목 등록
                </button>
              )}
            </div>
          )}
        </div>
      )}

      {/* 페이지네이션 */}
      {hasMore && (
        <div className="table-pagination">
          <button
            disabled={page === 0}
            onClick={() => onPageChange(Math.max(0, page - 1))}
          >
            이전
          </button>
          <span>Page {page + 1}</span>
          <button onClick={() => onPageChange(page + 1)}>
            다음
          </button>
        </div>
      )}
    </div>
  )
}
