/**
 * TableHeader Showcase - TableHeader 컴포넌트 전시
 */
import { TableHeader, Button } from '@/shared/ui'
import { Database, Plus } from 'lucide-react'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function TableHeaderShowcase() {
  return (
    <div className="showcase">
      <PageHeader
        title="TableHeader"
        description="테이블 헤더 컴포넌트입니다."
        stability="stable"
      />

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>기본 TableHeader</h3>
        <LivePreview>
          <TableHeader
            icon={<Database size={18} />}
            title="RawData"
            count={42}
          />
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>액션 버튼과 함께</h3>
        <LivePreview>
          <TableHeader
            icon={<Database size={18} />}
            title="RawData"
            count={42}
            actions={
              <Button variant="primary" size="sm" icon={<Plus size={16} />}>
                추가
              </Button>
            }
          />
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>카운트 없이</h3>
        <LivePreview>
          <TableHeader
            icon={<Database size={18} />}
            title="RawData"
            actions={
              <Button variant="ghost" size="sm">
                새로고침
              </Button>
            }
          />
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`import { TableHeader, Button } from '@/shared/ui'
import { Database, Plus } from 'lucide-react'

<TableHeader
  icon={<Database size={18} />}
  title="RawData"
  count={42}
  actions={
    <Button variant="primary" size="sm" icon={<Plus />}>
      추가
    </Button>
  }
/>`}
        </pre>
      </section>
    </div>
  )
}
