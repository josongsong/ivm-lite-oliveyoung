import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button, IconButton } from '@/shared/ui'

interface VersionNavigationProps {
  versions: number[]
  currentVersion: number
  latestVersion: number
  compareVersion: number | null
  showDiff: boolean
  onVersionSelect: (version: number) => void
  onCompareVersionSelect: (version: number) => void
  onNavigate: (direction: 'prev' | 'next') => void
}

export function VersionNavigation({
  versions,
  currentVersion,
  latestVersion,
  compareVersion,
  showDiff,
  onVersionSelect,
  onCompareVersionSelect,
  onNavigate,
}: VersionNavigationProps) {
  const currentIdx = versions.indexOf(currentVersion)

  return (
    <div className="rawdata-version-nav">
      <IconButton
        icon={ChevronLeft}
        iconSize={16}
        variant="ghost"
        size="sm"
        onClick={() => onNavigate('prev')}
        disabled={currentIdx <= 0}
        aria-label="Previous version"
      />

      <div className="version-timeline">
        {versions.map((v) => (
          <Button
            key={v}
            variant={v === currentVersion ? 'primary' : v === compareVersion ? 'secondary' : 'ghost'}
            size="sm"
            className={`version-dot ${v === currentVersion ? 'active' : ''} ${
              v === compareVersion ? 'compare' : ''
            }`}
            onClick={() => {
              if (showDiff && v !== currentVersion) {
                onCompareVersionSelect(v)
              } else {
                onVersionSelect(v)
              }
            }}
            title={`Version ${v}`}
          >
            <span className="version-label">v{v}</span>
          </Button>
        ))}
      </div>

      <IconButton
        icon={ChevronRight}
        iconSize={16}
        variant="ghost"
        size="sm"
        onClick={() => onNavigate('next')}
        disabled={currentIdx >= versions.length - 1}
        aria-label="Next version"
      />

      <span className="version-info">
        v{currentVersion} / {latestVersion}
        {currentVersion === latestVersion && (
          <span className="latest-badge">latest</span>
        )}
      </span>
    </div>
  )
}
