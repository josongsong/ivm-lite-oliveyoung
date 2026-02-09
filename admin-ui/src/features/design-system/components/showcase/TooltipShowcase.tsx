/**
 * Tooltip Showcase
 */
import { Button, Tooltip } from '@/shared/ui'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function TooltipShowcase() {
  return (
    <div className="showcase">
      <PageHeader
        title="Tooltip"
        description="호버 시 추가 정보를 표시하는 툴팁 컴포넌트입니다."
        stability="stable"
      />

      <LivePreview>
        <div style={{ display: 'flex', gap: '2rem', justifyContent: 'center', padding: '2rem' }}>
          <Tooltip content="This is a tooltip!">
            <Button variant="secondary">Hover me</Button>
          </Tooltip>
        </div>
      </LivePreview>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Placements</h3>
        <div style={{ display: 'flex', gap: '2rem', justifyContent: 'center', padding: '3rem', flexWrap: 'wrap' }}>
          <Tooltip content="Top tooltip" placement="top">
            <Button variant="ghost" size="sm">Top</Button>
          </Tooltip>
          <Tooltip content="Bottom tooltip" placement="bottom">
            <Button variant="ghost" size="sm">Bottom</Button>
          </Tooltip>
          <Tooltip content="Left tooltip" placement="left">
            <Button variant="ghost" size="sm">Left</Button>
          </Tooltip>
          <Tooltip content="Right tooltip" placement="right">
            <Button variant="ghost" size="sm">Right</Button>
          </Tooltip>
        </div>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`<Tooltip content="Tooltip text" placement="top">
  <Button>Hover me</Button>
</Tooltip>`}
        </pre>
      </section>
    </div>
  )
}
