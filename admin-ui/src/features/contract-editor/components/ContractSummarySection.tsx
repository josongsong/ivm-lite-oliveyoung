/**
 * ContractSummarySection Component
 *
 * WhyPanel에서 Contract 요약 정보를 표시하는 섹션
 */
import type { ContractExplanationResponse } from '../api/types'

interface ContractSummarySectionProps {
  explanation: ContractExplanationResponse
}

export function ContractSummarySection({ explanation }: ContractSummarySectionProps) {
  return (
    <section className="why-panel__summary">
      <h3 className="why-panel__section-title">Contract 요약</h3>
      <div className="why-panel__summary-content">
        <p className="why-panel__summary-text">{explanation.summary}</p>
        <p className="why-panel__purpose"><strong>목적:</strong> {explanation.purpose}</p>
        {explanation.inputs.length > 0 && (
          <div className="why-panel__io">
            <span className="why-panel__io-label">입력:</span>
            <span className="why-panel__io-values">{explanation.inputs.join(', ')}</span>
          </div>
        )}
        {explanation.outputs.length > 0 && (
          <div className="why-panel__io">
            <span className="why-panel__io-label">출력:</span>
            <span className="why-panel__io-values">{explanation.outputs.join(', ')}</span>
          </div>
        )}
      </div>
    </section>
  )
}
