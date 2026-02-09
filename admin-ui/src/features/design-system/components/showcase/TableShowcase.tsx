/**
 * Table Showcase Component
 *
 * Phase 3-F: Table 컴포넌트 전시
 * Live Preview, Props Playground, 코드 스니펫 등을 제공
 */

import { useState } from 'react'
import { Edit, Trash2 } from 'lucide-react'
import { IconButton, StatusBadge, Switch, Table, type TableColumn } from '@/shared/ui'
import { PageHeader } from '../layout'
import './TableShowcase.css'

// ============================================================================
// Types
// ============================================================================

interface TablePlaygroundState {
  loading: boolean
  striped: boolean
  hoverable: boolean
  compact: boolean
  showActions: boolean
}

interface SampleUser {
  id: string
  name: string
  email: string
  role: string
  status: 'active' | 'inactive' | 'pending'
}

// ============================================================================
// Mock Data
// ============================================================================

const SAMPLE_DATA: SampleUser[] = [
  { id: '1', name: '김철수', email: 'kim@example.com', role: 'Admin', status: 'active' },
  { id: '2', name: '이영희', email: 'lee@example.com', role: 'Editor', status: 'active' },
  { id: '3', name: '박민수', email: 'park@example.com', role: 'Viewer', status: 'pending' },
  { id: '4', name: '정수진', email: 'jung@example.com', role: 'Editor', status: 'inactive' },
  { id: '5', name: '최동욱', email: 'choi@example.com', role: 'Viewer', status: 'active' },
]

const tableMockData = {
  description: '데이터 목록을 테이블 형태로 표시하는 Table 컴포넌트',
  antiPatterns: [
    {
      title: 'keyExtractor 미제공',
      reason: 'React의 key prop을 위해 필수, 성능과 렌더링 정확성에 영향',
      badCode: `<Table data={users} columns={columns} />`,
      goodCode: `<Table\n  data={users}\n  columns={columns}\n  keyExtractor={(user) => user.id}\n/>`,
    },
    {
      title: '대량 데이터에 페이지네이션 없음',
      reason: '수천 개 행은 성능 저하, 가상화 또는 페이지네이션 필요',
      badCode: `<Table data={allUsers} /> // 10,000 rows`,
      goodCode: `<Table data={paginatedUsers} />\n<Pagination\n  currentPage={page}\n  totalPages={totalPages}\n  onPageChange={setPage}\n/>`,
    },
    {
      title: '액션 버튼에 aria-label 누락',
      reason: '접근성 문제 - 스크린 리더 사용자에게 버튼 목적 전달 안됨',
      badCode: `rowActions={(item) => (\n  <IconButton icon={<Trash2 />} />\n)}`,
      goodCode: `rowActions={(item) => (\n  <IconButton\n    icon={<Trash2 />}\n    aria-label={\`\${item.name} 삭제\`}\n  />\n)}`,
    },
  ],
  a11yScore: {
    overall: 88,
    categories: {
      colorContrast: 95,
      keyboardNavigation: 85,
      ariaLabels: 82,
      focusManagement: 90,
    },
    issues: [
      {
        id: 'sortable-announce',
        message: '정렬 가능 열에 aria-sort 속성 필요',
        severity: 'info',
        suggestion: '정렬 기능 사용 시 aria-sort="ascending|descending|none" 추가',
      },
    ],
  },
}

// ============================================================================
// Sample Columns
// ============================================================================

const sampleColumns: TableColumn<SampleUser>[] = [
  { key: 'id', header: 'ID', width: '60px' },
  { key: 'name', header: '이름', width: '120px' },
  { key: 'email', header: '이메일' },
  { key: 'role', header: '역할', width: '100px' },
  {
    key: 'status',
    header: '상태',
    width: '100px',
    render: (user: SampleUser) => (
      <StatusBadge
        status={user.status === 'active' ? 'healthy' : user.status === 'pending' ? 'pending' : 'failed'}
      />
    ),
  },
]

// ============================================================================
// Live Preview Section
// ============================================================================

