/**
 * PageHeader Showcase - PageHeader 컴포넌트 전시
 */
import { PageHeader, Button } from '@/shared/ui'
import { Plus } from 'lucide-react'
import { PageHeader as DSPageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function PageHeaderShowcase() {
  return (
    <div className="showcase">
      <DSPageHeader
        title="PageHeader"
        description="페이지 제목과 설명을 표시하는 헤더 컴포넌트입니다."
        stability="stable"
      />

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>기본 PageHeader</h3>
        <LivePreview>
          <PageHeader
            title="Contracts"
            subtitle="스키마, 룰셋, 뷰 정의 등 시스템 계약을 관리합니다"
          />
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>액션 버튼과 함께</h3>
        <LivePreview>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '1rem' }}>
            <PageHeader
              title="RawData"
              subtitle="원본 데이터를 탐색하고 관리합니다"
            />
            <Button variant="primary" size="sm" icon={<Plus size={16} />}>
              새로 만들기
            </Button>
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`import { PageHeader, Button } from '@/shared/ui'
import { Plus } from 'lucide-react'

<PageHeader
  title="Contracts"
  subtitle="스키마, 룰셋, 뷰 정의 등 시스템 계약을 관리합니다"
/>

<div style={{ display: 'flex', justifyContent: 'space-between' }}>
  <PageHeader title="RawData" subtitle="원본 데이터를 탐색하고 관리합니다" />
  <Button variant="primary" icon={<Plus />}>새로 만들기</Button>
</div>`}
        </pre>
      </section>
    </div>
  )
}
