/**
 * SearchFilter Showcase - SearchFilter 컴포넌트 전시
 */
import { Search } from 'lucide-react'
import { SearchFilter } from '@/shared/ui'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function SearchFilterShowcase() {
  return (
    <div className="showcase">
      <PageHeader
        title="SearchFilter"
        description="검색 아이콘이 있는 필터 입력 컴포넌트입니다."
        stability="stable"
      />

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>기본 SearchFilter</h3>
        <LivePreview>
          <div style={{ width: '100%', maxWidth: '300px' }}>
            <SearchFilter placeholder="검색 필터..." />
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>커스텀 아이콘</h3>
        <LivePreview>
          <div style={{ width: '100%', maxWidth: '300px' }}>
            <SearchFilter icon={<Search size={16} />} placeholder="Entity ID로 필터..." />
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`import { SearchFilter } from '@/shared/ui'
import { Search } from 'lucide-react'

<SearchFilter placeholder="검색 필터..." />
<SearchFilter icon={<Search size={16} />} placeholder="Entity ID로 필터..." />`}
        </pre>
      </section>
    </div>
  )
}
