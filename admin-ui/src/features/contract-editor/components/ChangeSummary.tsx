/**
 * ChangeSummary (Phase 2: DX Platform)
 *
 * 의미론적 변경 요약 표시.
 * - Breaking change 강조
 * - 영향받는 Slice/View 목록
 * - 재생성 필요 여부
 */
import { Button } from '@/shared/ui'
import type { ChangeType, SemanticChange, SemanticDiffResponse } from '../api/types'
import './ChangeSummary.css'

interface ChangeSummaryProps {
  diff: SemanticDiffResponse | null
  isLoading: boolean
  onCopyToClipboard?: () => void
  onCreatePR?: () => void
}

const CHANGE_CONFIG: Record<ChangeType, { icon: string; color: string; label: string }> = {
  PROPERTY_ADDED: { icon: '+', color: '#22c55e', label: 'Property added' },
  PROPERTY_REMOVED: { icon: '-', color: '#ef4444', label: 'Property removed' },
  PROPERTY_CHANGED: { icon: '~', color: '#eab308', label: 'Property changed' },
  FIELD_ADDED: { icon: '+', color: '#22c55e', label: 'Field added' },
  FIELD_REMOVED: { icon: '-', color: '#ef4444', label: 'Field removed' },
  FIELD_TYPE_CHANGED: { icon: '~', color: '#ef4444', label: 'Type changed' },
  FIELD_REQUIRED_CHANGED: { icon: '~', color: '#eab308', label: 'Required changed' },
  SLICE_ADDED: { icon: '+', color: '#22c55e', label: 'Slice added' },
  SLICE_REMOVED: { icon: '-', color: '#ef4444', label: 'Slice removed' },
  RULE_CHANGED: { icon: '~', color: '#3b82f6', label: 'Rule changed' },
  IMPACT_MAP_CHANGED: { icon: '~', color: '#8b5cf6', label: 'Impact map changed' },
  SLICE_REF_ADDED: { icon: '+', color: '#22c55e', label: 'Slice ref added' },
  SLICE_REF_REMOVED: { icon: '-', color: '#ef4444', label: 'Slice ref removed' },
  SLICE_REF_PROMOTED: { icon: '^', color: '#f97316', label: 'Optional -> Required' },
  SLICE_REF_DEMOTED: { icon: 'v', color: '#06b6d4', label: 'Required -> Optional' },
}

export function ChangeSummary({
  diff,
  isLoading,
  onCopyToClipboard,
  onCreatePR,
}: ChangeSummaryProps) {
  if (isLoading) {
    return (
      <div className="change-summary change-summary--loading">
        <div className="change-summary__spinner" />
        <span>Diff 계산 중...</span>
      </div>
    )
  }

  if (!diff) {
    return (
      <div className="change-summary change-summary--empty">
        <p>변경 전/후 YAML을 비교하면 차이점이 표시됩니다</p>
      </div>
    )
  }

  if (diff.changes.length === 0) {
    return (
      <div className="change-summary change-summary--no-changes">
        <span className="change-summary__icon">✓</span>
        <p>변경사항 없음</p>
      </div>
    )
  }

  const breakingChanges = diff.changes.filter(c => c.breaking)

  return (
    <div className="change-summary">
      {/* 헤더 */}
      <div className="change-summary__header">
        <h3 className="change-summary__title">CHANGE SUMMARY</h3>
        <div className="change-summary__actions">
          {onCopyToClipboard ? <Button variant="secondary" size="sm" onClick={onCopyToClipboard}>
              Copy
            </Button> : null}
          {onCreatePR ? <Button variant="primary" size="sm" onClick={onCreatePR}>
              Create PR
            </Button> : null}
        </div>
      </div>

      {/* 변경 목록 */}
      <div className="change-summary__changes">
        {diff.changes.map((change, index) => (
          <ChangeItem key={`${change.target}-${index}`} change={change} />
        ))}
      </div>

      {/* 요약 통계 */}
      <div className="change-summary__stats">
        <div className="change-summary__stat">
          <span className="change-summary__stat-label">Breaking changes</span>
          <span className={`change-summary__stat-value ${breakingChanges.length > 0 ? 'change-summary__stat-value--warning' : ''}`}>
            {breakingChanges.length}
          </span>
        </div>

        {diff.affectedSlices.length > 0 && (
          <div className="change-summary__stat">
            <span className="change-summary__stat-label">Slice impact</span>
            <span className="change-summary__stat-value">
              {diff.affectedSlices.join(', ')}
            </span>
          </div>
        )}

        {diff.affectedViews.length > 0 && (
          <div className="change-summary__stat">
            <span className="change-summary__stat-label">View impact</span>
            <span className="change-summary__stat-value">
              {diff.affectedViews.join(', ')}
            </span>
          </div>
        )}

        <div className="change-summary__stat">
          <span className="change-summary__stat-label">Re-ingest required</span>
          <span className={`change-summary__stat-value ${diff.regenRequired ? 'change-summary__stat-value--warning' : 'change-summary__stat-value--ok'}`}>
            {diff.regenRequired ? 'YES' : 'NO'}
          </span>
        </div>
      </div>
    </div>
  )
}

function ChangeItem({ change }: { change: SemanticChange }) {
  const config = CHANGE_CONFIG[change.type] || { icon: '?', color: '#6b7280', label: change.type }

  return (
    <div className={`change-summary__change ${change.breaking ? 'change-summary__change--breaking' : ''}`}>
      <span
        className="change-summary__change-icon"
        style={{ color: config.color }}
      >
        {config.icon}
      </span>
      <div className="change-summary__change-content">
        <span className="change-summary__change-label">{config.label}:</span>
        <code className="change-summary__change-target">{change.target}</code>
        {change.before && change.after ? <span className="change-summary__change-values">
            <span className="change-summary__change-before">{change.before}</span>
            <span className="change-summary__change-arrow">→</span>
            <span className="change-summary__change-after">{change.after}</span>
          </span> : null}
      </div>
      {change.breaking ? <span className="change-summary__breaking-badge">BREAKING</span> : null}
    </div>
  )
}
