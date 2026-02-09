/**
 * SearchFilter Component
 *
 * 검색 필터 컴포넌트 (아이콘 + 입력)
 */

import { Search } from 'lucide-react'
import './SearchFilter.css'

export interface SearchFilterProps extends React.InputHTMLAttributes<HTMLInputElement> {
  icon?: React.ReactNode
  className?: string
}

export function SearchFilter({ icon, className = '', ...props }: SearchFilterProps) {
  return (
    <div className={`ui-search-filter ${className}`}>
      {icon || <Search size={14} />}
      <input type="text" className="ui-search-filter__input" {...props} />
    </div>
  )
}
