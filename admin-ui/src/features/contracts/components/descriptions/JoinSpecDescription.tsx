import { BookOpen, Link2, Shield, Zap } from 'lucide-react'
import { DescriptionSection } from './DescriptionSection'
import type { DescriptionProps } from './types'
import {
  ConstraintsSection,
  DeterminismSection,
  InputsTargetSection,
  RuntimeGuardsSection
} from './joinspec'

export function JoinSpecDescription({ parsed }: DescriptionProps) {
  const definition = parsed.definition as Record<string, unknown> | undefined
  const constraints = parsed.constraints as Record<string, unknown> | undefined
  const inputs = parsed.inputs as Record<string, unknown> | undefined
  const target = parsed.target as Record<string, unknown> | undefined
  const determinism = parsed.determinism as Record<string, unknown> | undefined
  const runtimeGuards = parsed.runtimeGuards as Record<string, unknown> | undefined

  return (
    <div className="description-container">
      <div className="description-header">
        <div className="description-icon join-spec">
          <Link2 size={24} />
        </div>
        <div>
          <h3>조인 스펙 컨트랙트</h3>
          <p className="description-subtitle">슬라이스 컴파일을 위한 조인 규칙을 정의합니다</p>
        </div>
      </div>

      {definition ? (
        <DescriptionSection title="정의" icon={<BookOpen size={18} />}>
          <p className="prose">
            {definition.meaning ? (
              <>{String(definition.meaning)}</>
            ) : (
              '이 조인 스펙은 엔티티 간의 관계를 정의합니다.'
            )}
          </p>
          <div className="feature-tags">
            {definition.joinIsNotPersisted ? (
              <span className="feature-tag info">
                <Zap size={12} />
                조인 결과 비저장
              </span>
            ) : null}
            {definition.joinResultsMustNotBeMaterialized ? (
              <span className="feature-tag info">
                <Shield size={12} />
                실체화 금지
              </span>
            ) : null}
          </div>
        </DescriptionSection>
      ) : null}

      {constraints ? <ConstraintsSection constraints={constraints} /> : null}

      <InputsTargetSection inputs={inputs} target={target} />

      {runtimeGuards ? <RuntimeGuardsSection runtimeGuards={runtimeGuards} /> : null}

      {determinism ? <DeterminismSection determinism={determinism} /> : null}
    </div>
  )
}
