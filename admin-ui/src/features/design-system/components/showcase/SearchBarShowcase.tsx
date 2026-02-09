/**
 * SearchBar Showcase - SearchBar 컴포넌트 전시
 */
import { useState } from 'react'
import { SearchBar } from '@/shared/ui/recipes'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function SearchBarShowcase() {
  const [searchResult, setSearchResult] = useState<string>('')

  const handleSearch = (tenant: string, entityId: string, version?: number | 'latest') => {
    setSearchResult(`Searched: tenant=${tenant}, entityId=${entityId}, version=${version}`)
  }

  const handleAutocomplete = async (_query: string, _tenant: string) => {
    // Mock autocomplete suggestions
    return [
      { type: 'entity' as const, value: 'SKU-12345', label: 'SKU-12345', description: 'Product entity' },
      { type: 'entity' as const, value: 'SKU-67890', label: 'SKU-67890', description: 'Product entity' },
    ]
  }

  return (
    <div className="showcase">
      <PageHeader
        title="SearchBar"
        description="고급 검색 기능이 있는 검색 바 컴포넌트입니다."
        stability="stable"
      />

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>기본 SearchBar</h3>
        <LivePreview>
          <div style={{ width: '100%', maxWidth: '600px' }}>
            <SearchBar
              onSearch={handleSearch}
              onAutocomplete={handleAutocomplete}
              defaultTenant="oliveyoung"
              placeholder="tenant:oliveyoung entity:SKU-12345 v:latest"
            />
            {searchResult && (
              <div style={{ marginTop: '1rem', padding: '0.75rem', background: 'var(--bg-secondary)', borderRadius: '8px', fontSize: '0.875rem' }}>
                {searchResult}
              </div>
            )}
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>검색 히스토리</h3>
        <LivePreview>
          <div style={{ width: '100%', maxWidth: '600px' }}>
            <SearchBar
              onSearch={handleSearch}
              defaultTenant="oliveyoung"
              historyKey="search_history_demo"
            />
            <p style={{ marginTop: '0.5rem', fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
              검색 히스토리는 localStorage에 저장되며, 포커스 시 최근 검색어가 표시됩니다.
            </p>
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`import { SearchBar } from '@/shared/ui/recipes'

const handleSearch = (tenant: string, entityId: string, version?: number | 'latest') => {
  console.log('Search:', { tenant, entityId, version })
}

const handleAutocomplete = async (query: string, tenant: string) => {
  // API 호출하여 자동완성 제안 반환
  return [
    { type: 'entity', value: 'SKU-12345', label: 'SKU-12345', description: 'Product entity' },
  ]
}

<SearchBar
  onSearch={handleSearch}
  onAutocomplete={handleAutocomplete}
  defaultTenant="oliveyoung"
  placeholder="tenant:oliveyoung entity:SKU-12345 v:latest"
/>`}
        </pre>
      </section>
    </div>
  )
}