function LivePreview({ state }: { state: TablePlaygroundState }) {
  return (
    <div className="table-showcase-preview">
      <Table
        data={SAMPLE_DATA}
        columns={sampleColumns}
        keyExtractor={(user) => user.id}
        loading={state.loading}
        striped={state.striped}
        hoverable={state.hoverable}
        compact={state.compact}
        rowActions={
          state.showActions
            ? (user) => (
                <div style={{ display: 'flex', gap: '0.25rem' }}>
                  <IconButton
                    icon={Edit}
                    iconSize={14}
                    size="sm"
                    variant="ghost"
                    aria-label={`${user.name} 편집`}
                  />
                  <IconButton
                    icon={Trash2}
                    iconSize={14}
                    size="sm"
                    variant="ghost"
                    aria-label={`${user.name} 삭제`}
                  />
                </div>
              )
            : undefined
        }
      />
    </div>
  )
}

// ============================================================================
// Controls Panel
// ============================================================================

function ControlsPanel({
  state,
  onChange,
}: {
  state: TablePlaygroundState
  onChange: (key: keyof TablePlaygroundState, value: unknown) => void
}) {
  return (
    <div className="table-showcase-controls">
      <h3 className="table-showcase-controls-title">Props Playground</h3>

      <div className="table-showcase-control-group">
        <div className="table-showcase-control-row">
          <span className="table-showcase-control-label">Loading</span>
          <Switch
            checked={state.loading}
            onChange={(checked) => onChange('loading', checked)}
            size="sm"
          />
        </div>
      </div>

      <div className="table-showcase-control-group">
        <div className="table-showcase-control-row">
          <span className="table-showcase-control-label">Striped Rows</span>
          <Switch
            checked={state.striped}
            onChange={(checked) => onChange('striped', checked)}
            size="sm"
          />
        </div>
      </div>

      <div className="table-showcase-control-group">
        <div className="table-showcase-control-row">
          <span className="table-showcase-control-label">Hoverable</span>
          <Switch
            checked={state.hoverable}
            onChange={(checked) => onChange('hoverable', checked)}
            size="sm"
          />
        </div>
      </div>

      <div className="table-showcase-control-group">
        <div className="table-showcase-control-row">
          <span className="table-showcase-control-label">Compact</span>
          <Switch
            checked={state.compact}
            onChange={(checked) => onChange('compact', checked)}
            size="sm"
          />
        </div>
      </div>

      <div className="table-showcase-control-group">
        <div className="table-showcase-control-row">
          <span className="table-showcase-control-label">Row Actions</span>
          <Switch
            checked={state.showActions}
            onChange={(checked) => onChange('showActions', checked)}
            size="sm"
          />
        </div>
      </div>
    </div>
  )
}

// ============================================================================
// Code Preview
// ============================================================================

function CodePreview({ state }: { state: TablePlaygroundState }) {
  const generateCode = () => {
    const props: string[] = [
      'data={users}',
      'columns={columns}',
      'keyExtractor={(user) => user.id}',
    ]

    if (state.loading) props.push('loading')
    if (state.striped) props.push('striped')
    if (!state.hoverable) props.push('hoverable={false}')
    if (state.compact) props.push('compact')
    if (state.showActions) props.push('rowActions={(user) => <Actions user={user} />}')

    return `<Table\n  ${props.join('\n  ')}\n/>`
  }

  return (
    <div className="table-showcase-code">
      <h3 className="table-showcase-code-title">Code</h3>
      <pre className="table-showcase-code-block">
        <code>{generateCode()}</code>
      </pre>
    </div>
  )
}

// ============================================================================
// Examples Section
// ============================================================================

