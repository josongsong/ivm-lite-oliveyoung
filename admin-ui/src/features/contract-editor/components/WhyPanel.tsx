/**
 * WhyPanel Component (Phase 3: Why Engine)
 *
 * "왜 안 됐지?" 질문에 답변하는 패널.
 */
import { useCallback, useState } from 'react'
import { Button, Chip, ChipGroup } from '@/shared/ui'
import type {
  ContractExplanationResponse,
  ContractRefResponse,
  WhyExplanationResponse,
} from '../api/types'
import { CauseItem } from './CauseItem'
import { ContractSummarySection } from './ContractSummarySection'
import { DependencySection } from './DependencySection'
import './WhyPanel.css'

interface WhyPanelProps {
  explanation: WhyExplanationResponse | null
  contractExplanation?: ContractExplanationResponse
  isAnalyzing: boolean
  isLoadingExplanation: boolean
  onAnalyze: (symptom: string) => void
  onJumpToContract: (ref: ContractRefResponse) => void
}

const SYMPTOM_PRESETS = [
  { label: 'View가 비어있음', value: 'View output is empty' },
  { label: 'Slice 생성 실패', value: 'Slice generation failed' },
  { label: '필드 누락', value: 'Expected field is missing' },
  { label: '타입 불일치', value: 'Type mismatch error' },
  { label: '참조 오류', value: 'Reference not found' },
]

export function WhyPanel({
  explanation,
  contractExplanation,
  isAnalyzing,
  isLoadingExplanation,
  onAnalyze,
  onJumpToContract,
}: WhyPanelProps) {
  const [symptom, setSymptom] = useState('')

  const handleAnalyze = useCallback(() => {
    if (symptom.trim()) {
      onAnalyze(symptom.trim())
    }
  }, [symptom, onAnalyze])

  const handlePresetClick = useCallback(
    (preset: string) => {
      setSymptom(preset)
      onAnalyze(preset)
    },
    [onAnalyze]
  )

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault()
        handleAnalyze()
      }
    },
    [handleAnalyze]
  )

  return (
    <div className="why-panel">
      {contractExplanation ? <ContractSummarySection explanation={contractExplanation} /> : null}

      {isLoadingExplanation ? (
        <div className="why-panel__loading">
          <div className="why-panel__spinner" />
          <span>Contract 정보 로딩 중...</span>
        </div>
      ) : null}

      <section className="why-panel__input-section">
        <h3 className="why-panel__section-title">왜 안 됐지?</h3>
        <div className="why-panel__input-wrapper">
          <textarea
            className="why-panel__textarea"
            value={symptom}
            onChange={(e) => setSymptom(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="증상을 입력하세요... (예: View가 비어있음)"
            rows={2}
          />
          <Button variant="primary" onClick={handleAnalyze} disabled={!symptom.trim() || isAnalyzing}>
            {isAnalyzing ? '분석 중...' : '분석'}
          </Button>
        </div>
        <ChipGroup className="why-panel__presets">
          {SYMPTOM_PRESETS.map((preset) => (
            <Chip key={preset.value} selected={false} onClick={() => handlePresetClick(preset.value)} disabled={isAnalyzing}>
              {preset.label}
            </Chip>
          ))}
        </ChipGroup>
      </section>

      {explanation ? (
        <section className="why-panel__causes">
          <h3 className="why-panel__section-title">
            원인 분석
            {explanation.lastEvaluated ? (
              <span className="why-panel__timestamp">{new Date(explanation.lastEvaluated).toLocaleString()}</span>
            ) : null}
          </h3>
          <div className="why-panel__cause-chain">
            {explanation.causeChain.map((cause, index) => (
              <CauseItem key={index} cause={cause} isLast={index === explanation.causeChain.length - 1} onJumpToContract={onJumpToContract} />
            ))}
          </div>
        </section>
      ) : null}

      {contractExplanation ? (
        <>
          <DependencySection title="의존성" deps={contractExplanation.dependencies} onJump={onJumpToContract} />
          <DependencySection title="참조하는 계약" deps={contractExplanation.dependents} onJump={onJumpToContract} />
        </>
      ) : null}
    </div>
  )
}
