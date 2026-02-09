import type { ReactNode } from 'react'

interface DescriptionSectionProps {
  title: string
  icon?: ReactNode
  children: ReactNode
}

export function DescriptionSection({ title, icon, children }: DescriptionSectionProps) {
  return (
    <div className="description-section">
      <h4 className="section-title">
        {icon}
        {title}
      </h4>
      <div className="section-content">
        {children}
      </div>
    </div>
  )
}
