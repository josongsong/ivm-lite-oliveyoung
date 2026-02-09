/**
 * ActionCard Showcase - ActionCard 컴포넌트 전시
 */
import { ActionCard } from '@/shared/ui'
import { Database, Layers, Eye, Rocket } from 'lucide-react'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function ActionCardShowcase() {
  return (
    <div className="showcase">
      <PageHeader
        title="ActionCard"
        description="빠른 액션을 위한 클릭 가능한 카드 컴포넌트입니다."
        stability="stable"
      />

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>기본 ActionCard</h3>
        <LivePreview>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem', width: '100%' }}>
            <ActionCard
              icon={<Database size={24} />}
              title="RawData"
              description="원본 데이터를 탐색합니다"
              onClick={() => alert('RawData 클릭')}
            />
            <ActionCard
              icon={<Layers size={24} />}
              title="Slices"
              description="슬라이스 데이터를 확인합니다"
              onClick={() => alert('Slices 클릭')}
            />
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>링크로 사용</h3>
        <LivePreview>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem', width: '100%' }}>
            <ActionCard
              icon={<Eye size={24} />}
              title="Views"
              description="뷰 데이터를 확인합니다"
              to="/views"
            />
            <ActionCard
              icon={<Rocket size={24} />}
              title="Sinks"
              description="싱크 설정을 관리합니다"
              to="/sinks"
            />
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`import { ActionCard } from '@/shared/ui'
import { Database } from 'lucide-react'

<ActionCard
  icon={<Database size={24} />}
  title="RawData"
  description="원본 데이터를 탐색합니다"
  onClick={() => handleClick()}
/>

<ActionCard
  icon={<Database size={24} />}
  title="RawData"
  description="원본 데이터를 탐색합니다"
  to="/rawdata"
/>`}
        </pre>
      </section>
    </div>
  )
}
