/**
 * StatCard Component
 *
 * 통계 카드 컴포넌트 (아이콘 + 값 + 라벨)
 */

import { type ReactNode } from 'react'
import './StatCard.css'

export interface StatCardProps {
  icon: ReactNode
  value: string | number
  label: string
  variant?: 'default' | 'error' | 'warning' | 'accent' | 'success'
  className?: string
}

export function StatCard({ icon, value, label, variant = 'default', className = '' }: StatCardProps) {
  return (
    <div className={`ui-stat-card ui-stat-card--${variant} ${className}`}>
      <div className="ui-stat-card__icon">{icon}</div>
      <div className="ui-stat-card__content">
        <span className="ui-stat-card__value">{value}</span>
        <span className="ui-stat-card__label">{label}</span>
      </div>
    </div>
  )
}