function ExamplesSection() {
  const simpleColumns: TableColumn<{ id: string; name: string; value: number }>[] = [
    { key: 'id', header: 'ID', width: '80px' },
    { key: 'name', header: '항목' },
    { key: 'value', header: '값', align: 'right' },
  ]

  const simpleData = [
    { id: '1', name: '항목 A', value: 100 },
    { id: '2', name: '항목 B', value: 250 },
    { id: '3', name: '항목 C', value: 75 },
  ]

  return (
    <div className="table-showcase-examples">
      <h3 className="table-showcase-section-title">Examples</h3>

      <div className="table-showcase-example-grid">
        {/* Basic Table */}
        <div className="table-showcase-example-card">
          <h4>Basic Table</h4>
          <p>기본 테이블</p>
          <div className="table-showcase-example-preview">
            <Table
              data={simpleData}
              columns={simpleColumns}
              keyExtractor={(item) => item.id}
            />
          </div>
          <pre className="table-showcase-example-code">
            <code>{`<Table\n  data={data}\n  columns={columns}\n  keyExtractor={(item) => item.id}\n/>`}</code>
          </pre>
        </div>

        {/* Striped Table */}
        <div className="table-showcase-example-card">
          <h4>Striped Rows</h4>
          <p>줄무늬 행</p>
          <div className="table-showcase-example-preview">
            <Table
              data={simpleData}
              columns={simpleColumns}
              keyExtractor={(item) => item.id}
              striped
            />
          </div>
          <pre className="table-showcase-example-code">
            <code>{`<Table striped ... />`}</code>
          </pre>
        </div>

        {/* Compact Table */}
        <div className="table-showcase-example-card">
          <h4>Compact Table</h4>
          <p>작은 패딩</p>
          <div className="table-showcase-example-preview">
            <Table
              data={simpleData}
              columns={simpleColumns}
              keyExtractor={(item) => item.id}
              compact
            />
          </div>
          <pre className="table-showcase-example-code">
            <code>{`<Table compact ... />`}</code>
          </pre>
        </div>

        {/* Empty State */}
        <div className="table-showcase-example-card">
          <h4>Empty State</h4>
          <p>빈 데이터</p>
          <div className="table-showcase-example-preview">
            <Table
              data={[] as typeof simpleData}
              columns={simpleColumns}
              keyExtractor={(item) => item.id}
              emptyMessage="표시할 데이터가 없습니다"
            />
          </div>
          <pre className="table-showcase-example-code">
            <code>{`<Table\n  data={[]}\n  emptyMessage="표시할 데이터가 없습니다"\n/>`}</code>
          </pre>
        </div>

        {/* Loading State */}
        <div className="table-showcase-example-card">
          <h4>Loading State</h4>
          <p>로딩 중</p>
          <div className="table-showcase-example-preview" style={{ minHeight: '100px' }}>
            <Table
              data={[] as typeof simpleData}
              columns={simpleColumns}
              keyExtractor={(item) => item.id}
              loading
            />
          </div>
          <pre className="table-showcase-example-code">
            <code>{`<Table loading ... />`}</code>
          </pre>
        </div>

        {/* Custom Render */}
        <div className="table-showcase-example-card">
          <h4>Custom Cell Render</h4>
          <p>커스텀 셀 렌더링</p>
          <div className="table-showcase-example-preview">
            <Table
              data={SAMPLE_DATA.slice(0, 2)}
              columns={[
                { key: 'name', header: '이름' },
                {
                  key: 'status',
                  header: '상태',
                  render: (user) => (
                    <StatusBadge status={user.status === 'active' ? 'healthy' : 'pending'} />
                  ),
                },
              ]}
              keyExtractor={(user) => user.id}
            />
          </div>
          <pre className="table-showcase-example-code">
            <code>{`columns={[\n  {\n    key: 'status',\n    render: (item) => <Badge />\n  }\n]}`}</code>
          </pre>
        </div>
      </div>
    </div>
  )
}

// ============================================================================
// Anti-Patterns Section
// ============================================================================

