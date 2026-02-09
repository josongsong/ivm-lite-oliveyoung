/**
 * Button Showcase - Button 컴포넌트 전시
 * Phase 3-A: Button 컴포넌트의 전체 전시 페이지
 */

import { useMemo, useState } from 'react'
import {
  AlertTriangle,
  Check,
  Lightbulb,
  Plus,
  X,
} from 'lucide-react'
import { Button } from '@/shared/ui'
import { buttonMockData } from '../../data'
import type { ControlDefinition } from '../../data/types'
import { generateCodeSnippet } from '../../utils'
import { LivePreview, StatePreview, VariantPreview } from './LivePreview'
import { ControlsPanel } from './ControlRenderer'
import './ButtonShowcase.css'

// ============================================================================
// Types
// ============================================================================

type TabType = 'playground' | 'examples' | 'a11y'

// ============================================================================
// Hooks
// ============================================================================

function usePlayground(controls: Record<string, ControlDefinition>) {
  const getDefaultValues = () => {
    const defaults: Record<string, unknown> = {}
    Object.entries(controls).forEach(([key, control]) => {
      defaults[key] = control.defaultValue
    })
    return defaults
  }

  const [values, setValues] = useState<Record<string, unknown>>(getDefaultValues)

  const handleChange = (name: string, value: unknown) => {
    setValues((prev) => ({ ...prev, [name]: value }))
  }

  const handleReset = () => {
    setValues(getDefaultValues())
  }

  return { values, handleChange, handleReset }
}

// ============================================================================
// Sub Components
// ============================================================================

function StabilityBadge({ stability }: { stability: string }) {
  const styles: Record<string, { bg: string; color: string }> = {
    stable: { bg: 'var(--color-success-light)', color: 'var(--color-success)' },
    beta: { bg: 'var(--color-warning-light)', color: 'var(--color-warning)' },
    experimental: { bg: 'var(--color-info-light)', color: 'var(--color-info)' },
    deprecated: { bg: 'var(--color-error-light)', color: 'var(--color-error)' },
  }

  const style = styles[stability] ?? styles.stable

  return (
    <span
      className="stability-badge"
      style={{ background: style.bg, color: style.color }}
    >
      {stability}
    </span>
  )
}

function TabBar({
  activeTab,
  onTabChange,
}: {
  activeTab: TabType
  onTabChange: (tab: TabType) => void
}) {
  const tabs: Array<{ id: TabType; label: string }> = [
    { id: 'playground', label: 'Playground' },
    { id: 'examples', label: 'Examples' },
    { id: 'a11y', label: 'Accessibility' },
  ]

  return (
    <div className="button-showcase-tabs">
      {tabs.map((tab) => (
        <span
          key={tab.id}
          className={`button-showcase-tab ${activeTab === tab.id ? 'button-showcase-tab--active' : ''}`}
          onClick={() => onTabChange(tab.id)}
          role="tab"
          tabIndex={0}
          onKeyDown={(e) => e.key === 'Enter' && onTabChange(tab.id)}
        >
          {tab.label}
        </span>
      ))}
    </div>
  )
}

// ============================================================================
// Playground Tab
// ============================================================================

