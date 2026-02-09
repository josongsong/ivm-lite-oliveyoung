import { Link } from 'react-router-dom'
import {
  CheckCircle,
  Database,
  Info,
  Layers,
  Link2
} from 'lucide-react'
import { DescriptionSection } from './DescriptionSection'
import type { EntitySchemaDescriptionProps } from './types'

export function EntitySchemaDescription({ parsed, allContracts }: EntitySchemaDescriptionProps) {
  const entityType = parsed.entityType as string | undefined
  const fields = parsed.fields as Array<Record<string, unknown>> | undefined
  const ruleSetRef = parsed.ruleSetRef as Record<string, unknown> | undefined

  const requiredFields = fields?.filter(f => f.required) || []
  const optionalFields = fields?.filter(f => !f.required) || []

  const relatedRuleSet = ruleSetRef && allContracts
    ? allContracts.find(
        c => c.kind === 'RULESET' &&
        c.id === String(ruleSetRef.id) &&
        c.parsed?.version === String(ruleSetRef.version)
      )
    : null

  const slices = relatedRuleSet?.parsed?.slices as Array<Record<string, unknown>> | undefined

  return (
    <div className="description-container">
      <div className="description-header">
        <div className="description-icon entity-schema">
          <Database size={24} />
        </div>
        <div>
          <h3>엔티티 스키마</h3>
          <p className="description-subtitle">
            <strong>{entityType || '엔티티'}</strong>의 데이터 구조를 정의합니다
          </p>
        </div>
      </div>

      <DescriptionSection title="엔티티 정보" icon={<Info size={18} />}>
        <p className="prose">
          이 스키마는 <strong>{entityType}</strong> 타입의 엔티티가 가져야 할 필드들을 정의합니다.
          {fields ? (
            <>
              {' '}총 <strong>{fields.length}개</strong>의 필드가 정의되어 있으며,
              이 중 <strong>{requiredFields.length}개</strong>는 필수,
              <strong>{optionalFields.length}개</strong>는 선택 필드입니다.
            </>
          ) : null}
        </p>
      </DescriptionSection>

      {requiredFields.length > 0 && (
        <DescriptionSection title="필수 필드" icon={<CheckCircle size={18} />}>
          <div className="field-grid">
            {requiredFields.map((field, idx) => (
              <div key={`required-${String(field.name)}-${idx}`} className="field-card required">
                <div className="field-header">
                  <code className="field-name">{String(field.name)}</code>
                  <span className="field-type">{String(field.type)}</span>
                </div>
                {field.description ? (
                  <p className="field-description">{String(field.description)}</p>
                ) : null}
              </div>
            ))}
          </div>
        </DescriptionSection>
      )}

      {optionalFields.length > 0 && (
        <DescriptionSection title="선택 필드" icon={<Layers size={18} />}>
          <div className="field-grid">
            {optionalFields.map((field, idx) => (
              <div key={`optional-${String(field.name)}-${idx}`} className="field-card optional">
                <div className="field-header">
                  <code className="field-name">{String(field.name)}</code>
                  <span className="field-type">{String(field.type)}</span>
                </div>
                {field.description ? (
                  <p className="field-description">{String(field.description)}</p>
                ) : null}
                {field.default !== undefined && (
                  <p className="field-default">기본값: <code>{String(field.default)}</code></p>
                )}
              </div>
            ))}
          </div>
        </DescriptionSection>
      )}

      {slices && slices.length > 0 ? (
        <DescriptionSection title="슬라이스 정의" icon={<Layers size={18} />}>
          <p className="section-desc">
            이 엔티티는 <strong>{slices.length}개</strong>의 슬라이스로 구성됩니다.
            {relatedRuleSet ? (
              <Link
                to={`/contracts/RULESET/${encodeURIComponent(relatedRuleSet.id)}`}
                className="ref-link"
              >
                RuleSet 상세 보기 →
              </Link>
            ) : null}
          </p>
          <div className="slice-definitions">
            {slices.map((slice, idx) => (
              <div key={`slice-${String(slice.type)}-${idx}`} className="slice-def-card">
                <div className="slice-def-header">
                  <Link
                    to={`/pipeline?slice=${encodeURIComponent(String(slice.type))}`}
                    className="slice-tag clickable"
                    title={`${String(slice.type)} 슬라이스 상세 보기`}
                  >
                    {String(slice.type)}
                  </Link>
                  {(slice.buildRules as Record<string, unknown>)?.type ? (
                    <span className="build-type">
                      {String((slice.buildRules as Record<string, unknown>).type)}
                    </span>
                  ) : null}
                </div>
                {(slice.buildRules as Record<string, unknown>)?.fields ? (
                  <div className="slice-fields">
                    <span className="field-label">필드:</span>
                    {((slice.buildRules as Record<string, unknown>).fields as string[]).slice(0, 4).map((f, fidx) => (
                      <code key={`field-${f}-${fidx}`}>{f}</code>
                    ))}
                    {((slice.buildRules as Record<string, unknown>).fields as string[]).length > 4 && (
                      <span className="more-fields">
                        +{((slice.buildRules as Record<string, unknown>).fields as string[]).length - 4}
                      </span>
                    )}
                  </div>
                ) : null}
              </div>
            ))}
          </div>
        </DescriptionSection>
      ) : null}

      {ruleSetRef ? (
        <DescriptionSection title="연관 RuleSet" icon={<Link2 size={18} />}>
          <div className="ref-card">
            {relatedRuleSet ? (
              <Link
                to={`/contracts/RULESET/${encodeURIComponent(relatedRuleSet.id)}`}
                className="ref-card-link"
              >
                <span className="ref-id">{String(ruleSetRef.id)}</span>
                <span className="ref-version">v{String(ruleSetRef.version)}</span>
              </Link>
            ) : (
              <>
                <span className="ref-id">{String(ruleSetRef.id)}</span>
                <span className="ref-version">v{String(ruleSetRef.version)}</span>
              </>
            )}
          </div>
        </DescriptionSection>
      ) : null}
    </div>
  )
}
