import { AlertTriangle, CheckCircle, Shield } from 'lucide-react'
import { DescriptionSection } from '../DescriptionSection'

interface ConstraintsSectionProps {
  constraints: Record<string, unknown>
}

export function ConstraintsSection({ constraints }: ConstraintsSectionProps) {
  return (
    <DescriptionSection title="제약 조건" icon={<Shield size={18} />}>
      <ul className="constraint-list">
        {constraints.allowedJoinTypes ? (
          <li>
            <strong>허용된 조인 타입:</strong>{' '}
            {Array.isArray(constraints.allowedJoinTypes)
              ? constraints.allowedJoinTypes.join(', ')
              : String(constraints.allowedJoinTypes)}
          </li>
        ) : null}
        {constraints.maxJoinDepth !== undefined && (
          <li>
            <strong>최대 조인 깊이:</strong> {String(constraints.maxJoinDepth)}단계
          </li>
        )}
        {constraints.sourceCardinality ? (
          <li>
            <strong>소스 카디널리티:</strong> {String(constraints.sourceCardinality)}
          </li>
        ) : null}
        {constraints.maxJoinTargetsPerSource !== undefined && (
          <li>
            <strong>소스당 최대 조인 타겟:</strong> {String(constraints.maxJoinTargetsPerSource)}개
          </li>
        )}
      </ul>
      <div className="feature-tags">
        {constraints.forbidJoinChain ? (
          <span className="feature-tag warning">
            <AlertTriangle size={12} />
            체인 조인 금지
          </span>
        ) : null}
        {constraints.forbidCycles ? (
          <span className="feature-tag warning">
            <AlertTriangle size={12} />
            순환 금지
          </span>
        ) : null}
        {constraints.forbidNMJoin ? (
          <span className="feature-tag warning">
            <AlertTriangle size={12} />
            N:M 조인 금지
          </span>
        ) : null}
        {constraints.requireDeterministicResolution ? (
          <span className="feature-tag success">
            <CheckCircle size={12} />
            결정론적 해석 필수
          </span>
        ) : null}
      </div>
    </DescriptionSection>
  )
}
