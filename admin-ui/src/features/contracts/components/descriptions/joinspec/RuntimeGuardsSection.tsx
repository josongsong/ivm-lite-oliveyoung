import { AlertTriangle, Shield, Zap } from 'lucide-react'
import { DescriptionSection } from '../DescriptionSection'

interface RuntimeGuardsSectionProps {
  runtimeGuards: Record<string, unknown>
}

export function RuntimeGuardsSection({ runtimeGuards }: RuntimeGuardsSectionProps) {
  return (
    <DescriptionSection title="런타임 안전장치" icon={<Zap size={18} />}>
      <div className="guards-grid">
        {runtimeGuards.forbidTimeSemantics ? (
          <div className="guard-item">
            <AlertTriangle size={16} className="warning" />
            <span>시간 의미론 금지</span>
          </div>
        ) : null}
        {runtimeGuards.forbidMaterializeJoin ? (
          <div className="guard-item">
            <AlertTriangle size={16} className="warning" />
            <span>조인 실체화 금지</span>
          </div>
        ) : null}
        {runtimeGuards.maxReadAttempts !== undefined && (
          <div className="guard-item">
            <Shield size={16} className="info" />
            <span>최대 읽기 시도: {String(runtimeGuards.maxReadAttempts)}회</span>
          </div>
        )}
      </div>
    </DescriptionSection>
  )
}
