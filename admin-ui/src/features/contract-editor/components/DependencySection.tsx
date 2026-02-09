/**
 * DependencySection Component
 *
 * WhyPanel에서 의존성/참조하는 계약 목록을 표시하는 섹션
 */
import { Button } from '@/shared/ui'
import type { ContractRefResponse } from '../api/types'

interface DependencySectionProps {
  title: string
  deps: ContractRefResponse[]
  onJump: (ref: ContractRefResponse) => void
}

export function DependencySection({ title, deps, onJump }: DependencySectionProps) {
  if (deps.length === 0) return null

  return (
    <section className="why-panel__relations">
      <h3 className="why-panel__section-title">{title}</h3>
      <div className="why-panel__relation-list">
        {deps.map((dep) => (
          <Button
            key={`${dep.kind}-${dep.id}`}
            variant="ghost"
            size="sm"
            className="why-panel__relation-item"
            onClick={() => onJump(dep)}
          >
            <span className="why-panel__relation-kind">{dep.kind}</span>
            <span className="why-panel__relation-id">{dep.id}</span>
          </Button>
        ))}
      </div>
    </section>
  )
}