function AntiPatternsSection() {
  return (
    <div className="table-showcase-antipatterns">
      <h3 className="table-showcase-section-title">Anti-Patterns</h3>

      <div className="table-showcase-antipattern-list">
        {tableMockData.antiPatterns.map((pattern, index) => (
          <div key={index} className="table-showcase-antipattern-card">
            <h4>{pattern.title}</h4>
            <p className="table-showcase-antipattern-reason">{pattern.reason}</p>

            <div className="table-showcase-antipattern-comparison">
              <div className="table-showcase-antipattern-bad">
                <span className="table-showcase-antipattern-label bad">Bad</span>
                <pre><code>{pattern.badCode}</code></pre>
              </div>
              <div className="table-showcase-antipattern-good">
                <span className="table-showcase-antipattern-label good">Good</span>
                <pre><code>{pattern.goodCode}</code></pre>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

// ============================================================================
// A11y Section
// ============================================================================

function A11ySection() {
  const { a11yScore } = tableMockData

  return (
    <div className="table-showcase-a11y">
      <h3 className="table-showcase-section-title">Accessibility</h3>

      <div className="table-showcase-a11y-score">
        <div className="table-showcase-a11y-overall">
          <span className="table-showcase-a11y-score-value">{a11yScore.overall}</span>
          <span className="table-showcase-a11y-score-label">Overall Score</span>
        </div>

        <div className="table-showcase-a11y-categories">
          {Object.entries(a11yScore.categories).map(([key, value]) => (
            <div key={key} className="table-showcase-a11y-category">
              <span className="table-showcase-a11y-category-name">
                {key.replace(/([A-Z])/g, ' $1').trim()}
              </span>
              <div className="table-showcase-a11y-bar">
                <div
                  className="table-showcase-a11y-bar-fill"
                  style={{ width: `${value}%` }}
                />
              </div>
              <span className="table-showcase-a11y-category-value">{value}</span>
            </div>
          ))}
        </div>
      </div>

      {a11yScore.issues.length > 0 && (
        <div className="table-showcase-a11y-issues">
          <h4>Known Issues</h4>
          {a11yScore.issues.map((issue) => (
            <div key={issue.id} className={`table-showcase-a11y-issue ${issue.severity}`}>
              <span className="table-showcase-a11y-issue-badge">{issue.severity}</span>
              <span className="table-showcase-a11y-issue-message">{issue.message}</span>
              {issue.suggestion && (
                <p className="table-showcase-a11y-issue-suggestion">{issue.suggestion}</p>
              )}
            </div>
          ))}
        </div>
      )}

      <div className="table-showcase-a11y-best-practices">
        <h4>Best Practices</h4>
        <ul>
          <li>테이블에 <code>aria-label</code> 또는 <code>caption</code>으로 목적 설명</li>
          <li>정렬 가능 열에 <code>aria-sort</code> 속성 사용</li>
          <li>행 액션 버튼에 명확한 <code>aria-label</code> 제공</li>
          <li>복잡한 테이블은 <code>scope</code> 속성으로 헤더 관계 명시</li>
        </ul>
      </div>
    </div>
  )
}

// ============================================================================
// Main Component
// ============================================================================

export function TableShowcase() {
  const [playgroundState, setPlaygroundState] = useState<TablePlaygroundState>({
    loading: false,
    striped: false,
    hoverable: true,
    compact: false,
    showActions: true,
  })

  const handleChange = (key: keyof TablePlaygroundState, value: unknown) => {
    setPlaygroundState((prev) => ({ ...prev, [key]: value }))
  }

  return (
    <div className="table-showcase">
      <PageHeader
        title="Table"
        description={tableMockData.description}
        stability="stable"
      />

      {/* Playground Section */}
      <section className="table-showcase-section">
        <div className="table-showcase-playground">
          <LivePreview state={playgroundState} />
          <ControlsPanel state={playgroundState} onChange={handleChange} />
        </div>
        <CodePreview state={playgroundState} />
      </section>

      {/* Examples Section */}
      <section className="table-showcase-section">
        <ExamplesSection />
      </section>

      {/* Anti-Patterns Section */}
      <section className="table-showcase-section">
        <AntiPatternsSection />
      </section>

      {/* A11y Section */}
      <section className="table-showcase-section">
        <A11ySection />
      </section>
    </div>
  )
}
