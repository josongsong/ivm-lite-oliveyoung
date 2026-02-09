/**
 * Explorer 테이블 셀 렌더러 컴포넌트
 * - react-refresh 호환을 위해 분리
 */
import { ChevronRight, Clock } from 'lucide-react'

// 공통 셀 렌더러
export function EntityCell({ value, icon: Icon }: { value: string; icon: React.ElementType }) {
  return (
    <div className="cell-flex">
      <Icon size={14} />
      <span className="entity-text">{value}</span>
    </div>
  )
}

export function VersionCell({ value }: { value: number | string }) {
  return <span className="version-badge">v{value}</span>
}

export function DateCell({ value }: { value: string | null | undefined }) {
  return (
    <div className="cell-flex">
      <Clock size={12} />
      <span>{value ? new Date(value).toLocaleDateString('ko-KR') : '-'}</span>
    </div>
  )
}

export function ActionCell() {
  return <ChevronRight size={14} />
}
