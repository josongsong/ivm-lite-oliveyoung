/**
 * DiffViewer Showcase - DiffViewer 컴포넌트 전시
 */
import { DiffViewer } from '@/shared/ui/recipes'
import type { VersionDiff } from '@/shared/types'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function DiffViewerShowcase() {
  const sampleDiffs: VersionDiff[] = [
    { type: 'added', path: 'name', newValue: 'New Product' },
    { type: 'removed', path: 'oldField', oldValue: 'Old Value' },
    { type: 'changed', path: 'price', oldValue: 20000, newValue: 25000 },
    { type: 'added', path: 'tags', newValue: ['beauty', 'skincare'] },
  ]

  const emptyDiffs: VersionDiff[] = []

  return (
    <div className="showcase">
      <PageHeader
        title="DiffViewer"
        description="버전 간 변경사항을 비교하여 표시하는 컴포넌트입니다."
        stability="stable"
      />

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>기본 DiffViewer</h3>
        <LivePreview>
          <div style={{ border: '1px solid var(--border-color)', borderRadius: '8px', padding: '1rem' }}>
            <DiffViewer
              fromVersion={1}
              toVersion={2}
              diffs={sampleDiffs}
            />
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>변경사항 없음</h3>
        <LivePreview>
          <div style={{ border: '1px solid var(--border-color)', borderRadius: '8px', padding: '1rem' }}>
            <DiffViewer
              fromVersion={1}
              toVersion={2}
              diffs={emptyDiffs}
            />
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`import { DiffViewer } from '@/shared/ui/recipes'
import type { VersionDiff } from '@/shared/types'

const diffs: VersionDiff[] = [
  { type: 'added', path: 'name', newValue: 'New Product' },
  { type: 'removed', path: 'oldField', oldValue: 'Old Value' },
  { type: 'changed', path: 'price', oldValue: 20000, newValue: 25000 },
]

<DiffViewer
  fromVersion={1}
  toVersion={2}
  diffs={diffs}
/>`}
        </pre>
      </section>
    </div>
  )
}
