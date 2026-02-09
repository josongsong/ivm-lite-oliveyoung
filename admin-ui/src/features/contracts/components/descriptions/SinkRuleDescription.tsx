import { Link } from 'react-router-dom'
import {
  Database,
  FileOutput,
  GitBranch,
  Info,
  Link2,
  Zap
} from 'lucide-react'
import { DescriptionSection } from './DescriptionSection'
import type { DescriptionProps } from './types'

export function SinkRuleDescription({ parsed }: DescriptionProps) {
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

      {input ? (
        <DescriptionSection title="입력 (Source)" icon={<Database size={18} />}>
          <div className="sink-flow">
            <div className="sink-source">
              <span className="source-type">{String(input.type)}</span>
              {input.sliceTypes ? (
                <div className="source-detail">
                  <span className="detail-label">슬라이스:</span>
                  <div className="slice-tags">
                    {(input.sliceTypes as string[]).map((s) => (
                      <Link
                        key={`input-slice-${s}`}
                        to={`/pipeline?slice=${encodeURIComponent(String(s))}`}
                        className="slice-tag small clickable"
                        title={`${s} 슬라이스 상세 보기`}
                      >
                        {s}
                      </Link>
                    ))}
                  </div>
                </div>
              ) : null}
              {input.entityTypes ? (
                <div className="source-detail">
                  <span className="detail-label">엔티티:</span>
                  <div className="slice-tags">
                    {(input.entityTypes as string[]).map((e) => (
                      <Link
                        key={`input-entity-${e}`}
                        to={`/contracts?search=${encodeURIComponent(String(e).toLowerCase())}`}
                        className="entity-tag clickable"
                        title={`${e} 엔티티 스키마 보기`}
                      >
                        {e}
                      </Link>
                    ))}
                  </div>
                </div>
              ) : null}
            </div>
          </div>
        </DescriptionSection>
      ) : null}

      {target ? (
        <DescriptionSection title="타겟 (Destination)" icon={<FileOutput size={18} />}>
          <div className="target-info">
            <div className="target-type-badge">
              {String(target.type)}
            </div>
            <ul className="target-details">
              {target.endpoint ? (
                <li>
                  <strong>엔드포인트:</strong>
                  <code>{String(target.endpoint)}</code>
                </li>
              ) : null}
              {target.indexPattern ? (
                <li>
                  <strong>인덱스 패턴:</strong>
                  <code>{String(target.indexPattern)}</code>
                </li>
              ) : null}
              {target.auth ? (
                <li>
                  <strong>인증:</strong>
                  <span>{String((target.auth as Record<string, unknown>).type)}</span>
                </li>
              ) : null}
            </ul>
          </div>
        </DescriptionSection>
      ) : null}

      {docId ? (
        <DescriptionSection title="문서 ID 패턴" icon={<Link2 size={18} />}>
          <p className="section-desc">멱등성을 보장하기 위한 문서 ID 생성 패턴입니다.</p>
          <code className="doc-id-pattern">{String(docId.pattern)}</code>
        </DescriptionSection>
      ) : null}

      {commit ? (
        <DescriptionSection title="커밋 설정" icon={<Zap size={18} />}>
          <div className="commit-settings">
            {commit.batchSize ? (
              <div className="commit-item">
                <span className="commit-label">배치 크기</span>
                <span className="commit-value">{String(commit.batchSize)}</span>
              </div>
            ) : null}
            {commit.timeoutMs ? (
              <div className="commit-item">
                <span className="commit-label">타임아웃</span>
                <span className="commit-value">{Number(commit.timeoutMs) / 1000}초</span>
              </div>
            ) : null}
          </div>
        </DescriptionSection>
      ) : null}

      {fieldMapping ? (
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
      ) : null}
    </div>
  )
}
