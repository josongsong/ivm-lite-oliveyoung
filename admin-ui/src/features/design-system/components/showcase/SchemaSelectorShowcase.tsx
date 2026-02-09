/**
 * SchemaSelector Showcase - SchemaSelector 컴포넌트 전시
 */
import { useState } from 'react'
import { SchemaSelector } from '@/shared/ui'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function SchemaSelectorShowcase() {
  const [selectedSchema, setSelectedSchema] = useState<string>('')

  const schemaOptions = [
    { value: 'product-v1', label: 'Product v1' },
    { value: 'product-v2', label: 'Product v2' },
    { value: 'brand-v1', label: 'Brand v1' },
    { value: 'category-v1', label: 'Category v1' },
  ]

  return (
    <div className="showcase">
      <PageHeader
        title="SchemaSelector"
        description="스키마를 선택하는 드롭다운 컴포넌트입니다."
        stability="stable"
      />

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>기본 SchemaSelector</h3>
        <LivePreview>
          <div style={{ width: '100%', maxWidth: '300px' }}>
            <SchemaSelector
              options={schemaOptions}
              value={selectedSchema}
              onChange={setSelectedSchema}
              placeholder="스키마 선택..."
            />
            {selectedSchema && (
              <div style={{ marginTop: '0.5rem', fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                선택된 스키마: {selectedSchema}
              </div>
            )}
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>수동 입력 허용</h3>
        <LivePreview>
          <div style={{ width: '100%', maxWidth: '300px' }}>
            <SchemaSelector
              options={schemaOptions}
              value={selectedSchema}
              onChange={setSelectedSchema}
              allowManualInput
              onManualInput={(value) => {
                console.log('Manual input:', value)
              }}
              placeholder="스키마 선택 또는 입력..."
            />
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`import { SchemaSelector } from '@/shared/ui'

const schemaOptions = [
  { value: 'product-v1', label: 'Product v1' },
  { value: 'product-v2', label: 'Product v2' },
]

const [selectedSchema, setSelectedSchema] = useState('')

<SchemaSelector
  options={schemaOptions}
  value={selectedSchema}
  onChange={setSelectedSchema}
  placeholder="스키마 선택..."
/>`}
        </pre>
      </section>
    </div>
  )
}
