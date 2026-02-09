/**
 * EmptyState Showcase - EmptyState 컴포넌트 전시
 */
import { Database, AlertCircle, Inbox } from 'lucide-react'
import { EmptyState, Button } from '@/shared/ui'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function EmptyStateShowcase() {
  return (
    <div className="showcase">
      <PageHeader
        title="EmptyState"
        description="데이터가 없을 때, 에러 상태, 초기 상태 등을 표시하는 컴포넌트입니다."
        stability="stable"
      />

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>기본 EmptyState</h3>
        <LivePreview>
          <EmptyState
            icon={<Database size={48} />}
            title="데이터가 없습니다"
            description="검색 조건을 변경하거나 새 데이터를 등록해보세요"
          />
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>액션이 있는 EmptyState</h3>
        <LivePreview>
          <EmptyState
            icon={<Database size={48} />}
            title="데이터가 없습니다"
            description="새 데이터를 등록해보세요"
            action={<Button variant="primary">새로 만들기</Button>}
          />
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>에러 EmptyState</h3>
        <LivePreview>
          <EmptyState
            icon={<AlertCircle size={48} />}
            title="데이터를 불러올 수 없습니다"
            description="네트워크 연결을 확인하고 다시 시도해주세요"
            action={<Button variant="primary">다시 시도</Button>}
          />
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Compact Variant</h3>
        <LivePreview>
          <EmptyState
            icon={<Inbox size={32} />}
            title="No data"
            description="Empty state message"
            variant="compact"
            size="sm"
            animate={false}
          />
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Card Variant</h3>
        <LivePreview>
          <EmptyState
            icon={<Database size={48} />}
            title="No data"
            description="Empty state message"
            variant="card"
          />
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`import { EmptyState, Button } from '@/shared/ui'
import { Database } from 'lucide-react'

<EmptyState
  icon={<Database size={48} />}
  title="데이터가 없습니다"
  description="검색 조건을 변경하거나 새 데이터를 등록해보세요"
  action={<Button variant="primary">새로 만들기</Button>}
/>`}
        </pre>
      </section>
    </div>
  )
}
