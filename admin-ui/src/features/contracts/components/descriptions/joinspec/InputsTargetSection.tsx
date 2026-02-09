import { ArrowRight, GitBranch, Info } from 'lucide-react'
import { DescriptionSection } from '../DescriptionSection'
import { getMissingPolicyLabel } from '../utils'

interface InputsTargetSectionProps {
  inputs?: Record<string, unknown>
  target?: Record<string, unknown>
}

export function InputsTargetSection({ inputs, target }: InputsTargetSectionProps) {
  if (!inputs && !target) return null

  return (
    <DescriptionSection title="입력 및 타겟" icon={<GitBranch size={18} />}>
      <div className="flow-diagram">
        {inputs ? (
          <div className="flow-node source">
            <span className="flow-label">소스</span>
            <div className="flow-content">
              {(inputs.sourceEntityType as Record<string, unknown>)?.values ? (
                <span>
                  {(
                    (inputs.sourceEntityType as Record<string, unknown>).values as string[]
                  ).join(' / ')}
                </span>
              ) : null}
            </div>
          </div>
        ) : null}
        <div className="flow-arrow">
          <ArrowRight size={20} />
        </div>
        {target ? (
          <div className="flow-node target">
            <span className="flow-label">타겟</span>
            <div className="flow-content">
              {(target.targetEntityType as Record<string, unknown>)?.values ? (
                <span>
                  {(
                    (target.targetEntityType as Record<string, unknown>).values as string[]
                  ).join(' / ')}
                </span>
              ) : null}
            </div>
          </div>
        ) : null}
      </div>
      {target?.missingPolicy ? (
        <p className="note">
          <Info size={14} />
          대상이 없을 경우: <strong>{getMissingPolicyLabel(String((target.missingPolicy as Record<string, unknown>).default || target.missingPolicy))}</strong>
        </p>
      ) : null}
    </DescriptionSection>
  )
}
