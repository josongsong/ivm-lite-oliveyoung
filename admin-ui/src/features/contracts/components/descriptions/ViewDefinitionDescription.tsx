import { Link } from 'react-router-dom'
import {
  CheckCircle,
  Info,
  Layers,
  Link2,
  Shield
} from 'lucide-react'
import { DescriptionSection } from './DescriptionSection'
import { getMissingPolicyLabel } from './utils'
import type { DescriptionProps } from './types'

export function ViewDefinitionDescription({ parsed }: DescriptionProps) {
  const viewName = parsed.viewName as string | undefined
  const entityType = parsed.entityType as string | undefined
  const description = parsed.description as string | undefined
  const requiredSlices = parsed.requiredSlices as string[] | undefined
  const optionalSlices = parsed.optionalSlices as string[] | undefined
  const missingPolicy = parsed.missingPolicy as string | undefined
  const partialPolicy = parsed.partialPolicy as Record<string, unknown> | undefined
  const ruleSetRef = parsed.ruleSetRef as Record<string, unknown> | undefined

  return (
    <div className="description-container">
      <div className="description-header">
        <div className="description-icon view-definition">
          <Layers size={24} />
        </div>
        <div>
          <h3>뷰 정의</h3>
          <p className="description-subtitle">{description || '데이터 뷰를 정의합니다'}</p>
        </div>
      </div>

      <DescriptionSection title="뷰 정보" icon={<Info size={18} />}>
        <p className="prose">
          <strong>{viewName}</strong> 뷰는 <strong>{entityType}</strong> 엔티티의
          특정 관점을 제공합니다. {description}
        </p>
      </DescriptionSection>

      {requiredSlices && requiredSlices.length > 0 ? (
        <DescriptionSection title="필수 슬라이스" icon={<CheckCircle size={18} />}>
          <p className="section-desc">이 뷰를 구성하기 위해 반드시 필요한 슬라이스입니다.</p>
          <div className="slice-tags">
            {requiredSlices.map((slice) => (
              <Link
                key={`required-slice-${slice}`}
                to={`/pipeline?slice=${encodeURIComponent(slice)}`}
                className="slice-tag required clickable"
                title={`${slice} 슬라이스 상세 보기`}
              >
                {slice}
              </Link>
            ))}
          </div>
        </DescriptionSection>
      ) : null}

      {optionalSlices && optionalSlices.length > 0 ? (
        <DescriptionSection title="선택 슬라이스" icon={<Layers size={18} />}>
          <p className="section-desc">추가 정보를 위해 포함될 수 있는 슬라이스입니다.</p>
          <div className="slice-tags">
            {optionalSlices.map((slice) => (
              <Link
                key={`optional-slice-${slice}`}
                to={`/pipeline?slice=${encodeURIComponent(slice)}`}
                className="slice-tag optional clickable"
                title={`${slice} 슬라이스 상세 보기`}
              >
                {slice}
              </Link>
            ))}
          </div>
        </DescriptionSection>
      ) : null}

      <DescriptionSection title="데이터 정책" icon={<Shield size={18} />}>
        <ul className="policy-list">
          {missingPolicy ? (
            <li>
              <strong>누락 정책:</strong> {getMissingPolicyLabel(missingPolicy)}
            </li>
          ) : null}
          {partialPolicy ? (
            <>
              <li>
                <strong>부분 응답:</strong> {partialPolicy.allowed ? '허용' : '비허용'}
              </li>
              {partialPolicy.responseMeta ? (
                <li>
                  <strong>응답 메타데이터:</strong>
                  <ul className="sub-list">
                    {(partialPolicy.responseMeta as Record<string, boolean>).includeMissingSlices ? (
                      <li>누락된 슬라이스 정보 포함</li>
                    ) : null}
                    {(partialPolicy.responseMeta as Record<string, boolean>).includeUsedContracts ? (
                      <li>사용된 컨트랙트 정보 포함</li>
                    ) : null}
                  </ul>
                </li>
              ) : null}
            </>
          ) : null}
        </ul>
      </DescriptionSection>

      {ruleSetRef ? (
        <DescriptionSection title="연관 RuleSet" icon={<Link2 size={18} />}>
          <div className="ref-card">
            <span className="ref-id">{String(ruleSetRef.id)}</span>
            <span className="ref-version">v{String(ruleSetRef.version)}</span>
          </div>
        </DescriptionSection>
      ) : null}
    </div>
  )
}
