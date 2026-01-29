// @ts-nocheck - Complex dynamic parsed object handling with many unknown types
import { Link } from 'react-router-dom'
import { 
  AlertTriangle, 
  ArrowRight, 
  BookOpen, 
  CheckCircle, 
  Database, 
  FileOutput, 
  GitBranch, 
  Info,
  Layers,
  Link2,
  Shield,
  Zap
} from 'lucide-react'
import './ContractDescription.css'

interface ContractDescriptionProps {
  kind: string
  parsed: Record<string, unknown>
  allContracts?: Array<{ kind: string; id: string; parsed: Record<string, unknown> }>
}

export function ContractDescription({ kind, parsed, allContracts }: ContractDescriptionProps) {
  const kindLower = kind.toLowerCase().replace(/_/g, '')
  
  switch (kindLower) {
    case 'joinspeccontract':
      return <JoinSpecDescription parsed={parsed} />
    case 'entityschema':
      return <EntitySchemaDescription parsed={parsed} allContracts={allContracts} />
    case 'viewdefinition':
      return <ViewDefinitionDescription parsed={parsed} />
    case 'ruleset':
      return <RulesetDescription parsed={parsed} />
    case 'sinkrule':
      return <SinkRuleDescription parsed={parsed} />
    default:
      return <GenericDescription kind={kind} parsed={parsed} />
  }
}

