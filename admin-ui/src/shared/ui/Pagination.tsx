/**
 * Pagination Component
 *
 * 페이지네이션 컨트롤 컴포넌트:
 * - 페이지 번호 표시
 * - 첫 페이지/마지막 페이지 이동
 * - 이전/다음 페이지 이동
 * - 현재 페이지 범위 정보 표시
 * - 반응형 디자인 (작은 화면에서 자동 최적화)
 *
 * @example
 * ```tsx
 * const [page, setPage] = useState(1)
 * const totalPages = Math.ceil(totalItems / pageSize)
 *
 * <Pagination
 *   page={page}
 *   totalPages={totalPages}
 *   totalItems={totalItems}
 *   pageSize={pageSize}
 *   onPageChange={setPage}
 * />
 * ```
 *
 * @example
 * ```tsx
 * // 모바일에서도 페이지 번호 표시
 * <Pagination
 *   page={page}
 *   totalPages={totalPages}
 *   totalItems={totalItems}
 *   pageSize={pageSize}
 *   onPageChange={setPage}
 *   compactMobile
 * />
 * ```
 *
 * @responsive
 * - 태블릿(768px 이하): 정보와 컨트롤 세로 배치, 페이지 번호 축소
 * - 모바일(480px 이하): 정보 간소화, 기본적으로 페이지 번호 숨김
 * - compactMobile prop으로 모바일에서도 페이지 번호 표시 가능
 */
import { useEffect, useState } from 'react'
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react'
import { IconButton } from './IconButton'
import './Pagination.css'

export interface PaginationProps {
  /** Current page number (1-based) */
  page: number
  /** Total number of pages */
  totalPages: number
  /** Total number of items */
  totalItems: number
  /** Items per page */
  pageSize: number
  /** Callback when page changes */
  onPageChange: (page: number) => void
  /** Additional CSS class */
  className?: string
  /** Show compact version on mobile */
  compactMobile?: boolean
  /** Hide page numbers on very small screens */
  hidePageNumbersOnMobile?: boolean
}

function generatePageNumbers(current: number, total: number, isMobile: boolean = false): (number | '...')[] {
  // 모바일에서는 더 적은 페이지 번호 표시
  const maxVisible = isMobile ? 5 : 7

  if (total <= maxVisible) return Array.from({ length: total }, (_, i) => i + 1)

  if (isMobile) {
    // 모바일: 현재 페이지 중심으로 3개만 표시
    if (current === 1) return [1, 2, 3, '...', total]
    if (current === total) return [1, '...', total - 2, total - 1, total]
    return [1, '...', current - 1, current, current + 1, '...', total]
  }

  if (current <= 4) return [1, 2, 3, 4, 5, '...', total]
  if (current >= total - 3) return [1, '...', total - 4, total - 3, total - 2, total - 1, total]
  return [1, '...', current - 1, current, current + 1, '...', total]
}

export function Pagination({
  page = 1,
  totalPages = 1,
  totalItems = 0,
  pageSize = 10,
  onPageChange,
  className = '',
  compactMobile = false,
  hidePageNumbersOnMobile = false,
}: PaginationProps) {
  // 모바일 감지 (간단한 방법, 필요시 더 정교한 방법 사용 가능)
  const [isMobile, setIsMobile] = useState(false)

  useEffect(() => {
    const checkMobile = () => {
      setIsMobile(window.innerWidth < 768)
    }
    checkMobile()
    window.addEventListener('resize', checkMobile)
    return () => window.removeEventListener('resize', checkMobile)
  }, [])

  const pages = generatePageNumbers(page, totalPages, isMobile)

  if (totalPages <= 1) return null

  // 방어 코드: undefined 체크
  const safeTotal = totalItems ?? 0
  const safePage = page ?? 1
  const safePageSize = pageSize ?? 10

  const showPageNumbers = !(hidePageNumbersOnMobile && isMobile)
  const compactClass = compactMobile ? 'compact-mobile' : ''

  return (
    <div className={`pagination ${compactClass} ${className}`}>
      <div className="pagination-info">
        {isMobile && compactMobile ? (
          <span>
            Page {safePage} / {totalPages}
          </span>
        ) : (
          <span>
            {(safePage - 1) * safePageSize + 1} - {Math.min(safePage * safePageSize, safeTotal)} of {safeTotal.toLocaleString()}
          </span>
        )}
      </div>
      <div className="pagination-controls">
        {!isMobile && (
          <IconButton
            icon={ChevronsLeft}
            size="sm"
            variant="ghost"
            disabled={page === 1}
            onClick={() => onPageChange(1)}
            tooltip="첫 페이지"
            aria-label="첫 페이지"
          />
        )}
        <IconButton
          icon={ChevronLeft}
          size="sm"
          variant="ghost"
          disabled={page === 1}
          onClick={() => onPageChange(Math.max(1, page - 1))}
          tooltip="이전 페이지"
          aria-label="이전 페이지"
        />
        {showPageNumbers && (
          <div className="pagination-pages">
            {pages.map((p, i) =>
              p === '...' ? (
                <span key={`ellipsis-${i}`} className="pagination-ellipsis">...</span>
              ) : (
                <button
                  key={p}
                  type="button"
                  className={`pagination-page ${page === p ? 'active' : ''}`}
                  onClick={() => onPageChange(p as number)}
                  aria-current={page === p ? 'page' : undefined}
                  aria-label={`페이지 ${p}`}
                >
                  {p}
                </button>
              )
            )}
          </div>
        )}
        <IconButton
          icon={ChevronRight}
          size="sm"
          variant="ghost"
          disabled={page === totalPages}
          onClick={() => onPageChange(Math.min(totalPages, page + 1))}
          tooltip="다음 페이지"
          aria-label="다음 페이지"
        />
        {!isMobile && (
          <IconButton
            icon={ChevronsRight}
            size="sm"
            variant="ghost"
            disabled={page === totalPages}
            onClick={() => onPageChange(totalPages)}
            tooltip="마지막 페이지"
            aria-label="마지막 페이지"
          />
        )}
      </div>
    </div>
  )
}
