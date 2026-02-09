/**
 * IconButton Showcase
 */
import { useState } from 'react'
import { Copy, Edit, Plus, Search, Settings, Trash2, X } from 'lucide-react'
import { IconButton, Switch } from '@/shared/ui'
import { PageHeader } from '../layout'
import './IconButtonShowcase.css'

export function IconButtonShowcase() {
  const [size, setSize] = useState<'sm' | 'md' | 'lg'>('md')
  const [variant, setVariant] = useState<'default' | 'ghost' | 'danger'>('default')
  const [disabled, setDisabled] = useState(false)

  return (
    <div className="iconbutton-showcase">
      <PageHeader
        title="IconButton"
        description="아이콘만 있는 버튼 컴포넌트입니다. 툴바, 액션 버튼 등에 사용됩니다."
        stability="stable"
      />

      <section className="showcase-section">
        <div className="showcase-playground">
          <div className="showcase-preview">
            <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
              <IconButton icon={Plus} size={size} variant={variant} disabled={disabled} tooltip="Add" aria-label="Add" />
              <IconButton icon={Edit} size={size} variant={variant} disabled={disabled} tooltip="Edit" aria-label="Edit" />
              <IconButton icon={Copy} size={size} variant={variant} disabled={disabled} tooltip="Copy" aria-label="Copy" />
              <IconButton icon={Search} size={size} variant={variant} disabled={disabled} tooltip="Search" aria-label="Search" />
              <IconButton icon={Settings} size={size} variant={variant} disabled={disabled} tooltip="Settings" aria-label="Settings" />
              <IconButton icon={Trash2} size={size} variant="danger" disabled={disabled} tooltip="Delete" aria-label="Delete" />
              <IconButton icon={X} size={size} variant={variant} disabled={disabled} tooltip="Close" aria-label="Close" />
            </div>
          </div>
          <div className="showcase-controls">
            <h3>Props Playground</h3>
            <div className="control-group">
              <label>Size</label>
              <div className="control-options">
                {(['sm', 'md', 'lg'] as const).map((s) => (
                  <button
                    key={s}
                    onClick={() => setSize(s)}
                    style={{
                      padding: '0.375rem 0.75rem',
                      background: size === s ? 'var(--accent-cyan)' : 'var(--bg-tertiary)',
                      border: 'none',
                      borderRadius: '4px',
                      color: size === s ? '#000' : 'var(--text-secondary)',
                      cursor: 'pointer',
                      fontSize: '0.75rem',
                      fontWeight: 500,
                    }}
                  >
                    {s.toUpperCase()}
                  </button>
                ))}
              </div>
            </div>
            <div className="control-group">
              <label>Variant</label>
              <div className="control-options">
                {(['default', 'ghost', 'danger'] as const).map((v) => (
                  <button
                    key={v}
                    onClick={() => setVariant(v)}
                    style={{
                      padding: '0.375rem 0.75rem',
                      background: variant === v ? 'var(--accent-cyan)' : 'var(--bg-tertiary)',
                      border: 'none',
                      borderRadius: '4px',
                      color: variant === v ? '#000' : 'var(--text-secondary)',
                      cursor: 'pointer',
                      fontSize: '0.75rem',
                      fontWeight: 500,
                    }}
                  >
                    {v}
                  </button>
                ))}
              </div>
            </div>
            <div className="control-row">
              <span>Disabled</span>
              <Switch checked={disabled} onChange={setDisabled} size="sm" />
            </div>
          </div>
        </div>
      </section>

      <section className="showcase-section">
        <h3 style={{ marginBottom: '1rem' }}>Examples</h3>
        <div className="example-grid">
          <div className="example-card">
            <h4>Size Variants</h4>
            <div className="example-preview">
              <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
                <IconButton icon={Settings} size="sm" aria-label="Small" tooltip="Small" />
                <IconButton icon={Settings} size="md" aria-label="Medium" tooltip="Medium" />
                <IconButton icon={Settings} size="lg" aria-label="Large" tooltip="Large" />
              </div>
            </div>
          </div>
          <div className="example-card">
            <h4>Color Variants</h4>
            <div className="example-preview">
              <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
                <IconButton icon={Plus} variant="default" aria-label="Default" />
                <IconButton icon={Edit} variant="primary" aria-label="Primary" />
                <IconButton icon={Trash2} variant="danger" aria-label="Danger" />
                <IconButton icon={Search} variant="ghost" aria-label="Ghost" />
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="showcase-section">
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
          <code>{`<IconButton icon={Settings} size="${size}" variant="${variant}" tooltip="Settings" aria-label="Settings" />`}</code>
        </pre>
      </section>
    </div>
  )
}