// ============ JoinSpec Description ============
function JoinSpecDescription({ parsed }: { parsed: Record<string, unknown> }) {
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

      {/* 정의 섹션 */}
      {definition && (
        <DescriptionSection title="정의" icon={<BookOpen size={18} />}>
          <p className="prose">
            {definition.meaning ? (
              <>{String(definition.meaning)}</>
            ) : (
              '이 조인 스펙은 엔티티 간의 관계를 정의합니다.'
            )}
          </p>
          <div className="feature-tags">
            {definition.joinIsNotPersisted && (
              <span className="feature-tag info">
                <Zap size={12} />
                조인 결과 비저장
              </span>
            )}
            {definition.joinResultsMustNotBeMaterialized && (
              <span className="feature-tag info">
                <Shield size={12} />
                실체화 금지
              </span>
            )}
          </div>
        </DescriptionSection>
      )}

      {/* 제약 조건 섹션 */}
      {constraints && (
        <DescriptionSection title="제약 조건" icon={<Shield size={18} />}>
          <ul className="constraint-list">
            {constraints.allowedJoinTypes && (
              <li>
                <strong>허용된 조인 타입:</strong>{' '}
                {Array.isArray(constraints.allowedJoinTypes) 
                  ? constraints.allowedJoinTypes.join(', ')
                  : String(constraints.allowedJoinTypes)}
              </li>
            )}
            {constraints.maxJoinDepth !== undefined && (
              <li>
                <strong>최대 조인 깊이:</strong> {String(constraints.maxJoinDepth)}단계
              </li>
            )}
            {constraints.sourceCardinality && (
              <li>
                <strong>소스 카디널리티:</strong> {String(constraints.sourceCardinality)}
              </li>
            )}
            {constraints.maxJoinTargetsPerSource !== undefined && (
              <li>
                <strong>소스당 최대 조인 타겟:</strong> {String(constraints.maxJoinTargetsPerSource)}개
              </li>
            )}
          </ul>
          <div className="feature-tags">
            {constraints.forbidJoinChain && (
              <span className="feature-tag warning">
                <AlertTriangle size={12} />
                체인 조인 금지
              </span>
            )}
            {constraints.forbidCycles && (
              <span className="feature-tag warning">
                <AlertTriangle size={12} />
                순환 금지
              </span>
            )}
            {constraints.forbidNMJoin && (
              <span className="feature-tag warning">
                <AlertTriangle size={12} />
                N:M 조인 금지
              </span>
            )}
            {constraints.requireDeterministicResolution && (
              <span className="feature-tag success">
                <CheckCircle size={12} />
                결정론적 해석 필수
              </span>
            )}
          </div>
        </DescriptionSection>
      )}

      {/* 입력/타겟 섹션 */}
      {(inputs || target) && (
        <DescriptionSection title="입력 및 타겟" icon={<GitBranch size={18} />}>
          <div className="flow-diagram">
            {inputs && (
              <div className="flow-node source">
                <span className="flow-label">소스</span>
                <div className="flow-content">
                  {(inputs.sourceEntityType as Record<string, unknown>)?.values && (
                    <span>
                      {(
                        (inputs.sourceEntityType as Record<string, unknown>).values as string[]
                      ).join(' / ')}
                    </span>
                  )}
                </div>
              </div>
            )}
            <div className="flow-arrow">
              <ArrowRight size={20} />
            </div>
            {target && (
              <div className="flow-node target">
                <span className="flow-label">타겟</span>
                <div className="flow-content">
                  {(target.targetEntityType as Record<string, unknown>)?.values && (
                    <span>
                      {(
                        (target.targetEntityType as Record<string, unknown>).values as string[]
                      ).join(' / ')}
                    </span>
                  )}
                </div>
              </div>
            )}
          </div>
          {target?.missingPolicy && (
            <p className="note">
              <Info size={14} />
              대상이 없을 경우: <strong>{getMissingPolicyLabel(String((target.missingPolicy as Record<string, unknown>).default || target.missingPolicy))}</strong>
            </p>
          )}
        </DescriptionSection>
      )}

      {/* 런타임 가드 섹션 */}
      {runtimeGuards && (
        <DescriptionSection title="런타임 안전장치" icon={<Zap size={18} />}>
          <div className="guards-grid">
            {runtimeGuards.forbidTimeSemantics && (
              <div className="guard-item">
                <AlertTriangle size={16} className="warning" />
                <span>시간 의미론 금지</span>
              </div>
            )}
            {runtimeGuards.forbidMaterializeJoin && (
              <div className="guard-item">
                <AlertTriangle size={16} className="warning" />
                <span>조인 실체화 금지</span>
              </div>
            )}
            {runtimeGuards.maxReadAttempts !== undefined && (
              <div className="guard-item">
                <Shield size={16} className="info" />
                <span>최대 읽기 시도: {String(runtimeGuards.maxReadAttempts)}회</span>
              </div>
            )}
          </div>
        </DescriptionSection>
      )}

      {/* 결정론 섹션 */}
      {determinism && (
        <DescriptionSection title="결정론 설정" icon={<CheckCircle size={18} />}>
          <ul className="info-list">
            {determinism.canonicalJsonProfile && (
              <li>JSON 표준화 프로필: <code>{String(determinism.canonicalJsonProfile)}</code></li>
            )}
            {determinism.hashAlg && (
              <li>해시 알고리즘: <code>{String(determinism.hashAlg)}</code></li>
            )}
            {(determinism.ordering as Record<string, unknown>)?.joinResult && (
              <li>조인 결과 정렬: <code>{String((determinism.ordering as Record<string, unknown>).joinResult)}</code></li>
            )}
          </ul>
        </DescriptionSection>
      )}
    </div>
  )
}

