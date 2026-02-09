import { Button } from '@/shared/ui'
import { formatDuration } from '@/shared/ui/formatters'

interface WaterfallHeaderProps {
  spanCount: number
  errorCount: number
  totalDuration: number
  onExpandAll: () => void
  onCollapseAll: () => void
}

export function WaterfallHeader({
  spanCount,
  errorCount,
  totalDuration,
  onExpandAll,
  onCollapseAll,
}: WaterfallHeaderProps) {
  return (
    <div className="waterfall-header">
      <div className="waterfall-stats">
        <div className="stat-item">
          <span className="stat-label">총 스팬</span>
          <span className="stat-value">{spanCount}</span>
        </div>
        <div className="stat-item">
          <span className="stat-label">총 시간</span>
          <span className="stat-value">{formatDuration(totalDuration)}</span>
        </div>
        <div className="stat-item error">
          <span className="stat-label">에러</span>
          <span className="stat-value">{errorCount}</span>
        </div>
      </div>
      <div className="waterfall-controls">
        <Button variant="ghost" size="sm" onClick={onExpandAll}>
          모두 펼치기
        </Button>
        <Button variant="ghost" size="sm" onClick={onCollapseAll}>
          모두 접기
        </Button>
      </div>
    </div>
  )
}
