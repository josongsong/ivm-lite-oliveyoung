/**
 * PanelHeader Showcase - PanelHeader 컴포넌트 전시
 */
import { PanelHeader, Button } from '@/shared/ui'
import { Inbox, ArrowRight } from 'lucide-react'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function PanelHeaderShowcase() {
  return (
    <div className="showcase">
      <PageHeader
        title="PanelHeader"
        description="패널 헤더 컴포넌트입니다."
        stability="stable"
      />

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>기본 PanelHeader</h3>
        <LivePreview>
          <PanelHeader
            icon={<Inbox size={18} />}
            title="Outbox Queue"
          />
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>링크와 함께</h3>
        <LivePreview>
          <PanelHeader
            icon={<Inbox size={18} />}
            title="Outbox Queue"
            link={{
              to: '/outbox',
              label: '상세보기',
            }}
          />
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>액션 버튼과 함께</h3>
        <LivePreview>
          <PanelHeader
            icon={<Inbox size={18} />}
            title="Outbox Queue"
            actions={
              <Button variant="ghost" size="sm" icon={<ArrowRight size={14} />}>
                View All
              </Button>
            }
          />
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`import { PanelHeader, Button } from '@/shared/ui'
import { Inbox, ArrowRight } from 'lucide-react'

<PanelHeader
  icon={<Inbox size={18} />}
  title="Outbox Queue"
  link={{
    to: '/outbox',
    label: '상세보기',
  }}
/>

<PanelHeader
  icon={<Inbox size={18} />}
  title="Outbox Queue"
  actions={<Button variant="ghost">View All</Button>}
/>`}
        </pre>
      </section>
    </div>
  )
}
