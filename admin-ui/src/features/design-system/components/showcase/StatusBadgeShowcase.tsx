/**
 * StatusBadge Showcase
 */
import { StatusBadge } from '@/shared/ui'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function StatusBadgeShowcase() {
  return (
    <div className="showcase">
      <PageHeader
        title="StatusBadge"
        description="상태를 시각적으로 표시하는 배지 컴포넌트입니다."
        stability="stable"
      />

      <LivePreview>
        <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
          <StatusBadge status="healthy" />
          <StatusBadge status="pending" />
          <StatusBadge status="failed" />
          <StatusBadge status="processing" />
          <StatusBadge status="completed" />
          <StatusBadge status="paused" />
        </div>
      </LivePreview>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Use Cases</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', padding: '1rem', background: 'var(--bg-tertiary)', borderRadius: '8px' }}>
            <span style={{ minWidth: '100px' }}>Order #1234</span>
            <StatusBadge status="completed" />
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', padding: '1rem', background: 'var(--bg-tertiary)', borderRadius: '8px' }}>
            <span style={{ minWidth: '100px' }}>Order #1235</span>
            <StatusBadge status="processing" />
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', padding: '1rem', background: 'var(--bg-tertiary)', borderRadius: '8px' }}>
            <span style={{ minWidth: '100px' }}>Order #1236</span>
            <StatusBadge status="pending" />
          </div>
        </div>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`<StatusBadge status="healthy" />
<StatusBadge status="pending" />
<StatusBadge status="failed" />
<StatusBadge status="processing" />
<StatusBadge status="completed" />
<StatusBadge status="paused" />`}
        </pre>
      </section>
    </div>
  )
}