function PlaygroundTab({
  values,
  code,
  onReset,
}: {
  values: Record<string, unknown>
  code: string
  onReset: () => void
}) {
  return (
    <div className="playground-tab">
      {/* Live Preview */}
      <LivePreview code={code} onReset={onReset}>
        <Button
          variant={values.variant as 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger' | 'success'}
          size={values.size as 'sm' | 'md' | 'lg'}
          loading={values.loading as boolean}
          disabled={values.disabled as boolean}
          fullWidth={values.fullWidth as boolean}
          iconPosition={values.iconPosition as 'left' | 'right'}
        >
          {String(values.children ?? 'Button')}
        </Button>
      </LivePreview>

      {/* Variants */}
      <VariantPreview
        title="Variants"
        variants={[
          { label: 'Primary', render: () => <Button variant="primary">Primary</Button> },
          { label: 'Secondary', render: () => <Button variant="secondary">Secondary</Button> },
          { label: 'Outline', render: () => <Button variant="outline">Outline</Button> },
          { label: 'Ghost', render: () => <Button variant="ghost">Ghost</Button> },
          { label: 'Danger', render: () => <Button variant="danger">Danger</Button> },
          { label: 'Success', render: () => <Button variant="success">Success</Button> },
        ]}
      />

      {/* Sizes */}
      <VariantPreview
        title="Sizes"
        variants={[
          { label: 'Small', render: () => <Button size="sm">Small</Button> },
          { label: 'Medium', render: () => <Button size="md">Medium</Button> },
          { label: 'Large', render: () => <Button size="lg">Large</Button> },
        ]}
      />

      {/* States */}
      <StatePreview
        states={[
          {
            label: 'Loading',
            description: '비동기 작업 진행 중',
            render: () => <Button loading>Saving...</Button>,
          },
          {
            label: 'Disabled',
            description: '비활성화 상태',
            render: () => <Button disabled>Disabled</Button>,
          },
          {
            label: 'With Icon',
            description: '아이콘과 함께',
            render: () => (
              <Button variant="primary" icon={<Plus size={16} />}>
                Add Item
              </Button>
            ),
          },
          {
            label: 'Full Width',
            description: '전체 너비',
            render: () => <Button fullWidth>Full Width Button</Button>,
          },
        ]}
      />
    </div>
  )
}

// ============================================================================
// Examples Tab
// ============================================================================

function ExamplesTab() {
  const { examples } = buttonMockData

  return (
    <div className="examples-tab">
      <div className="examples-grid">
        {examples.map((example, index) => (
          <div key={index} className="example-card">
            <h4 className="example-title">{example.title}</h4>
            <p className="example-description">{example.description}</p>
            <pre className="example-code">
              <code>{example.code}</code>
            </pre>
            {example.context ? (
              <span className="example-context">
                <Lightbulb size={14} /> {example.context}
              </span>
            ) : null}
          </div>
        ))}
      </div>

      {/* Anti-Patterns */}
      <h3 className="section-title">
        <AlertTriangle size={18} />
        Anti-Patterns
      </h3>
      <div className="anti-patterns-list">
        {buttonMockData.antiPatterns.map((pattern, index) => (
          <div key={index} className="anti-pattern-card">
            <h4 className="anti-pattern-title">{pattern.title}</h4>
            <p className="anti-pattern-reason">{pattern.reason}</p>
            <div className="anti-pattern-codes">
              <div className="anti-pattern-bad">
                <span className="anti-pattern-label">
                  <X size={14} /> Don&apos;t
                </span>
                <pre><code>{pattern.badCode}</code></pre>
              </div>
              <div className="anti-pattern-good">
                <span className="anti-pattern-label">
                  <Check size={14} /> Do
                </span>
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
// A11y Tab
// ============================================================================

function A11yTab() {
  const { a11yScore } = buttonMockData

  if (!a11yScore) {
    return <div className="a11y-tab"><p>접근성 정보가 없습니다.</p></div>
  }

  const getScoreColor = (score: number): string => {
    if (score >= 90) return 'var(--color-success)'
    if (score >= 70) return 'var(--color-warning)'
    return 'var(--color-error)'
  }

  const categoryNames: Record<string, string> = {
    colorContrast: 'Color Contrast',
    keyboardNavigation: 'Keyboard Navigation',
    ariaLabels: 'ARIA Labels',
    focusManagement: 'Focus Management',
  }

  return (
    <div className="a11y-tab">
      {/* Overall Score */}
      <div className="a11y-score-card">
        <div
          className="a11y-score-value"
          style={{ color: getScoreColor(a11yScore.overall) }}
        >
          {a11yScore.overall}
        </div>
        <div className="a11y-score-label">Overall Score</div>
      </div>

      {/* Category Scores */}
      <div className="a11y-categories">
        {Object.entries(a11yScore.categories).map(([category, score]) => (
          <div key={category} className="a11y-category-item">
            <div className="a11y-category-header">
              <span className="a11y-category-name">{categoryNames[category] ?? category}</span>
              <span className="a11y-category-score">{score}</span>
            </div>
            <div className="a11y-category-bar">
              <div
                className="a11y-category-bar-fill"
                style={{ width: `${score}%`, background: getScoreColor(score) }}
              />
            </div>
          </div>
        ))}
      </div>

      {/* Issues or Success */}
      {a11yScore.issues.length > 0 ? (
        <div className="a11y-issues">
          <h4>Issues</h4>
          {a11yScore.issues.map((issue) => (
            <div key={issue.id} className={`a11y-issue a11y-issue--${issue.severity}`}>
              <span className="a11y-issue-icon">
                {issue.severity === 'error' ? <X size={14} /> : <AlertTriangle size={14} />}
              </span>
              <div className="a11y-issue-content">
                <span className="a11y-issue-message">{issue.message}</span>
                {issue.suggestion ? (
                  <span className="a11y-issue-suggestion">{issue.suggestion}</span>
                ) : null}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="a11y-no-issues">
          <Check size={20} />
          <span>No accessibility issues found!</span>
        </div>
      )}

      {/* Best Practices */}
      <div className="a11y-best-practices">
        <h4>Best Practices</h4>
        <ul>
          <li>아이콘만 있는 버튼에는 반드시 <code>aria-label</code> 추가</li>
          <li>Loading 상태에서는 <code>aria-busy=&quot;true&quot;</code> 자동 적용</li>
          <li>Disabled 상태에서는 <code>aria-disabled=&quot;true&quot;</code> 자동 적용</li>
          <li>키보드 포커스가 명확하게 표시됨</li>
        </ul>
      </div>
    </div>
  )
}

// ============================================================================
// Props Table
// ============================================================================

function PropsTable() {
  const { controls } = buttonMockData

  return (
    <div className="props-section">
      <h3 className="section-title">Props</h3>
      <table className="props-table">
        <thead>
          <tr>
            <th>Prop</th>
            <th>Type</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          {Object.entries(controls).map(([name, control]) => (
            <tr key={name}>
              <td className="props-table-name">{name}</td>
              <td className="props-table-type">
                {control.type === 'select' || control.type === 'radio'
                  ? control.options?.map((o) => `'${o.value}'`).join(' | ')
                  : control.type}
              </td>
              <td className="props-table-default">
                {JSON.stringify(control.defaultValue)}
              </td>
              <td className="props-table-description">{control.description ?? '-'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// ============================================================================
// Main Component
// ============================================================================

export function ButtonShowcase() {
  const [activeTab, setActiveTab] = useState<TabType>('playground')
  const { values, handleChange, handleReset } = usePlayground(buttonMockData.controls)

  const code = useMemo(() => {
    return generateCodeSnippet('Button', values)
  }, [values])

  return (
    <div className="button-showcase">
      {/* Header */}
      <header className="button-showcase-header">
        <div className="button-showcase-title-row">
          <h1 className="button-showcase-title">{buttonMockData.name}</h1>
          <StabilityBadge stability={buttonMockData.stability} />
        </div>
        <p className="button-showcase-description">{buttonMockData.description}</p>
      </header>

      {/* When to Use */}
      {buttonMockData.whenToUse && buttonMockData.whenToUse.length > 0 ? (
        <div className="when-to-use">
          <h4>When to Use</h4>
          <ul>
            {buttonMockData.whenToUse.map((item, i) => (
              <li key={i}>{item}</li>
            ))}
          </ul>
        </div>
      ) : null}

      {/* Tabs */}
      <TabBar activeTab={activeTab} onTabChange={setActiveTab} />

      {/* Content */}
      <div className="button-showcase-content">
        <div className="button-showcase-main">
          {activeTab === 'playground' ? (
            <PlaygroundTab values={values} code={code} onReset={handleReset} />
          ) : null}
          {activeTab === 'examples' ? <ExamplesTab /> : null}
          {activeTab === 'a11y' ? <A11yTab /> : null}
        </div>

        {/* Controls Sidebar */}
        {activeTab === 'playground' ? (
          <aside className="button-showcase-sidebar">
            <ControlsPanel
              controls={buttonMockData.controls}
              values={values}
              onChange={handleChange}
              onReset={handleReset}
            />
          </aside>
        ) : null}
      </div>

      {/* Props Table */}
      <PropsTable />
    </div>
  )
}
