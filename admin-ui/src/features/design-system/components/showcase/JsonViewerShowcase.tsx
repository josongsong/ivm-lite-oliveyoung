/**
 * JsonViewer Showcase - JsonViewer 컴포넌트 전시
 */
import { JsonViewer } from '@/shared/ui/recipes'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function JsonViewerShowcase() {
  const sampleData = {
    name: 'Product',
    price: 25000,
    stock: 150,
    tags: ['beauty', 'skincare'],
    metadata: {
      created: '2024-01-01',
      updated: '2024-01-02',
      author: {
        name: 'Admin',
        email: 'admin@example.com',
      },
    },
  }

  const complexData = {
    id: '123',
    name: 'Complex Object',
    items: [
      { id: 1, name: 'Item 1', value: 100 },
      { id: 2, name: 'Item 2', value: 200 },
    ],
    nested: {
      level1: {
        level2: {
          level3: 'Deep nested value',
        },
      },
    },
  }

  return (
    <div className="showcase">
      <PageHeader
        title="JsonViewer"
        description="JSON 데이터를 구조화된 형태로 표시하는 컴포넌트입니다."
        stability="stable"
      />

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>기본 JsonViewer</h3>
        <LivePreview>
          <div style={{ maxHeight: '300px', overflow: 'auto', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '1rem' }}>
            <JsonViewer data={sampleData} initialExpanded={true} />
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>접을 수 있는 JsonViewer</h3>
        <LivePreview>
          <div style={{ maxHeight: '300px', overflow: 'auto', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '1rem' }}>
            <JsonViewer data={complexData} initialExpanded={false} maxDepth={3} />
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`import { JsonViewer } from '@/shared/ui/recipes'

const data = {
  name: 'Product',
  price: 25000,
  stock: 150,
  tags: ['beauty', 'skincare'],
}

<JsonViewer data={data} initialExpanded={true} />`}
        </pre>
      </section>
    </div>
  )
}
