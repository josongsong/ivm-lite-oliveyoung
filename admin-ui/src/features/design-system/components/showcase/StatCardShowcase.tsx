/**
 * StatCard Showcase - StatCard 컴포넌트 전시
 */
import { StatCard } from '@/shared/ui'
import { Activity, Clock, CheckCircle2, AlertTriangle } from 'lucide-react'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function StatCardShowcase() {
  return (
    <div className="showcase">
      <PageHeader
        title="StatCard"
        description="통계를 표시하는 카드 컴포넌트입니다."
        stability="stable"
      />

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>기본 StatCard</h3>
        <LivePreview>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: '1rem', width: '100%' }}>
            <StatCard
              icon={<Activity size={24} />}
              value="42"
              label="Pending"
            />
            <StatCard
              icon={<Clock size={24} />}
              value="12"
              label="Processing"
            />
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Variants</h3>
        <LivePreview>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: '1rem', width: '100%' }}>
            <StatCard
              icon={<CheckCircle2 size={24} />}
              value="98%"
              label="Uptime"
              variant="success"
            />
            <StatCard
              icon={<AlertTriangle size={24} />}
              value="3"
              label="Errors"
              variant="error"
            />
            <StatCard
              icon={<Clock size={24} />}
              value="42"
              label="Pending"
              variant="warning"
            />
            <StatCard
              icon={<Activity size={24} />}
              value="1,234"
              label="Throughput"
              variant="accent"
            />
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`import { StatCard } from '@/shared/ui'
import { Activity, CheckCircle2 } from 'lucide-react'

<StatCard
  icon={<Activity size={24} />}
  value="42"
  label="Pending"
/>

<StatCard
  icon={<CheckCircle2 size={24} />}
  value="98%"
  label="Uptime"
  variant="success"
/>`}
        </pre>
      </section>
    </div>
  )
}
