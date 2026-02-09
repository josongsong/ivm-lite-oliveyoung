/**
 * LineageGraph Showcase - LineageGraph 컴포넌트 전시
 */
import { Database } from 'lucide-react'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function LineageGraphShowcase() {
  return (
    <div className="showcase">
      <PageHeader
        title="LineageGraph"
        description="데이터 계보를 시각화하는 그래프 컴포넌트입니다."
        stability="stable"
      />

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>LineageGraph</h3>
        <LivePreview>
          <div style={{ border: '1px solid var(--border-color)', borderRadius: '8px', padding: '2rem', minHeight: '300px', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--bg-secondary)' }}>
            <div style={{ textAlign: 'center' }}>
              <Database size={48} style={{ color: 'var(--text-muted)', marginBottom: '1rem' }} />
              <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
                LineageGraph는 실제 데이터가 필요합니다.
                <br />
                Explorer 페이지에서 entityId를 선택하면 계보 그래프가 표시됩니다.
              </p>
            </div>
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>사용 예시</h3>
        <LivePreview>
          <div style={{ border: '1px solid var(--border-color)', borderRadius: '8px', padding: '1rem', background: 'var(--bg-secondary)' }}>
            <pre style={{ margin: 0, fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
{`// Explorer 페이지에서 사용
<LineageGraph
  tenant="oliveyoung"
  entityId="SKU-12345"
/>

// RawData → Ruleset → Slice → ViewDef → View → Sink
// 형태로 데이터 흐름을 시각화합니다`}
            </pre>
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`import { LineageGraph } from '@/shared/ui/recipes'

<LineageGraph
  tenant="oliveyoung"
  entityId="SKU-12345"
/>

// LineageGraph는 React Query를 사용하여
// 자동으로 데이터를 가져오고 시각화합니다.
// 노드 타입별로 다른 아이콘과 색상을 사용합니다.`}
        </pre>
      </section>
    </div>
  )
}
