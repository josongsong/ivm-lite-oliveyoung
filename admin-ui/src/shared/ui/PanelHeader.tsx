/**
 * PanelHeader Component
 *
 * 패널 헤더 컴포넌트 (아이콘 + 제목 + 링크/액션)
 */

import { type ReactNode } from 'react'
import './PanelHeader.css'

export interface PanelHeaderProps {
  icon?: ReactNode
  title: string
  link?: {
    to: string
    label: string
  }
  actions?: ReactNode
  className?: string
}

export function PanelHeader({ icon, title, link, actions, className = '' }: PanelHeaderProps) {
  return (
    <div className={`ui-panel-header ${className}`}>
      {icon && <span className="ui-panel-header__icon">{icon}</span>}
      <h3 className="ui-panel-header__title">{title}</h3>
      {link && (
        <a href={link.to} className="ui-panel-header__link">
          {link.label}
        </a>
      )}
      {actions && <div className="ui-panel-header__actions">{actions}</div>}
    </div>
  )
}
