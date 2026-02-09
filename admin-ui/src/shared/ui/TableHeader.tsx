/**
 * TableHeader Component
 *
 * 테이블 헤더 컴포넌트 (아이콘 + 제목 + 카운트 + 액션)
 */

import { type ReactNode } from 'react'
import './TableHeader.css'

export interface TableHeaderProps {
  icon?: ReactNode
  title: string
  count?: number
  actions?: ReactNode
  className?: string
}

export function TableHeader({ icon, title, count, actions, className = '' }: TableHeaderProps) {
  return (
    <div className={`ui-table-header ${className}`}>
      <div className="ui-table-header__title">
        {icon && <span className="ui-table-header__icon">{icon}</span>}
        <h3>{title}</h3>
        {count !== undefined && (
          <span className="ui-table-header__count">{count}</span>
        )}
      </div>
      {actions && <div className="ui-table-header__actions">{actions}</div>}
    </div>
  )
}
