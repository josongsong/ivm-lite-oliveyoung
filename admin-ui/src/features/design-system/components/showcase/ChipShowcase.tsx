/**
 * Chip Showcase
 */
import { Chip, ChipGroup } from '@/shared/ui'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function ChipShowcase() {
  return (
    <div className="showcase">
      <PageHeader
        title="Chip"
        description="태그, 필터, 선택 항목 등을 표시하는 칩 컴포넌트입니다."
        stability="stable"
      />

      <LivePreview>
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          <Chip>Default</Chip>
          <Chip variant="primary">Primary</Chip>
          <Chip variant="success">Success</Chip>
          <Chip variant="warning">Warning</Chip>
          <Chip variant="error">Error</Chip>
        </div>
      </LivePreview>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Removable Chips</h3>
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          <Chip onRemove={() => {}}>React</Chip>
          <Chip onRemove={() => {}}>TypeScript</Chip>
          <Chip onRemove={() => {}}>Vite</Chip>
        </div>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Chip Group</h3>
        <ChipGroup>
          <Chip variant="primary">Frontend</Chip>
          <Chip variant="primary">Backend</Chip>
          <Chip variant="primary">DevOps</Chip>
          <Chip variant="primary">Design</Chip>
        </ChipGroup>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`<Chip>Default</Chip>
<Chip variant="primary">Primary</Chip>
<Chip variant="success">Success</Chip>

// Removable
<Chip onRemove={() => handleRemove()}>Tag</Chip>

// Group
<ChipGroup>
  <Chip>Item 1</Chip>
  <Chip>Item 2</Chip>
</ChipGroup>`}
        </pre>
      </section>
    </div>
  )
}
