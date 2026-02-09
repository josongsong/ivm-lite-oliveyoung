/**
 * CauseItem Component
 *
 * WhyPanel의 Cause Chain 개별 항목 렌더링
 */
import { Button } from '@/shared/ui'
import type { CauseResponse, ContractRefResponse } from '../api/types'
import './CauseItem.css'

interface CauseItemProps {
  cause: CauseResponse
  isLast: boolean
  onJumpToContract: (ref: ContractRefResponse) => void
}

function CauseComparison({ expected, actual }: { expected?: string | null; actual?: string | null }) {
  if (!expected && !actual) return null

  return (
    <div className="cause-item__comparison">
      {expected ? (
        <div className="cause-item__expected">
          <span className="cause-item__label">예상:</span>
          <code>{expected}</code>
        </div>
      ) : null}
      {actual ? (
        <div className="cause-item__actual">
          <span className="cause-item__label">실제:</span>
          <code>{actual}</code>
        </div>
      ) : null}
    </div>
  )
}

function CauseContractLink({
  contract,
  onJump,
}: {
  contract: ContractRefResponse
  onJump: (ref: ContractRefResponse) => void
}) {
  return (
    <Button
      variant="ghost"
      size="sm"
      className="cause-item__contract-link"
      onClick={() => onJump(contract)}
    >
      {contract.kind} / {contract.id}
    </Button>
  )
}

function CauseFixSuggestion({ suggestion }: { suggestion: string }) {
  return (
    <div className="cause-item__fix">
      <span className="cause-item__fix-icon">💡</span>
      <span className="cause-item__fix-text">{suggestion}</span>
    </div>
  )
}

export function CauseItem({ cause, isLast, onJumpToContract }: CauseItemProps) {
  return (
    <div className={`cause-item ${isLast ? 'cause-item--last' : ''}`}>
      <div className="cause-item__connector">
        <div className="cause-item__number">{cause.order}</div>
        {!isLast && <div className="cause-item__line" />}
      </div>
      <div className="cause-item__content">
        <p className="cause-item__description">{cause.description}</p>

        <CauseComparison expected={cause.expected} actual={cause.actual} />

        {cause.relatedContract ? (
          <CauseContractLink contract={cause.relatedContract} onJump={onJumpToContract} />
        ) : null}

        {cause.fixSuggestion ? <CauseFixSuggestion suggestion={cause.fixSuggestion} /> : null}
      </div>
    </div>
  )
}
