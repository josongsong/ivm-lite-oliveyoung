/**
 * Card Showcase
 */
import { Card, StatsCard, StatsGrid } from '@/shared/ui'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function CardShowcase() {
  return (
    <div className="showcase">
      <PageHeader
        title="Card"
        description="콘텐츠를 그룹화하는 카드 컨테이너입니다."
        stability="stable"
      />

      <LivePreview>
        <Card>
          <h3 style={{ marginBottom: '0.5rem' }}>Card Title</h3>
          <p style={{ color: 'var(--text-secondary)', margin: 0 }}>
            This is the card content. Cards are used to group related content together.
          </p>
        </Card>
      </LivePreview>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Stats Card</h3>
        <StatsGrid>
          <StatsCard
            title="Total Users"
            value="12,345"
            trend={{ value: 12.5, direction: 'up' }}
          />
          <StatsCard
            title="Revenue"
            value="$45,678"
            trend={{ value: 8.2, direction: 'up' }}
          />
          <StatsCard
            title="Active Sessions"
            value="1,234"
            trend={{ value: 3.1, direction: 'down' }}
          />
          <StatsCard
            title="Conversion Rate"
            value="3.45%"
          />
        </StatsGrid>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`<Card>
  <h3>Card Title</h3>
  <p>Card content</p>
</Card>

<StatsCard
  title="Total Users"
  value="12,345"
  trend={{ value: 12.5, direction: 'up' }}
/>`}
        </pre>
      </section>
    </div>
  )
}
