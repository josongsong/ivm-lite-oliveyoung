/**
 * SimulationPanel (Phase 5: Simulation)
 *
 * 파이프라인 시뮬레이션 결과를 단계별로 시각화.
 * - RawData → ChangeSet → Slices → View
 * - 각 단계의 상태와 출력 표시
 * - 샘플 데이터 편집 및 자동 생성
 */
import { useCallback, useState } from 'react'
import { Button } from '@/shared/ui'
import type {
  SimulationErrorResponse,
  SimulationResultResponse,
  SimulationStageResponse,
} from '../api/types'
import { getStageStatusColor, getStageStatusIcon } from '../hooks/useSimulation'
import './SimulationPanel.css'

interface SimulationPanelProps {
  yaml: string
  result: SimulationResultResponse | null
  isSimulating: boolean
  sampleData: string
  onSampleDataChange: (data: string) => void
  onSimulate: (yaml: string, sampleData: string) => void
  onGenerateSample?: () => void
  isGeneratingSample?: boolean
}

export function SimulationPanel({
  yaml,
  result,
  isSimulating,
  sampleData,
  onSampleDataChange,
  onSimulate,
  onGenerateSample,
  isGeneratingSample,
}: SimulationPanelProps) {
  const [expandedStage, setExpandedStage] = useState<string | null>(null)

  const handleRun = useCallback(() => {
    onSimulate(yaml, sampleData)
  }, [yaml, sampleData, onSimulate])

  const toggleStage = useCallback((stageName: string) => {
    setExpandedStage((prev) => (prev === stageName ? null : stageName))
  }, [])

  return (
    <div className="simulation-panel">
      {/* 상단: 샘플 데이터 편집 */}
      <div className="simulation-panel__input-section">
        <div className="simulation-panel__input-header">
          <span className="simulation-panel__input-title">Sample Data</span>
          <div className="simulation-panel__input-actions">
            {onGenerateSample ? <Button
                variant="secondary"
                size="sm"
                onClick={onGenerateSample}
                disabled={isGeneratingSample}
              >
                {isGeneratingSample ? 'Generating...' : 'Auto-generate'}
              </Button> : null}
          </div>
        </div>
        <textarea
          className="simulation-panel__textarea"
          value={sampleData}
          onChange={(e) => onSampleDataChange(e.target.value)}
          placeholder='{"id": "sample-001", "name": "Sample Item", ...}'
          spellCheck={false}
        />
      </div>

      {/* 실행 버튼 */}
      <div className="simulation-panel__run-section">
        <Button
          variant="primary"
          className="simulation-panel__btn--run"
          onClick={handleRun}
          disabled={isSimulating || !sampleData.trim()}
        >
          {isSimulating ? (
            <>
              <span className="simulation-panel__spinner" />
              Simulating...
            </>
          ) : (
            <>
              <span className="simulation-panel__run-icon">▶</span>
              Run Simulation
            </>
          )}
        </Button>
      </div>

      {/* 결과 */}
      {result ? <div className="simulation-panel__result">
          {/* 파이프라인 스테이지 */}
          <div className="simulation-panel__pipeline">
            {result.stages.map((stage, index) => (
              <StageCard
                key={stage.name}
                stage={stage}
                index={index}
                isLast={index === result.stages.length - 1}
                isExpanded={expandedStage === stage.name}
                onToggle={() => toggleStage(stage.name)}
              />
            ))}
          </div>

          {/* 에러 표시 */}
          {result.errors.length > 0 && (
            <div className="simulation-panel__errors">
              <div className="simulation-panel__errors-header">
                <span className="simulation-panel__errors-icon">⚠</span>
                <span>Errors ({result.errors.length})</span>
              </div>
              <div className="simulation-panel__errors-list">
                {result.errors.map((error, idx) => (
                  <ErrorCard key={idx} error={error} />
                ))}
              </div>
            </div>
          )}

          {/* 최종 출력 */}
          {result.finalOutput ? <div className="simulation-panel__final">
              <div className="simulation-panel__final-header">
                <span className="simulation-panel__final-title">
                  Final View Output
                </span>
                <span className="simulation-panel__final-badge">✓ Success</span>
              </div>
              <pre className="simulation-panel__final-output">
                {formatJson(result.finalOutput)}
              </pre>
            </div> : null}
        </div> : null}

      {/* 빈 상태 */}
      {!result && !isSimulating && (
        <div className="simulation-panel__empty">
          <span className="simulation-panel__empty-icon">🧪</span>
          <span className="simulation-panel__empty-text">
            Enter sample data and run simulation to see pipeline stages
          </span>
        </div>
      )}
    </div>
  )
}

// ==================== 서브 컴포넌트 ====================

interface StageCardProps {
  stage: SimulationStageResponse
  index: number
  isLast: boolean
  isExpanded: boolean
  onToggle: () => void
}

function StageCard({
  stage,
  index,
  isLast,
  isExpanded,
  onToggle,
}: StageCardProps) {
  const statusColor = getStageStatusColor(stage.status)
  const statusIcon = getStageStatusIcon(stage.status)

  return (
    <div className="stage-card">
      <div
        role="button"
        tabIndex={0}
        className={`stage-card__header stage-card__header--${stage.status.toLowerCase()}`}
        onClick={onToggle}
        onKeyDown={(e) => e.key === 'Enter' && onToggle?.()}
        style={{ borderLeftColor: statusColor }}
      >
        <div className="stage-card__header-left">
          <span className="stage-card__number">{index + 1}</span>
          <span className="stage-card__name">{stage.name}</span>
        </div>
        <div className="stage-card__header-right">
          <span
            className="stage-card__status"
            style={{ color: statusColor }}
          >
            {statusIcon}
          </span>
          <span className="stage-card__duration">{stage.durationMs}ms</span>
          <span className="stage-card__expand">{isExpanded ? '−' : '+'}</span>
        </div>
      </div>

      {isExpanded && stage.output ? <div className="stage-card__content">
          <pre className="stage-card__output">{formatJson(stage.output)}</pre>
        </div> : null}

      {!isLast && (
        <div className="stage-card__arrow">
          <span>→</span>
        </div>
      )}
    </div>
  )
}

interface ErrorCardProps {
  error: SimulationErrorResponse
}

function ErrorCard({ error }: ErrorCardProps) {
  return (
    <div className="error-card">
      <div className="error-card__stage">{error.stage}</div>
      <div className="error-card__message">{error.message}</div>
      {error.line !== null && (
        <div className="error-card__line">Line {error.line}</div>
      )}
    </div>
  )
}

// ==================== 유틸 ====================

function formatJson(str: string): string {
  try {
    const parsed = JSON.parse(str)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return str
  }
}
