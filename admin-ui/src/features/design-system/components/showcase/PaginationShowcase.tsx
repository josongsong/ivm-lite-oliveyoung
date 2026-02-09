/**
 * Pagination Showcase
 */
import { useState } from 'react'
import { Pagination } from '@/shared/ui'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function PaginationShowcase() {
  const [page, setPage] = useState(1)
  const totalPages = 10
  const totalItems = 100
  const pageSize = 10

  return (
    <div className="showcase">
      <PageHeader
        title="Pagination"
        description="페이지네이션 컴포넌트입니다. 대량의 데이터를 페이지 단위로 탐색합니다."
        stability="stable"
      />

      <LivePreview>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', alignItems: 'center' }}>
          <Pagination
            page={page}
            totalPages={totalPages}
            totalItems={totalItems}
            pageSize={pageSize}
            onPageChange={setPage}
          />
          <div style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
            Current page: {page}
          </div>
        </div>
      </LivePreview>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Different Page Counts</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
          <div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginBottom: '0.5rem' }}>5 pages</div>
            <Pagination page={2} totalPages={5} totalItems={50} pageSize={10} onPageChange={() => {}} />
          </div>
          <div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginBottom: '0.5rem' }}>20 pages</div>
            <Pagination page={10} totalPages={20} totalItems={200} pageSize={10} onPageChange={() => {}} />
          </div>
        </div>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Responsive Behavior</h3>
        <div style={{ 
          background: 'var(--bg-tertiary)', 
          padding: '1rem', 
          borderRadius: '8px',
          fontSize: '0.875rem',
          color: 'var(--text-secondary)'
        }}>
          <p style={{ margin: '0 0 0.5rem 0' }}>
            <strong>태블릿 (768px 이하):</strong>
          </p>
          <ul style={{ margin: '0 0 1rem 0', paddingLeft: '1.5rem' }}>
            <li>정보 텍스트와 컨트롤이 세로로 배치됩니다</li>
            <li>페이지 번호 크기가 작아집니다</li>
            <li>첫/마지막 페이지 버튼이 숨겨집니다</li>
          </ul>
          <p style={{ margin: '0 0 0.5rem 0' }}>
            <strong>모바일 (480px 이하):</strong>
          </p>
          <ul style={{ margin: '0 0 1rem 0', paddingLeft: '1.5rem' }}>
            <li>정보 텍스트가 간결해집니다 ("Page 2 / 10")</li>
            <li>기본적으로 페이지 번호가 숨겨집니다</li>
            <li>이전/다음 버튼만 표시됩니다</li>
            <li>compactMobile prop으로 페이지 번호 표시 가능</li>
          </ul>
          <p style={{ margin: 0 }}>
            브라우저 창 크기를 조절하여 반응형 동작을 확인하세요.
          </p>
        </div>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Mobile Compact Mode</h3>
        <LivePreview>
          <Pagination
            page={page}
            totalPages={totalPages}
            totalItems={totalItems}
            pageSize={pageSize}
            onPageChange={setPage}
            compactMobile
          />
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`const [page, setPage] = useState(1)

<Pagination
  page={page}
  totalPages={10}
  totalItems={100}
  pageSize={10}
  onPageChange={setPage}
  compactMobile  // 모바일에서도 페이지 번호 표시
/>`}
        </pre>
      </section>
    </div>
  )
}
