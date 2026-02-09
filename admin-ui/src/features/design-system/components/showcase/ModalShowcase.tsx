/**
 * Modal Showcase Component
 *
 * Phase 3-E: Modal 컴포넌트 전시
 * Live Preview, Props Playground, 코드 스니펫 등을 제공
 */

import { useState } from 'react'
import { AlertTriangle } from 'lucide-react'
import { Button, Input, Modal, Switch } from '@/shared/ui'
import { PageHeader } from '../layout'
import './ModalShowcase.css'

// ============================================================================
// Types
// ============================================================================

interface ModalPlaygroundState {
  size: 'sm' | 'md' | 'lg' | 'xl' | 'full'
  title: string
  showFooter: boolean
}

// ============================================================================
// Mock Data
// ============================================================================

const modalMockData = {
  description: '오버레이 다이얼로그를 표시하는 Modal 컴포넌트',
  antiPatterns: [
    {
      title: 'Modal 중첩 사용',
      reason: 'UX 혼란, 포커스 관리 복잡, 모바일에서 사용성 저하',
      badCode: `<Modal isOpen={isOpen1}>\n  <Modal isOpen={isOpen2}>\n    Nested content\n  </Modal>\n</Modal>`,
      goodCode: `// 단계별 플로우는 Stepper 또는\n// 새 페이지로 이동\n<Modal isOpen={isOpen}>\n  <Stepper steps={steps} />\n</Modal>`,
    },
    {
      title: 'onClose 핸들러 누락',
      reason: '사용자가 모달을 닫을 수 없음',
      badCode: `<Modal isOpen={isOpen} title="제목">\n  Content\n</Modal>`,
      goodCode: `<Modal\n  isOpen={isOpen}\n  onClose={() => setIsOpen(false)}\n  title="제목"\n>\n  Content\n</Modal>`,
    },
    {
      title: 'ESC 키로 닫기 불가',
      reason: '접근성 문제 - 키보드 사용자를 위해 ESC로 닫기 필수',
      badCode: `// ESC 핸들링 없음`,
      goodCode: `// Modal 컴포넌트 내부에서\n// 자동으로 ESC 처리됨`,
    },
  ],
  a11yScore: {
    overall: 92,
    categories: {
      colorContrast: 95,
      keyboardNavigation: 90,
      ariaLabels: 88,
      focusManagement: 95,
    },
    issues: [
      {
        id: 'focus-trap',
        message: '모달 열릴 때 포커스 트랩 필요',
        severity: 'info',
        suggestion: 'react-focus-lock 또는 focus-trap-react 사용 권장',
      },
    ],
  },
}

// ============================================================================
// Live Preview Section
// ============================================================================

