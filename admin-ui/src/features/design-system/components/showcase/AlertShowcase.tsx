/**
 * Alert Showcase
 */
import { useState } from 'react'
import { Alert, Banner, InlineAlert } from '@/shared/ui'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function AlertShowcase() {
  const [variant, setVariant] = useState<'info' | 'success' | 'warning' | 'error'>('info')

  return (
    <div className="showcase">
      <PageHeader
        title="Alert"
        description="사용자에게 중요한 정보를 알려주는 알림 컴포넌트입니다."
        stability="stable"
      />

      <LivePreview>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', width: '100%' }}>
          <Alert variant={variant}>
            This is a {variant} alert message.
          </Alert>
        </div>
      </LivePreview>

      <section style={{ marginTop: '1.5rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Variant Selector</h3>
        <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
          {(['info', 'success', 'warning', 'error'] as const).map((v) => (
            <button
              key={v}
              onClick={() => setVariant(v)}
              style={{
                padding: '0.5rem 1rem',
                background: variant === v ? 'var(--accent-cyan)' : 'var(--bg-tertiary)',
                border: 'none',
                borderRadius: '6px',
                color: variant === v ? '#000' : 'var(--text-secondary)',
                cursor: 'pointer',
                fontSize: '0.8125rem',
                fontWeight: 500,
              }}
            >
              {v}
            </button>
          ))}
        </div>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>All Variants</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          <Alert variant="info">Info: This is an informational message.</Alert>
          <Alert variant="success">Success: Operation completed successfully!</Alert>
          <Alert variant="warning">Warning: Please review before proceeding.</Alert>
          <Alert variant="error">Error: Something went wrong.</Alert>
        </div>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>With Title</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          <Alert variant="success" title="Saved!">
            Your changes have been saved successfully.
          </Alert>
          <Alert variant="warning" title="Attention">
            This action cannot be undone.
          </Alert>
        </div>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Banner</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          <Banner variant="info" centered>
            This is a banner message that spans the full width.
          </Banner>
          <Banner variant="success" title="Success!" centered>
            Operation completed successfully.
          </Banner>
        </div>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>InlineAlert</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          <InlineAlert variant="info">Inline info alert</InlineAlert>
          <InlineAlert variant="success">Inline success alert</InlineAlert>
          <InlineAlert variant="warning">Inline warning alert</InlineAlert>
          <InlineAlert variant="error">Inline error alert</InlineAlert>
        </div>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`<Alert variant="${variant}">
  Your message here
</Alert>

<Alert variant="success" title="Saved!">
  Your changes have been saved.
</Alert>

<Banner variant="info" centered>
  Banner message
</Banner>

<InlineAlert variant="info">
  Inline alert message
</InlineAlert>`}
        </pre>
      </section>
    </div>
  )
}
