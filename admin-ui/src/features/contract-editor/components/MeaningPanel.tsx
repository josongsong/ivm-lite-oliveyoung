/**
 * MeaningPanel (Phase 1: DX Platform)
 *
 * 커서 위치의 의미론적 정보 표시.
 * - 현재 노드 타입/이름
 * - 사용처 (RuleSets, Views, Sinks)
 * - 영향 분석 (slicesAffected, regenRequired)
 */
import { Button } from '@/shared/ui'
import type { CursorContextResponse, DependencyRef, UsageInfo } from '../api/types'
import './MeaningPanel.css'

interface MeaningPanelProps {
  context: CursorContextResponse | null
  isLoading: boolean
  onJumpToContract?: (ref: DependencyRef) => void
}

export function MeaningPanel({
  context,
  isLoading,
  onJumpToContract,
}: MeaningPanelProps) {
  if (isLoading) {
    return (
      <div className="meaning-panel meaning-panel--loading">
        <div className="meaning-panel__spinner" />
        <span>분석 중...</span>
      </div>
    )
  }

  if (!context || !context.semanticNode) {
    return (
      <div className="meaning-panel meaning-panel--empty">
        <div className="meaning-panel__empty-icon">?</div>
        <p>커서를 YAML 요소 위에 놓으면 의미가 표시됩니다</p>
      </div>
    )
  }

  const { semanticNode } = context

  return (
    <div className="meaning-panel">
      {/* 헤더: 노드 타입 + 이름 */}
      <div className="meaning-panel__header">
        <span className={`meaning-panel__type meaning-panel__type--${semanticNode.type.toLowerCase()}`}>
          {semanticNode.type}
        </span>
        <span className="meaning-panel__name">{semanticNode.name}</span>
      </div>

      {/* 상세 정보 */}
      <div className="meaning-panel__details">
        {semanticNode.dataType ? <div className="meaning-panel__row">
            <span className="meaning-panel__label">타입</span>
            <code className="meaning-panel__value">{semanticNode.dataType}</code>
          </div> : null}

        {semanticNode.required !== null && (
          <div className="meaning-panel__row">
            <span className="meaning-panel__label">필수</span>
            <span className={`meaning-panel__badge ${semanticNode.required ? 'meaning-panel__badge--required' : ''}`}>
              {semanticNode.required ? 'Yes' : 'No'}
            </span>
          </div>
        )}

        {semanticNode.description ? <div className="meaning-panel__row meaning-panel__row--full">
            <span className="meaning-panel__label">설명</span>
            <p className="meaning-panel__description">{semanticNode.description}</p>
          </div> : null}
      </div>

      {/* AST 경로 */}
      <div className="meaning-panel__section">
        <h4 className="meaning-panel__section-title">AST Path</h4>
        <code className="meaning-panel__path">
          {context.astPath.length > 0 ? context.astPath.join(' > ') : '(root)'}
        </code>
      </div>

      {/* 사용처 */}
      {hasUsages(semanticNode.usedBy) && (
        <div className="meaning-panel__section">
          <h4 className="meaning-panel__section-title">사용처</h4>
          <UsageList
            label="RuleSets"
            refs={semanticNode.usedBy.ruleSets}
            onJump={onJumpToContract}
          />
          <UsageList
            label="Views"
            refs={semanticNode.usedBy.views}
            onJump={onJumpToContract}
          />
          <UsageList
            label="Sinks"
            refs={semanticNode.usedBy.sinks}
            onJump={onJumpToContract}
          />
        </div>
      )}

      {/* 영향 분석 */}
      <div className="meaning-panel__section">
        <h4 className="meaning-panel__section-title">영향 분석</h4>
        <div className="meaning-panel__impact">
          {semanticNode.impact.slicesAffected.length > 0 && (
            <div className="meaning-panel__row">
              <span className="meaning-panel__label">영향받는 Slice</span>
              <div className="meaning-panel__tags">
                {semanticNode.impact.slicesAffected.map((slice) => (
                  <span key={slice} className="meaning-panel__tag">{slice}</span>
                ))}
              </div>
            </div>
          )}
          <div className="meaning-panel__row">
            <span className="meaning-panel__label">재생성 필요</span>
            <span className={`meaning-panel__badge ${semanticNode.impact.regenRequired ? 'meaning-panel__badge--warning' : 'meaning-panel__badge--ok'}`}>
              {semanticNode.impact.regenRequired ? 'Yes' : 'No'}
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}

// Helper: 사용처가 있는지 확인
function hasUsages(usedBy: UsageInfo): boolean {
  if (!usedBy) return false
  return (
    usedBy.ruleSets.length > 0 ||
    usedBy.views.length > 0 ||
    usedBy.sinks.length > 0
  )
}

// 사용처 목록 컴포넌트
function UsageList({
  label,
  refs,
  onJump,
}: {
  label: string
  refs: DependencyRef[]
  onJump?: (ref: DependencyRef) => void
}) {
  if (refs.length === 0) return null

  return (
    <div className="meaning-panel__usage-list">
      <span className="meaning-panel__usage-label">{label}</span>
      <ul className="meaning-panel__usage-items">
        {refs.map((ref) => (
          <li key={`${ref.kind}/${ref.id}`}>
            <Button
              variant="ghost"
              size="sm"
              className="meaning-panel__usage-link"
              onClick={() => onJump?.(ref)}
              title={`${ref.kind}/${ref.id} (${ref.relation})`}
            >
              {ref.id}
            </Button>
          </li>
        ))}
      </ul>
    </div>
  )
}
