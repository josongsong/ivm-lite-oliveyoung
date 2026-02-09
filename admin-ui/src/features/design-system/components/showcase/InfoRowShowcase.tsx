/**
 * InfoRow Showcase - InfoRow 컴포넌트 전시
 */
import { InfoRow, InfoList } from '@/shared/ui'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function InfoRowShowcase() {
  return (
    <div className="showcase">
      <PageHeader
        title="InfoRow"
        description="키-값 정보를 표시하는 컴포넌트입니다."
        stability="stable"
      />

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>기본 InfoRow</h3>
        <LivePreview>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <InfoRow label="Status" value="Active" />
            <InfoRow label="ID" value="abc-123-def-456" />
            <InfoRow label="Created" value="2024-01-01 12:00:00" />
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Monospace & Highlight</h3>
        <LivePreview>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <InfoRow label="ID" value="abc-123-def-456" mono />
            <InfoRow label="Duration" value="1.5s" highlight />
            <InfoRow label="Hash" value="a1b2c3d4e5f6" mono highlight />
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Badge & Copyable</h3>
        <LivePreview>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <InfoRow label="Status" value="Active" badge badgeVariant="success" />
            <InfoRow label="Status" value="Pending" badge badgeVariant="warning" />
            <InfoRow label="Status" value="Failed" badge badgeVariant="error" />
            <InfoRow label="ID" value="abc-123-def-456" mono copyable />
            <InfoRow label="URL" value="https://example.com" copyable externalLink="https://example.com" />
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>InfoList</h3>
        <LivePreview>
          <InfoList dividers>
            <InfoRow label="Name" value="Product" />
            <InfoRow label="Price" value="25,000" />
            <InfoRow label="Stock" value="150" />
            <InfoRow label="Status" value="Active" badge badgeVariant="success" />
          </InfoList>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`import { InfoRow, InfoList } from '@/shared/ui'

<InfoRow label="Status" value="Active" />
<InfoRow label="ID" value="abc-123" mono copyable />
<InfoRow label="Duration" value="1.5s" highlight />
<InfoRow label="Status" value="Active" badge badgeVariant="success" />

<InfoList dividers>
  <InfoRow label="Name" value="Product" />
  <InfoRow label="Price" value="25,000" />
</InfoList>`}
        </pre>
      </section>
    </div>
  )
}
