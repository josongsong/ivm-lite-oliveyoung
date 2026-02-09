import { BookOpen, Info } from 'lucide-react'
import { DescriptionSection } from './DescriptionSection'
import type { GenericDescriptionProps } from './types'

export function GenericDescription({ kind, parsed }: GenericDescriptionProps) {
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
          {parsed.id ? (
            <li><strong>ID:</strong> <code>{String(parsed.id)}</code></li>
          ) : null}
          {parsed.version ? (
            <li><strong>버전:</strong> {String(parsed.version)}</li>
          ) : null}
          {parsed.status ? (
            <li><strong>상태:</strong> {String(parsed.status)}</li>
          ) : null}
        </ul>
      </DescriptionSection>
    </div>
  )
}
