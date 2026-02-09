/**
 * ActionCard Component
 *
 * 빠른 액션 카드 컴포넌트 (아이콘 + 제목 + 설명 + 화살표)
 */

import { type ReactNode } from 'react'
import { ArrowRight } from 'lucide-react'
import './ActionCard.css'

export interface ActionCardProps {
  icon: ReactNode
  title: string
  description: string
  to?: string
  onClick?: () => void
  className?: string
}

export function ActionCard({ icon, title, description, to, onClick, className = '' }: ActionCardProps) {
  const Component = to ? 'a' : onClick ? 'button' : 'div'
  const props = to
    ? { href: to, className: `ui-action-card ${className}` }
    : onClick
      ? { onClick, className: `ui-action-card ${className}`, type: 'button' as const }
      : { className: `ui-action-card ${className}` }

  return (
    <Component {...props}>
      <span className="ui-action-card__icon">{icon}</span>
      <div className="ui-action-card__content">
        <span className="ui-action-card__title">{title}</span>
        <span className="ui-action-card__desc">{description}</span>
      </div>
      <ArrowRight size={16} className="ui-action-card__arrow" />
    </Component>
  )
}
