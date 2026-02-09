/**
 * Loading Showcase
 */
import { useState } from 'react'
import { Loading } from '@/shared/ui'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function LoadingShowcase() {
  const [size, setSize] = useState<'sm' | 'md' | 'lg'>('md')

  return (
    <div className="showcase">
      <PageHeader
        title="Loading"
        description="로딩 상태를 표시하는 스피너 컴포넌트입니다."
        stability="stable"
      />

      <LivePreview>
        <div style={{ display: 'flex', gap: '2rem', alignItems: 'center', justifyContent: 'center', minHeight: '100px' }}>
          <Loading size={size} />
        </div>
      </LivePreview>

      <section style={{ marginTop: '1.5rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Size Selector</h3>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          {(['sm', 'md', 'lg'] as const).map((s) => (
            <button
              key={s}
              onClick={() => setSize(s)}
              style={{
                padding: '0.5rem 1rem',
                background: size === s ? 'var(--accent-cyan)' : 'var(--bg-tertiary)',
                border: 'none',
                borderRadius: '6px',
                color: size === s ? '#000' : 'var(--text-secondary)',
                cursor: 'pointer',
                fontSize: '0.8125rem',
                fontWeight: 500,
              }}
            >
              {s.toUpperCase()}
            </button>
          ))}
        </div>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>All Sizes</h3>
        <div style={{ display: 'flex', gap: '3rem', alignItems: 'center', padding: '2rem', background: 'var(--bg-tertiary)', borderRadius: '8px' }}>
          <div style={{ textAlign: 'center' }}>
            <Loading size="sm" />
            <div style={{ marginTop: '0.5rem', fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Small</div>
          </div>
          <div style={{ textAlign: 'center' }}>
            <Loading size="md" />
            <div style={{ marginTop: '0.5rem', fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Medium</div>
          </div>
          <div style={{ textAlign: 'center' }}>
            <Loading size="lg" />
            <div style={{ marginTop: '0.5rem', fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Large</div>
          </div>
        </div>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`<Loading size="${size}" />`}
        </pre>
      </section>
    </div>
  )
}
