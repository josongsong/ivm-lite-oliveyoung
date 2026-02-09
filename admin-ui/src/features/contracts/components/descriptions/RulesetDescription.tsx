import {
  Database,
  GitBranch,
  Layers,
  Zap
} from 'lucide-react'
import { DescriptionSection } from './DescriptionSection'
import type { DescriptionProps } from './types'

export function RulesetDescription({ parsed }: DescriptionProps) {
  const entityType = parsed.entityType as string | undefined
  const impactMap = parsed.impactMap as Record<string, string[]> | undefined
  const slices = parsed.slices as Array<Record<string, unknown>> | undefined
  const indexes = parsed.indexes as Array<Record<string, unknown>> | undefined

  return (
    <div className="description-container">
      <div className="description-header">
        <div className="description-icon ruleset">
          <GitBranch size={24} />
        </div>
        <div>
          <h3>RuleSet</h3>
          <p className="description-subtitle">
            <strong>{entityType}</strong> 엔티티의 슬라이스 빌드 및 영향 분석 규칙
          </p>
        </div>
      </div>

      {impactMap && Object.keys(impactMap).length > 0 ? (
        <DescriptionSection title="영향 맵 (Impact Map)" icon={<Zap size={18} />}>
          <p className="section-desc">
            데이터 경로 변경 시 영향받는 슬라이스를 정의합니다.
            특정 필드가 변경되면 해당 슬라이스가 재계산됩니다.
          </p>
          <div className="impact-map">
            {Object.entries(impactMap).map(([sliceName, paths]) => (
              <div key={`impact-${sliceName}`} className="impact-item">
                <div className="impact-slice">
                  <span className="slice-tag">{sliceName}</span>
                  <span className="path-count">{paths.length}개 경로</span>
                </div>
                <div className="impact-paths">
                  {paths.slice(0, 3).map((path, idx) => (
                    <code key={`path-${sliceName}-${idx}`}>{path}</code>
                  ))}
                  {paths.length > 3 && (
                    <span className="more-paths">+{paths.length - 3}개 더</span>
                  )}
                </div>
              </div>
            ))}
          </div>
        </DescriptionSection>
      ) : null}

      {slices && slices.length > 0 ? (
        <DescriptionSection title="슬라이스 정의" icon={<Layers size={18} />}>
          <p className="section-desc">
            엔티티를 구성하는 {slices.length}개의 슬라이스가 정의되어 있습니다.
          </p>
          <div className="slice-definitions">
            {slices.map((slice, idx) => (
              <div key={`slice-${String(slice.type)}-${idx}`} className="slice-def-card">
                <div className="slice-def-header">
                  <span className="slice-tag">{String(slice.type)}</span>
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

      {indexes && indexes.length > 0 ? (
        <DescriptionSection title="인덱스 정의" icon={<Database size={18} />}>
          <p className="section-desc">
            조인 및 조회 성능을 위한 인덱스입니다.
          </p>
          <div className="index-list">
            {indexes.map((index, idx) => (
              <div key={`index-${String(index.type)}-${idx}`} className="index-item">
                <div className="index-header">
                  <span className="index-type">{String(index.type)}</span>
                  {index.references ? (
                    <span className="index-ref">→ {String(index.references)}</span>
                  ) : null}
                </div>
                <code className="index-selector">{String(index.selector)}</code>
                {index.maxFanout ? (
                  <span className="index-fanout">최대 팬아웃: {String(index.maxFanout)}</span>
                ) : null}
              </div>
            ))}
          </div>
        </DescriptionSection>
      ) : null}
    </div>
  )
}
