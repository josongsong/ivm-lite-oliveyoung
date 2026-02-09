import { CheckCircle } from 'lucide-react'
import { DescriptionSection } from '../DescriptionSection'

interface DeterminismSectionProps {
  determinism: Record<string, unknown>
}

export function DeterminismSection({ determinism }: DeterminismSectionProps) {
  return (
    <DescriptionSection title="결정론 설정" icon={<CheckCircle size={18} />}>
      <ul className="info-list">
        {determinism.canonicalJsonProfile ? (
          <li>JSON 표준화 프로필: <code>{String(determinism.canonicalJsonProfile)}</code></li>
        ) : null}
        {determinism.hashAlg ? (
          <li>해시 알고리즘: <code>{String(determinism.hashAlg)}</code></li>
        ) : null}
        {(determinism.ordering as Record<string, unknown>)?.joinResult ? (
          <li>조인 결과 정렬: <code>{String((determinism.ordering as Record<string, unknown>).joinResult)}</code></li>
        ) : null}
      </ul>
    </DescriptionSection>
  )
}
