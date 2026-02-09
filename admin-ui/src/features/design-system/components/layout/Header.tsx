/**
 * Design System Header Component
 *
 * 페이지 상단 헤더를 제공합니다.
 * - 페이지 타이틀 및 브레드크럼
 * - 검색 (Optional)
 * - 테마 토글 (Optional)
 */
import { type ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { ChevronRight, ExternalLink, Github } from 'lucide-react'
import { Button } from '@/shared/ui'
import './Header.css'

interface HeaderProps {
  title: string
  description?: string
  actions?: ReactNode
}

interface BreadcrumbItem {
  label: string
  path?: string
}

function getBreadcrumbs(pathname: string): BreadcrumbItem[] {
  const segments = pathname.split('/').filter(Boolean)
  const breadcrumbs: BreadcrumbItem[] = [{ label: 'Design System', path: '/design-system' }]

  if (segments.length <= 1) return breadcrumbs

  let currentPath = ''
  for (let i = 1; i < segments.length; i++) {
    currentPath += `/${segments[i]}`
    const segment = segments[i]
    const label = segment
      .split('-')
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ')

    if (i === segments.length - 1) {
      breadcrumbs.push({ label })
    } else {
      breadcrumbs.push({ label, path: `/design-system${currentPath}` })
    }
  }

  return breadcrumbs
}

export function Header({ title, description, actions }: HeaderProps) {
  const location = useLocation()
  const breadcrumbs = getBreadcrumbs(location.pathname)

  return (
    <header className="ds-header">
      <div className="ds-header-top">
        <nav className="ds-breadcrumbs" aria-label="Breadcrumb">
          {breadcrumbs.map((item, index) => (
            <span key={item.label} className="ds-breadcrumb-item">
              {index > 0 && <ChevronRight size={14} className="ds-breadcrumb-separator" />}
              {item.path ? (
                <Link to={item.path} className="ds-breadcrumb-link">
                  {item.label}
                </Link>
              ) : (
                <span className="ds-breadcrumb-current">{item.label}</span>
              )}
            </span>
          ))}
        </nav>

        <div className="ds-header-actions">
          {actions}
          <Button
            variant="ghost"
            size="sm"
            icon={<Github size={16} />}
            onClick={() => window.open('https://github.com/oliveyoung/ivm-lite', '_blank')}
          >
            Source
          </Button>
        </div>
      </div>

      <div className="ds-header-main">
        <div className="ds-header-content">
          <h1 className="ds-header-title">{title}</h1>
          {description ? <p className="ds-header-description">{description}</p> : null}
        </div>
      </div>
    </header>
  )
}

interface PageHeaderProps {
  title: string
  description?: string
  stability?: 'stable' | 'beta' | 'experimental' | 'deprecated'
  sourceLink?: string
  children?: ReactNode
}

export function PageHeader({
  title,
  description,
  stability,
  sourceLink,
  children,
}: PageHeaderProps) {
  return (
    <Header
      title={title}
      description={description}
      actions={
        <>
          {stability ? <StabilityBadge stability={stability} /> : null}
          {sourceLink ? <Button
              variant="ghost"
              size="sm"
              icon={<ExternalLink size={14} />}
              onClick={() => window.open(sourceLink, '_blank')}
            >
              View Source
            </Button> : null}
          {children}
        </>
      }
    />
  )
}

interface StabilityBadgeProps {
  stability: 'stable' | 'beta' | 'experimental' | 'deprecated'
}

function StabilityBadge({ stability }: StabilityBadgeProps) {
  const labels: Record<string, string> = {
    stable: 'Stable',
    beta: 'Beta',
    experimental: 'Experimental',
    deprecated: 'Deprecated',
  }

  return (
    <span className={`ds-stability-badge ds-stability-${stability}`}>
      {labels[stability]}
    </span>
  )
}