// ============ EntitySchema Description ============
function EntitySchemaDescription({ 
  parsed, 
  allContracts 
}: { 
  parsed: Record<string, unknown>
  allContracts?: Array<{ kind: string; id: string; parsed: Record<string, unknown> }>
}) {
  const entityType = parsed.entityType as string | undefined
  const fields = parsed.fields as Array<Record<string, unknown>> | undefined
  const ruleSetRef = parsed.ruleSetRef as Record<string, unknown> | undefined

  const requiredFields = fields?.filter(f => f.required) || []
  const optionalFields = fields?.filter(f => !f.required) || []

  // RuleSet에서 슬라이스 정보 가져오기
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

      {/* 엔티티 타입 */}
      <DescriptionSection title="엔티티 정보" icon={<Info size={18} />}>
        <p className="prose">
          이 스키마는 <strong>{entityType}</strong> 타입의 엔티티가 가져야 할 필드들을 정의합니다.
          {fields && (
            <> 총 <strong>{fields.length}개</strong>의 필드가 정의되어 있으며, 
            이 중 <strong>{requiredFields.length}개</strong>는 필수, 
            <strong>{optionalFields.length}개</strong>는 선택 필드입니다.</>
          )}
        </p>
      </DescriptionSection>

      {/* 필수 필드 */}
      {requiredFields.length > 0 && (
        <DescriptionSection title="필수 필드" icon={<CheckCircle size={18} />}>
          <div className="field-grid">
            {requiredFields.map((field, idx) => (
              <div key={idx} className="field-card required">
                <div className="field-header">
                  <code className="field-name">{String(field.name)}</code>
                  <span className="field-type">{String(field.type)}</span>
                </div>
                {field.description && (
                  <p className="field-description">{String(field.description)}</p>
                )}
              </div>
            ))}
          </div>
        </DescriptionSection>
      )}

      {/* 선택 필드 */}
      {optionalFields.length > 0 && (
        <DescriptionSection title="선택 필드" icon={<Layers size={18} />}>
          <div className="field-grid">
            {optionalFields.map((field, idx) => (
              <div key={idx} className="field-card optional">
                <div className="field-header">
                  <code className="field-name">{String(field.name)}</code>
                  <span className="field-type">{String(field.type)}</span>
                </div>
                {field.description && (
                  <p className="field-description">{String(field.description)}</p>
                )}
                {field.default !== undefined && (
                  <p className="field-default">기본값: <code>{String(field.default)}</code></p>
                )}
              </div>
            ))}
          </div>
        </DescriptionSection>
      )}

      {/* 슬라이스 정의 */}
      {slices && slices.length > 0 && (
        <DescriptionSection title="슬라이스 정의" icon={<Layers size={18} />}>
          <p className="section-desc">
            이 엔티티는 <strong>{slices.length}개</strong>의 슬라이스로 구성됩니다.
            {relatedRuleSet && (
              <Link
                to={`/contracts/RULESET/${encodeURIComponent(relatedRuleSet.id)}`}
                className="ref-link"
              >
                RuleSet 상세 보기 →
              </Link>
            )}
          </p>
          <div className="slice-definitions">
            {slices.map((slice, idx) => (
              <div key={idx} className="slice-def-card">
                <div className="slice-def-header">
                  <Link
                    to={`/pipeline?slice=${encodeURIComponent(String(slice.type))}`}
                    className="slice-tag clickable"
                    title={`${slice.type} 슬라이스 상세 보기`}
                  >
                    {String(slice.type)}
                  </Link>
                  {(slice.buildRules as Record<string, unknown>)?.type && (
                    <span className="build-type">
                      {String((slice.buildRules as Record<string, unknown>).type)}
                    </span>
                  )}
                </div>
                {(slice.buildRules as Record<string, unknown>)?.fields && (
                  <div className="slice-fields">
                    <span className="field-label">필드:</span>
                    {((slice.buildRules as Record<string, unknown>).fields as string[]).slice(0, 4).map((f, fidx) => (
                      <code key={fidx}>{f}</code>
                    ))}
                    {((slice.buildRules as Record<string, unknown>).fields as string[]).length > 4 && (
                      <span className="more-fields">
                        +{((slice.buildRules as Record<string, unknown>).fields as string[]).length - 4}
                      </span>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        </DescriptionSection>
      )}

      {/* RuleSet 참조 */}
      {ruleSetRef && (
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
      )}
    </div>
  )
}

// ============ ViewDefinition Description ============
function ViewDefinitionDescription({ parsed }: { parsed: Record<string, unknown> }) {
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

      {/* 뷰 정보 */}
      <DescriptionSection title="뷰 정보" icon={<Info size={18} />}>
        <p className="prose">
          <strong>{viewName}</strong> 뷰는 <strong>{entityType}</strong> 엔티티의 
          특정 관점을 제공합니다. {description}
        </p>
      </DescriptionSection>

      {/* 필수 슬라이스 */}
      {requiredSlices && requiredSlices.length > 0 && (
        <DescriptionSection title="필수 슬라이스" icon={<CheckCircle size={18} />}>
          <p className="section-desc">이 뷰를 구성하기 위해 반드시 필요한 슬라이스입니다.</p>
          <div className="slice-tags">
            {requiredSlices.map((slice, idx) => (
              <Link 
                key={idx} 
                to={`/pipeline?slice=${encodeURIComponent(slice)}`}
                className="slice-tag required clickable"
                title={`${slice} 슬라이스 상세 보기`}
              >
                {slice}
              </Link>
            ))}
          </div>
        </DescriptionSection>
      )}

      {/* 선택 슬라이스 */}
      {optionalSlices && optionalSlices.length > 0 && (
        <DescriptionSection title="선택 슬라이스" icon={<Layers size={18} />}>
          <p className="section-desc">추가 정보를 위해 포함될 수 있는 슬라이스입니다.</p>
          <div className="slice-tags">
            {optionalSlices.map((slice, idx) => (
              <Link 
                key={idx} 
                to={`/pipeline?slice=${encodeURIComponent(slice)}`}
                className="slice-tag optional clickable"
                title={`${slice} 슬라이스 상세 보기`}
              >
                {slice}
              </Link>
            ))}
          </div>
        </DescriptionSection>
      )}

      {/* 정책 */}
      <DescriptionSection title="데이터 정책" icon={<Shield size={18} />}>
        <ul className="policy-list">
          {missingPolicy && (
            <li>
              <strong>누락 정책:</strong> {getMissingPolicyLabel(missingPolicy)}
            </li>
          )}
          {partialPolicy && (
            <>
              <li>
                <strong>부분 응답:</strong> {partialPolicy.allowed ? '허용' : '비허용'}
              </li>
              {partialPolicy.responseMeta && (
                <li>
                  <strong>응답 메타데이터:</strong>
                  <ul className="sub-list">
                    {(partialPolicy.responseMeta as Record<string, boolean>).includeMissingSlices && (
                      <li>누락된 슬라이스 정보 포함</li>
                    )}
                    {(partialPolicy.responseMeta as Record<string, boolean>).includeUsedContracts && (
                      <li>사용된 컨트랙트 정보 포함</li>
                    )}
                  </ul>
                </li>
              )}
            </>
          )}
        </ul>
      </DescriptionSection>

      {/* RuleSet 참조 */}
      {ruleSetRef && (
        <DescriptionSection title="연관 RuleSet" icon={<Link2 size={18} />}>
          <div className="ref-card">
            <span className="ref-id">{String(ruleSetRef.id)}</span>
            <span className="ref-version">v{String(ruleSetRef.version)}</span>
          </div>
        </DescriptionSection>
      )}
    </div>
  )
}

// ============ Ruleset Description ============
function RulesetDescription({ parsed }: { parsed: Record<string, unknown> }) {
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

      {/* 영향 맵 */}
      {impactMap && Object.keys(impactMap).length > 0 && (
        <DescriptionSection title="영향 맵 (Impact Map)" icon={<Zap size={18} />}>
          <p className="section-desc">
            데이터 경로 변경 시 영향받는 슬라이스를 정의합니다. 
            특정 필드가 변경되면 해당 슬라이스가 재계산됩니다.
          </p>
          <div className="impact-map">
            {Object.entries(impactMap).map(([sliceName, paths]) => (
              <div key={sliceName} className="impact-item">
                <div className="impact-slice">
                  <span className="slice-tag">{sliceName}</span>
                  <span className="path-count">{paths.length}개 경로</span>
                </div>
                <div className="impact-paths">
                  {paths.slice(0, 3).map((path, idx) => (
                    <code key={idx}>{path}</code>
                  ))}
                  {paths.length > 3 && (
                    <span className="more-paths">+{paths.length - 3}개 더</span>
                  )}
                </div>
              </div>
            ))}
          </div>
        </DescriptionSection>
      )}

      {/* 슬라이스 정의 */}
      {slices && slices.length > 0 && (
        <DescriptionSection title="슬라이스 정의" icon={<Layers size={18} />}>
          <p className="section-desc">
            엔티티를 구성하는 {slices.length}개의 슬라이스가 정의되어 있습니다.
          </p>
          <div className="slice-definitions">
            {slices.map((slice, idx) => (
              <div key={idx} className="slice-def-card">
                <div className="slice-def-header">
                  <span className="slice-tag">{String(slice.type)}</span>
                  {(slice.buildRules as Record<string, unknown>)?.type && (
                    <span className="build-type">
                      {String((slice.buildRules as Record<string, unknown>).type)}
                    </span>
                  )}
                </div>
                {(slice.buildRules as Record<string, unknown>)?.fields && (
                  <div className="slice-fields">
                    <span className="field-label">필드:</span>
                    {((slice.buildRules as Record<string, unknown>).fields as string[]).slice(0, 4).map((f, fidx) => (
                      <code key={fidx}>{f}</code>
                    ))}
                    {((slice.buildRules as Record<string, unknown>).fields as string[]).length > 4 && (
                      <span className="more-fields">
                        +{((slice.buildRules as Record<string, unknown>).fields as string[]).length - 4}
                      </span>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        </DescriptionSection>
      )}

      {/* 인덱스 */}
      {indexes && indexes.length > 0 && (
        <DescriptionSection title="인덱스 정의" icon={<Database size={18} />}>
          <p className="section-desc">
            조인 및 조회 성능을 위한 인덱스입니다.
          </p>
          <div className="index-list">
            {indexes.map((index, idx) => (
              <div key={idx} className="index-item">
                <div className="index-header">
                  <span className="index-type">{String(index.type)}</span>
                  {index.references && (
                    <span className="index-ref">→ {String(index.references)}</span>
                  )}
                </div>
                <code className="index-selector">{String(index.selector)}</code>
                {index.maxFanout && (
                  <span className="index-fanout">최대 팬아웃: {String(index.maxFanout)}</span>
                )}
              </div>
            ))}
          </div>
        </DescriptionSection>
      )}
    </div>
  )
}

// ============ SinkRule Description ============
function SinkRuleDescription({ parsed }: { parsed: Record<string, unknown> }) {
  const input = parsed.input as Record<string, unknown> | undefined
  const target = parsed.target as Record<string, unknown> | undefined
  const docId = parsed.docId as Record<string, unknown> | undefined
  const fieldMapping = parsed.fieldMapping as Record<string, unknown> | undefined
  const commit = parsed.commit as Record<string, unknown> | undefined

  return (
    <div className="description-container">
      <div className="description-header">
        <div className="description-icon sink-rule">
          <FileOutput size={24} />
        </div>
        <div>
          <h3>Sink Rule</h3>
          <p className="description-subtitle">외부 시스템으로 데이터를 전송하는 규칙</p>
        </div>
      </div>

      {/* 입력 설정 */}
      {input && (
        <DescriptionSection title="입력 (Source)" icon={<Database size={18} />}>
          <div className="sink-flow">
            <div className="sink-source">
              <span className="source-type">{String(input.type)}</span>
              {input.sliceTypes && (
                <div className="source-detail">
                  <span className="detail-label">슬라이스:</span>
                  <div className="slice-tags">
                    {(input.sliceTypes as string[]).map((s, i) => (
                      <Link 
                        key={i} 
                        to={`/pipeline?slice=${encodeURIComponent(String(s))}`}
                        className="slice-tag small clickable"
                        title={`${s} 슬라이스 상세 보기`}
                      >
                        {s}
                      </Link>
                    ))}
                  </div>
                </div>
              )}
              {input.entityTypes && (
                <div className="source-detail">
                  <span className="detail-label">엔티티:</span>
                  <div className="slice-tags">
                    {(input.entityTypes as string[]).map((e, i) => (
                      <Link 
                        key={i} 
                        to={`/contracts?search=${encodeURIComponent(String(e).toLowerCase())}`}
                        className="entity-tag clickable"
                        title={`${e} 엔티티 스키마 보기`}
                      >
                        {e}
                      </Link>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        </DescriptionSection>
      )}

      {/* 타겟 설정 */}
      {target && (
        <DescriptionSection title="타겟 (Destination)" icon={<FileOutput size={18} />}>
          <div className="target-info">
            <div className="target-type-badge">
              {String(target.type)}
            </div>
            <ul className="target-details">
              {target.endpoint && (
                <li>
                  <strong>엔드포인트:</strong>
                  <code>{String(target.endpoint)}</code>
                </li>
              )}
              {target.indexPattern && (
                <li>
                  <strong>인덱스 패턴:</strong>
                  <code>{String(target.indexPattern)}</code>
                </li>
              )}
              {target.auth && (
                <li>
                  <strong>인증:</strong>
                  <span>{String((target.auth as Record<string, unknown>).type)}</span>
                </li>
              )}
            </ul>
          </div>
        </DescriptionSection>
      )}

      {/* 문서 ID */}
      {docId && (
        <DescriptionSection title="문서 ID 패턴" icon={<Link2 size={18} />}>
          <p className="section-desc">멱등성을 보장하기 위한 문서 ID 생성 패턴입니다.</p>
          <code className="doc-id-pattern">{String(docId.pattern)}</code>
        </DescriptionSection>
      )}

      {/* 커밋 설정 */}
      {commit && (
        <DescriptionSection title="커밋 설정" icon={<Zap size={18} />}>
          <div className="commit-settings">
            {commit.batchSize && (
              <div className="commit-item">
                <span className="commit-label">배치 크기</span>
                <span className="commit-value">{String(commit.batchSize)}</span>
              </div>
            )}
            {commit.timeoutMs && (
              <div className="commit-item">
                <span className="commit-label">타임아웃</span>
                <span className="commit-value">{Number(commit.timeoutMs) / 1000}초</span>
              </div>
            )}
          </div>
        </DescriptionSection>
      )}

      {/* 필드 매핑 */}
      {fieldMapping && (
        <DescriptionSection title="필드 매핑" icon={<GitBranch size={18} />}>
          {fieldMapping.enabled ? (
            <p className="section-desc">커스텀 필드 매핑이 활성화되어 있습니다.</p>
          ) : (
            <p className="section-desc">
              <Info size={14} />
              필드 매핑 비활성화 - Slice payload가 그대로 전송됩니다.
            </p>
          )}
        </DescriptionSection>
      )}
    </div>
  )
}

// ============ Generic Description ============
function GenericDescription({ kind, parsed }: { kind: string; parsed: Record<string, unknown> }) {
  return (
    <div className="description-container">
      <div className="description-header">
        <div className="description-icon generic">
          <BookOpen size={24} />
        </div>
        <div>
          <h3>{kind.replace(/_/g, ' ')}</h3>
          <p className="description-subtitle">컨트랙트 정보</p>
        </div>
      </div>

      <DescriptionSection title="기본 정보" icon={<Info size={18} />}>
        <ul className="info-list">
          {parsed.id && <li><strong>ID:</strong> <code>{String(parsed.id)}</code></li>}
          {parsed.version && <li><strong>버전:</strong> {String(parsed.version)}</li>}
          {parsed.status && <li><strong>상태:</strong> {String(parsed.status)}</li>}
        </ul>
      </DescriptionSection>
    </div>
  )
}

// ============ Utility Components ============
function DescriptionSection({ 
  title, 
  icon, 
  children 
}: { 
  title: string
  icon?: React.ReactNode
  children: React.ReactNode 
}) {
  return (
    <div className="description-section">
      <h4 className="section-title">
        {icon}
        {title}
      </h4>
      <div className="section-content">
        {children}
      </div>
    </div>
  )
}

// ============ Utility Functions ============
function getMissingPolicyLabel(policy: string): string {
  const labels: Record<string, string> = {
    'FAIL_CLOSED': '오류 발생 (Fail Closed)',
    'PARTIAL_ALLOWED': '부분 결과 허용',
    'NONE': '정책 없음'
  }
  return labels[policy] || policy
}