function LivePreview({ state }: { state: ModalPlaygroundState }) {
  const [isOpen, setIsOpen] = useState(false)

  return (
    <div className="modal-showcase-preview">
      <Button variant="primary" onClick={() => setIsOpen(true)}>
        Open Modal
      </Button>

      <Modal
        isOpen={isOpen}
        onClose={() => setIsOpen(false)}
        title={state.title}
        size={state.size}
        footer={
          state.showFooter ? (
            <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
              <Button variant="ghost" onClick={() => setIsOpen(false)}>
                취소
              </Button>
              <Button variant="primary" onClick={() => setIsOpen(false)}>
                확인
              </Button>
            </div>
          ) : undefined
        }
      >
        <p style={{ color: 'var(--text-secondary)', margin: 0 }}>
          이것은 Modal 내용입니다. 다양한 컨텐츠를 포함할 수 있습니다.
          폼, 정보, 확인 대화상자 등에 활용됩니다.
        </p>
      </Modal>
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
  state: ModalPlaygroundState
  onChange: (key: keyof ModalPlaygroundState, value: unknown) => void
}) {
  return (
    <div className="modal-showcase-controls">
      <h3 className="modal-showcase-controls-title">Props Playground</h3>

      <div className="modal-showcase-control-group">
        <label className="modal-showcase-control-label">Size</label>
        <div className="modal-showcase-control-options">
          {(['sm', 'md', 'lg', 'xl'] as const).map((size) => (
            <Button
              key={size}
              size="sm"
              variant={state.size === size ? 'primary' : 'ghost'}
              onClick={() => onChange('size', size)}
            >
              {size.toUpperCase()}
            </Button>
          ))}
        </div>
      </div>

      <div className="modal-showcase-control-group">
        <label className="modal-showcase-control-label">Title</label>
        <Input
          size="sm"
          value={state.title}
          onChange={(e) => onChange('title', e.target.value)}
        />
      </div>

      <div className="modal-showcase-control-group">
        <div className="modal-showcase-control-row">
          <span className="modal-showcase-control-label">Show Footer</span>
          <Switch
            checked={state.showFooter}
            onChange={(checked) => onChange('showFooter', checked)}
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

function CodePreview({ state }: { state: ModalPlaygroundState }) {
  const generateCode = () => {
    const props: string[] = [
      'isOpen={isOpen}',
      'onClose={() => setIsOpen(false)}',
      `title="${state.title}"`,
    ]

    if (state.size !== 'md') props.push(`size="${state.size}"`)
    if (state.showFooter) props.push('footer={<Footer />}')

    return `<Modal\n  ${props.join('\n  ')}\n>\n  {children}\n</Modal>`
  }

  return (
    <div className="modal-showcase-code">
      <h3 className="modal-showcase-code-title">Code</h3>
      <pre className="modal-showcase-code-block">
        <code>{generateCode()}</code>
      </pre>
    </div>
  )
}

// ============================================================================
// Examples Section
// ============================================================================

function ExamplesSection() {
  const [basicOpen, setBasicOpen] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [formOpen, setFormOpen] = useState(false)
  const [fullOpen, setFullOpen] = useState(false)

  return (
    <div className="modal-showcase-examples">
      <h3 className="modal-showcase-section-title">Examples</h3>

      <div className="modal-showcase-example-grid">
        {/* Basic Modal */}
        <div className="modal-showcase-example-card">
          <h4>Basic Modal</h4>
          <p>기본 정보 모달</p>
          <div className="modal-showcase-example-preview">
            <Button variant="secondary" onClick={() => setBasicOpen(true)}>
              Open Basic
            </Button>
          </div>
          <pre className="modal-showcase-example-code">
            <code>{`<Modal title="알림" isOpen={isOpen} onClose={onClose}>\n  Content here\n</Modal>`}</code>
          </pre>
          <Modal
            isOpen={basicOpen}
            onClose={() => setBasicOpen(false)}
            title="알림"
          >
            <p style={{ margin: 0, color: 'var(--text-secondary)' }}>
              기본 모달입니다. 간단한 정보를 표시할 때 사용합니다.
            </p>
          </Modal>
        </div>

        {/* Confirm Modal */}
        <div className="modal-showcase-example-card">
          <h4>Confirm Dialog</h4>
          <p>확인/취소 다이얼로그</p>
          <div className="modal-showcase-example-preview">
            <Button variant="danger" onClick={() => setConfirmOpen(true)}>
              Delete Item
            </Button>
          </div>
          <pre className="modal-showcase-example-code">
            <code>{`<Modal\n  title="삭제 확인"\n  footer={<Actions />}\n>\n  정말 삭제하시겠습니까?\n</Modal>`}</code>
          </pre>
          <Modal
            isOpen={confirmOpen}
            onClose={() => setConfirmOpen(false)}
            title="삭제 확인"
            size="sm"
            footer={
              <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
                <Button variant="ghost" onClick={() => setConfirmOpen(false)}>
                  취소
                </Button>
                <Button variant="danger" onClick={() => setConfirmOpen(false)}>
                  삭제
                </Button>
              </div>
            }
          >
            <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'flex-start' }}>
              <AlertTriangle size={20} style={{ color: 'var(--status-warning)', flexShrink: 0 }} />
              <p style={{ margin: 0, color: 'var(--text-secondary)' }}>
                이 항목을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.
              </p>
            </div>
          </Modal>
        </div>

        {/* Form Modal */}
        <div className="modal-showcase-example-card">
          <h4>Form Modal</h4>
          <p>폼 입력 모달</p>
          <div className="modal-showcase-example-preview">
            <Button variant="primary" onClick={() => setFormOpen(true)}>
              Add New
            </Button>
          </div>
          <pre className="modal-showcase-example-code">
            <code>{`<Modal size="lg" title="새 항목 추가">\n  <form>...</form>\n</Modal>`}</code>
          </pre>
          <Modal
            isOpen={formOpen}
            onClose={() => setFormOpen(false)}
            title="새 항목 추가"
            size="lg"
            footer={
              <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
                <Button variant="ghost" onClick={() => setFormOpen(false)}>
                  취소
                </Button>
                <Button variant="primary" onClick={() => setFormOpen(false)}>
                  저장
                </Button>
              </div>
            }
          >
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <label style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', display: 'block', marginBottom: '0.5rem' }}>이름</label>
                <Input placeholder="항목 이름을 입력하세요" />
              </div>
              <div>
                <label style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', display: 'block', marginBottom: '0.5rem' }}>설명</label>
                <Input placeholder="설명을 입력하세요" />
              </div>
            </div>
          </Modal>
        </div>

        {/* Full Width Modal */}
        <div className="modal-showcase-example-card">
          <h4>Full Size Modal</h4>
          <p>전체 화면 모달</p>
          <div className="modal-showcase-example-preview">
            <Button variant="outline" onClick={() => setFullOpen(true)}>
              Open Full
            </Button>
          </div>
          <pre className="modal-showcase-example-code">
            <code>{`<Modal size="full" title="전체 화면">\n  Large content\n</Modal>`}</code>
          </pre>
          <Modal
            isOpen={fullOpen}
            onClose={() => setFullOpen(false)}
            title="전체 화면 모달"
            size="full"
          >
            <p style={{ margin: 0, color: 'var(--text-secondary)' }}>
              대량의 컨텐츠나 복잡한 작업 흐름에 적합합니다.
              상세 편집, 미리보기 등에 활용됩니다.
            </p>
          </Modal>
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
    <div className="modal-showcase-antipatterns">
      <h3 className="modal-showcase-section-title">Anti-Patterns</h3>

      <div className="modal-showcase-antipattern-list">
        {modalMockData.antiPatterns.map((pattern, index) => (
          <div key={index} className="modal-showcase-antipattern-card">
            <h4>{pattern.title}</h4>
            <p className="modal-showcase-antipattern-reason">{pattern.reason}</p>

            <div className="modal-showcase-antipattern-comparison">
              <div className="modal-showcase-antipattern-bad">
                <span className="modal-showcase-antipattern-label bad">Bad</span>
                <pre><code>{pattern.badCode}</code></pre>
              </div>
              <div className="modal-showcase-antipattern-good">
                <span className="modal-showcase-antipattern-label good">Good</span>
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
  const { a11yScore } = modalMockData

  return (
    <div className="modal-showcase-a11y">
      <h3 className="modal-showcase-section-title">Accessibility</h3>

      <div className="modal-showcase-a11y-score">
        <div className="modal-showcase-a11y-overall">
          <span className="modal-showcase-a11y-score-value">{a11yScore.overall}</span>
          <span className="modal-showcase-a11y-score-label">Overall Score</span>
        </div>

        <div className="modal-showcase-a11y-categories">
          {Object.entries(a11yScore.categories).map(([key, value]) => (
            <div key={key} className="modal-showcase-a11y-category">
              <span className="modal-showcase-a11y-category-name">
                {key.replace(/([A-Z])/g, ' $1').trim()}
              </span>
              <div className="modal-showcase-a11y-bar">
                <div
                  className="modal-showcase-a11y-bar-fill"
                  style={{ width: `${value}%` }}
                />
              </div>
              <span className="modal-showcase-a11y-category-value">{value}</span>
            </div>
          ))}
        </div>
      </div>

      {a11yScore.issues.length > 0 && (
        <div className="modal-showcase-a11y-issues">
          <h4>Known Issues</h4>
          {a11yScore.issues.map((issue) => (
            <div key={issue.id} className={`modal-showcase-a11y-issue ${issue.severity}`}>
              <span className="modal-showcase-a11y-issue-badge">{issue.severity}</span>
              <span className="modal-showcase-a11y-issue-message">{issue.message}</span>
              {issue.suggestion && (
                <p className="modal-showcase-a11y-issue-suggestion">{issue.suggestion}</p>
              )}
            </div>
          ))}
        </div>
      )}

      <div className="modal-showcase-a11y-best-practices">
        <h4>Best Practices</h4>
        <ul>
          <li>모달 열릴 때 첫 번째 포커스 가능 요소에 포커스</li>
          <li>ESC 키로 모달 닫기 지원</li>
          <li>오버레이 클릭으로 닫기 지원</li>
          <li><code>role="dialog"</code>와 <code>aria-modal="true"</code> 적용</li>
          <li>닫힐 때 트리거 요소로 포커스 복귀</li>
        </ul>
      </div>
    </div>
  )
}

// ============================================================================
// Main Component
// ============================================================================

export function ModalShowcase() {
  const [playgroundState, setPlaygroundState] = useState<ModalPlaygroundState>({
    size: 'md',
    title: '모달 제목',
    showFooter: true,
  })

  const handleChange = (key: keyof ModalPlaygroundState, value: unknown) => {
    setPlaygroundState((prev) => ({ ...prev, [key]: value }))
  }

  return (
    <div className="modal-showcase">
      <PageHeader
        title="Modal"
        description={modalMockData.description}
        stability="stable"
      />

      {/* Playground Section */}
      <section className="modal-showcase-section">
        <div className="modal-showcase-playground">
          <LivePreview state={playgroundState} />
          <ControlsPanel state={playgroundState} onChange={handleChange} />
        </div>
        <CodePreview state={playgroundState} />
      </section>

      {/* Examples Section */}
      <section className="modal-showcase-section">
        <ExamplesSection />
      </section>

      {/* Anti-Patterns Section */}
      <section className="modal-showcase-section">
        <AntiPatternsSection />
      </section>

      {/* A11y Section */}
      <section className="modal-showcase-section">
        <A11ySection />
      </section>
    </div>
  )
}
