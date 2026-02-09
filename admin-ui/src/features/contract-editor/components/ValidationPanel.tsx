/**
 * ValidationPanel (Phase 1: DX Platform)
 *
 * 다단계 검증 결과 표시.
 * - L0: Syntax (YAML 파싱)
 * - L1: Shape (필수 필드)
 * - L2: Semantic (비즈니스 규칙)
 * - L3: Cross-Ref (참조 무결성)
 */
import { Button } from '@/shared/ui'
import type { ContractValidationResponse, ValidationError, ValidationLevel } from '../api/types'
import './ValidationPanel.css'

interface ValidationPanelProps {
  validation: ContractValidationResponse | null
  isLoading: boolean
  onGoToLine?: (line: number, column: number) => void
  onApplyFix?: (line: number, replacement: string) => void
}

const LEVEL_CONFIG: Record<ValidationLevel, { label: string; color: string; icon: string }> = {
  L0_SYNTAX: { label: 'Syntax', color: '#ef4444', icon: '!' },
  L1_SHAPE: { label: 'Shape', color: '#f97316', icon: '!' },
  L2_SEMANTIC: { label: 'Semantic', color: '#eab308', icon: '~' },
  L3_CROSS_REF: { label: 'Reference', color: '#3b82f6', icon: '?' },
  L4_RUNTIME: { label: 'Runtime', color: '#8b5cf6', icon: '*' },
}

export function ValidationPanel({
  validation,
  isLoading,
  onGoToLine,
  onApplyFix,
}: ValidationPanelProps) {
  if (isLoading) {
    return (
      <div className="validation-panel validation-panel--loading">
        <div className="validation-panel__spinner" />
        <span>검증 중...</span>
      </div>
    )
  }

  if (!validation) {
    return (
      <div className="validation-panel validation-panel--empty">
        <p>YAML을 입력하면 자동으로 검증됩니다</p>
      </div>
    )
  }

  const { valid, errors, warnings } = validation

  return (
    <div className="validation-panel">
      {/* 상태 요약 */}
      <div className={`validation-panel__status ${valid ? 'validation-panel__status--valid' : 'validation-panel__status--invalid'}`}>
        <span className="validation-panel__status-icon">{valid ? '✓' : '✗'}</span>
        <span className="validation-panel__status-text">
          {valid ? '유효한 Contract' : `${errors.length}개 오류 발견`}
        </span>
      </div>

      {/* 에러 목록 */}
      {errors.length > 0 && (
        <div className="validation-panel__errors">
          <h4 className="validation-panel__section-title">오류</h4>
          <ul className="validation-panel__error-list">
            {errors.map((error, index) => (
              <ErrorItem
                key={`${error.line}-${error.column}-${index}`}
                error={error}
                onGoToLine={onGoToLine}
                onApplyFix={onApplyFix}
              />
            ))}
          </ul>
        </div>
      )}

      {/* 경고 목록 */}
      {warnings.length > 0 && (
        <div className="validation-panel__warnings">
          <h4 className="validation-panel__section-title">경고</h4>
          <ul className="validation-panel__warning-list">
            {warnings.map((warning, index) => (
              <li key={index} className="validation-panel__warning-item">
                <span className="validation-panel__warning-icon">⚠</span>
                <span>{warning}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* 검증 통과 시 */}
      {valid && errors.length === 0 && warnings.length === 0 ? <div className="validation-panel__success">
          <p>모든 검증을 통과했습니다.</p>
          <ul className="validation-panel__checks">
            <li>✓ L0: YAML 문법 정상</li>
            <li>✓ L1: 필수 필드 완비</li>
            <li>✓ L2: 비즈니스 규칙 준수</li>
            <li>✓ L3: 참조 무결성 확인</li>
          </ul>
        </div> : null}
    </div>
  )
}

// 에러 항목 컴포넌트
function ErrorItem({
  error,
  onGoToLine,
  onApplyFix,
}: {
  error: ValidationError
  onGoToLine?: (line: number, column: number) => void
  onApplyFix?: (line: number, replacement: string) => void
}) {
  const config = LEVEL_CONFIG[error.level]

  return (
    <li className="validation-panel__error-item">
      <div className="validation-panel__error-header">
        <span
          className="validation-panel__error-level"
          style={{ backgroundColor: `${config.color}20`, color: config.color }}
        >
          {config.label}
        </span>
        <Button
          variant="ghost"
          size="sm"
          className="validation-panel__error-location"
          onClick={() => onGoToLine?.(error.line, error.column)}
          title="해당 위치로 이동"
        >
          L{error.line}:{error.column}
        </Button>
      </div>
      <p className="validation-panel__error-message">{error.message}</p>
      {error.fix ? <Button
          variant="ghost"
          size="sm"
          className="validation-panel__fix-button"
          onClick={() => onApplyFix?.(error.line, error.fix!.replacement)}
          title={error.fix.description}
        >
          ⌘. Quick Fix: {error.fix.description}
        </Button> : null}
    </li>
  )
}
