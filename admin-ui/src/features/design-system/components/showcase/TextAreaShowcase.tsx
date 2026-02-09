/**
 * TextArea Showcase Component
 *
 * Phase 3-C: TextArea 컴포넌트 전시
 * Live Preview, Props Playground, 코드 스니펫 등을 제공
 */

import { useState } from 'react'
import { Button, Switch, TextArea } from '@/shared/ui'
import { PageHeader } from '../layout'
import './TextAreaShowcase.css'

// ============================================================================
// Types
// ============================================================================

interface TextAreaPlaygroundState {
  size: 'sm' | 'md' | 'lg'
  placeholder: string
  error: boolean
  errorMessage: string
  helperText: string
  disabled: boolean
  mono: boolean
  rows: number
}

// ============================================================================
// Mock Data
// ============================================================================

const textAreaMockData = {
  description: '여러 줄 텍스트 입력을 받는 TextArea 컴포넌트',
  antiPatterns: [
    {
      title: 'placeholder를 label 대신 사용',
      reason: '접근성 문제 - placeholder는 입력 시 사라짐',
      badCode: `<TextArea placeholder="설명" />`,
      goodCode: `<Label htmlFor="desc">설명</Label>\n<TextArea id="desc" placeholder="상세 내용을 입력하세요" />`,
    },
    {
      title: 'error 없이 errorMessage만 설정',
      reason: 'error 상태가 false면 errorMessage가 표시되지 않음',
      badCode: `<TextArea errorMessage="에러입니다" />`,
      goodCode: `<TextArea error errorMessage="에러입니다" />`,
    },
    {
      title: 'JSON 입력에 mono 미사용',
      reason: '코드/JSON은 모노스페이스 폰트로 가독성 향상',
      badCode: `<TextArea placeholder="Enter JSON" />`,
      goodCode: `<TextArea mono placeholder="Enter JSON" />`,
    },
  ],
  a11yScore: {
    overall: 90,
    categories: {
      colorContrast: 95,
      keyboardNavigation: 88,
      ariaLabels: 85,
      focusManagement: 92,
    },
    issues: [
      {
        id: 'label-required',
        message: 'TextArea에는 연결된 label이 필요합니다',
        severity: 'warning',
        suggestion: 'Label 컴포넌트와 함께 사용하거나 aria-label 추가',
      },
    ],
  },
}

// ============================================================================
// Live Preview Section
// ============================================================================

function LivePreview({ state }: { state: TextAreaPlaygroundState }) {
  const [value, setValue] = useState('')

  return (
    <div className="textarea-showcase-preview">
      <div className="textarea-showcase-preview-area">
        <TextArea
          size={state.size}
          placeholder={state.placeholder}
          error={state.error}
          errorMessage={state.error ? state.errorMessage : undefined}
          helperText={!state.error ? state.helperText : undefined}
          disabled={state.disabled}
          mono={state.mono}
          rows={state.rows}
          value={value}
          onChange={(e) => setValue(e.target.value)}
        />
      </div>
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
  state: TextAreaPlaygroundState
  onChange: (key: keyof TextAreaPlaygroundState, value: unknown) => void
}) {
  return (
    <div className="textarea-showcase-controls">
      <h3 className="textarea-showcase-controls-title">Props Playground</h3>

      <div className="textarea-showcase-control-group">
        <label className="textarea-showcase-control-label">Size</label>
        <div className="textarea-showcase-control-options">
          {(['sm', 'md', 'lg'] as const).map((size) => (
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

      <div className="textarea-showcase-control-group">
        <label className="textarea-showcase-control-label">Placeholder</label>
        <TextArea
          size="sm"
          rows={2}
          value={state.placeholder}
          onChange={(e) => onChange('placeholder', e.target.value)}
        />
      </div>

      <div className="textarea-showcase-control-group">
        <label className="textarea-showcase-control-label">Rows</label>
        <div className="textarea-showcase-control-options">
          {[3, 5, 8, 10].map((rows) => (
            <Button
              key={rows}
              size="sm"
              variant={state.rows === rows ? 'primary' : 'ghost'}
              onClick={() => onChange('rows', rows)}
            >
              {rows}
            </Button>
          ))}
        </div>
      </div>

      <div className="textarea-showcase-control-group">
        <label className="textarea-showcase-control-label">Helper Text</label>
        <TextArea
          size="sm"
          rows={2}
          value={state.helperText}
          onChange={(e) => onChange('helperText', e.target.value)}
          placeholder="도움말 텍스트"
        />
      </div>

      <div className="textarea-showcase-control-group">
        <div className="textarea-showcase-control-row">
          <span className="textarea-showcase-control-label">Error State</span>
          <Switch
            checked={state.error}
            onChange={(checked) => onChange('error', checked)}
            size="sm"
          />
        </div>
      </div>

      {state.error && (
        <div className="textarea-showcase-control-group">
          <label className="textarea-showcase-control-label">Error Message</label>
          <TextArea
            size="sm"
            rows={2}
            value={state.errorMessage}
            onChange={(e) => onChange('errorMessage', e.target.value)}
            placeholder="에러 메시지"
          />
        </div>
      )}

      <div className="textarea-showcase-control-group">
        <div className="textarea-showcase-control-row">
          <span className="textarea-showcase-control-label">Monospace Font</span>
          <Switch
            checked={state.mono}
            onChange={(checked) => onChange('mono', checked)}
            size="sm"
          />
        </div>
      </div>

      <div className="textarea-showcase-control-group">
        <div className="textarea-showcase-control-row">
          <span className="textarea-showcase-control-label">Disabled</span>
          <Switch
            checked={state.disabled}
            onChange={(checked) => onChange('disabled', checked)}
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

function CodePreview({ state }: { state: TextAreaPlaygroundState }) {
  const generateCode = () => {
    const props: string[] = []

    if (state.size !== 'md') props.push(`size="${state.size}"`)
    if (state.placeholder) props.push(`placeholder="${state.placeholder}"`)
    if (state.rows !== 5) props.push(`rows={${state.rows}}`)
    if (state.error) props.push('error')
    if (state.error && state.errorMessage) props.push(`errorMessage="${state.errorMessage}"`)
    if (!state.error && state.helperText) props.push(`helperText="${state.helperText}"`)
    if (state.mono) props.push('mono')
    if (state.disabled) props.push('disabled')

    if (props.length === 0) {
      return '<TextArea />'
    }

    if (props.length <= 2) {
      return `<TextArea ${props.join(' ')} />`
    }

    return `<TextArea\n  ${props.join('\n  ')}\n/>`
  }

  return (
    <div className="textarea-showcase-code">
      <h3 className="textarea-showcase-code-title">Code</h3>
      <pre className="textarea-showcase-code-block">
        <code>{generateCode()}</code>
      </pre>
    </div>
  )
}

// ============================================================================
// Examples Section
// ============================================================================

function ExamplesSection() {
  return (
    <div className="textarea-showcase-examples">
      <h3 className="textarea-showcase-section-title">Examples</h3>

      <div className="textarea-showcase-example-grid">
        {/* Basic TextArea */}
        <div className="textarea-showcase-example-card">
          <h4>Basic TextArea</h4>
          <p>기본 여러 줄 입력</p>
          <div className="textarea-showcase-example-preview">
            <TextArea placeholder="설명을 입력하세요" />
          </div>
          <pre className="textarea-showcase-example-code">
            <code>{`<TextArea placeholder="설명을 입력하세요" />`}</code>
          </pre>
        </div>

        {/* With Error */}
        <div className="textarea-showcase-example-card">
          <h4>With Error</h4>
          <p>에러 상태 표시</p>
          <div className="textarea-showcase-example-preview">
            <TextArea error errorMessage="필수 입력 항목입니다" defaultValue="잘못된 값" />
          </div>
          <pre className="textarea-showcase-example-code">
            <code>{`<TextArea error errorMessage="필수 입력 항목입니다" />`}</code>
          </pre>
        </div>

        {/* With Helper Text */}
        <div className="textarea-showcase-example-card">
          <h4>With Helper Text</h4>
          <p>도움말 텍스트 포함</p>
          <div className="textarea-showcase-example-preview">
            <TextArea placeholder="제품 설명" helperText="최대 500자까지 입력 가능합니다" />
          </div>
          <pre className="textarea-showcase-example-code">
            <code>{`<TextArea helperText="최대 500자까지 입력 가능합니다" />`}</code>
          </pre>
        </div>

        {/* Monospace */}
        <div className="textarea-showcase-example-card">
          <h4>Monospace (Code/JSON)</h4>
          <p>코드/JSON 입력용</p>
          <div className="textarea-showcase-example-preview">
            <TextArea mono placeholder='{"key": "value"}' rows={4} />
          </div>
          <pre className="textarea-showcase-example-code">
            <code>{`<TextArea mono placeholder='{"key": "value"}' />`}</code>
          </pre>
        </div>

        {/* Sizes */}
        <div className="textarea-showcase-example-card">
          <h4>Sizes</h4>
          <p>다양한 크기</p>
          <div className="textarea-showcase-example-preview" style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <TextArea size="sm" placeholder="Small" rows={2} />
            <TextArea size="md" placeholder="Medium" rows={2} />
            <TextArea size="lg" placeholder="Large" rows={2} />
          </div>
          <pre className="textarea-showcase-example-code">
            <code>{`<TextArea size="sm" />\n<TextArea size="md" />\n<TextArea size="lg" />`}</code>
          </pre>
        </div>

        {/* Custom Rows */}
        <div className="textarea-showcase-example-card">
          <h4>Custom Rows</h4>
          <p>높이 조절</p>
          <div className="textarea-showcase-example-preview">
            <TextArea placeholder="긴 텍스트 입력" rows={8} />
          </div>
          <pre className="textarea-showcase-example-code">
            <code>{`<TextArea rows={8} />`}</code>
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
    <div className="textarea-showcase-antipatterns">
      <h3 className="textarea-showcase-section-title">Anti-Patterns</h3>

      <div className="textarea-showcase-antipattern-list">
        {textAreaMockData.antiPatterns.map((pattern, index) => (
          <div key={index} className="textarea-showcase-antipattern-card">
            <h4>{pattern.title}</h4>
            <p className="textarea-showcase-antipattern-reason">{pattern.reason}</p>

            <div className="textarea-showcase-antipattern-comparison">
              <div className="textarea-showcase-antipattern-bad">
                <span className="textarea-showcase-antipattern-label bad">Bad</span>
                <pre><code>{pattern.badCode}</code></pre>
              </div>
              <div className="textarea-showcase-antipattern-good">
                <span className="textarea-showcase-antipattern-label good">Good</span>
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
  const { a11yScore } = textAreaMockData

  return (
    <div className="textarea-showcase-a11y">
      <h3 className="textarea-showcase-section-title">Accessibility</h3>

      <div className="textarea-showcase-a11y-score">
        <div className="textarea-showcase-a11y-overall">
          <span className="textarea-showcase-a11y-score-value">{a11yScore.overall}</span>
          <span className="textarea-showcase-a11y-score-label">Overall Score</span>
        </div>

        <div className="textarea-showcase-a11y-categories">
          {Object.entries(a11yScore.categories).map(([key, value]) => (
            <div key={key} className="textarea-showcase-a11y-category">
              <span className="textarea-showcase-a11y-category-name">
                {key.replace(/([A-Z])/g, ' $1').trim()}
              </span>
              <div className="textarea-showcase-a11y-bar">
                <div
                  className="textarea-showcase-a11y-bar-fill"
                  style={{ width: `${value}%` }}
                />
              </div>
              <span className="textarea-showcase-a11y-category-value">{value}</span>
            </div>
          ))}
        </div>
      </div>

      {a11yScore.issues.length > 0 && (
        <div className="textarea-showcase-a11y-issues">
          <h4>Known Issues</h4>
          {a11yScore.issues.map((issue) => (
            <div key={issue.id} className={`textarea-showcase-a11y-issue ${issue.severity}`}>
              <span className="textarea-showcase-a11y-issue-badge">{issue.severity}</span>
              <span className="textarea-showcase-a11y-issue-message">{issue.message}</span>
              {issue.suggestion && (
                <p className="textarea-showcase-a11y-issue-suggestion">{issue.suggestion}</p>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// ============================================================================
// Main Component
// ============================================================================

export function TextAreaShowcase() {
  const [playgroundState, setPlaygroundState] = useState<TextAreaPlaygroundState>({
    size: 'md',
    placeholder: 'Enter your text here...',
    error: false,
    errorMessage: '필수 입력 항목입니다',
    helperText: '',
    disabled: false,
    mono: false,
    rows: 5,
  })

  const handleChange = (key: keyof TextAreaPlaygroundState, value: unknown) => {
    setPlaygroundState((prev) => ({ ...prev, [key]: value }))
  }

  return (
    <div className="textarea-showcase">
      <PageHeader
        title="TextArea"
        description={textAreaMockData.description}
        stability="stable"
      />

      {/* Playground Section */}
      <section className="textarea-showcase-section">
        <div className="textarea-showcase-playground">
          <LivePreview state={playgroundState} />
          <ControlsPanel state={playgroundState} onChange={handleChange} />
        </div>
        <CodePreview state={playgroundState} />
      </section>

      {/* Examples Section */}
      <section className="textarea-showcase-section">
        <ExamplesSection />
      </section>

      {/* Anti-Patterns Section */}
      <section className="textarea-showcase-section">
        <AntiPatternsSection />
      </section>

      {/* A11y Section */}
      <section className="textarea-showcase-section">
        <A11ySection />
      </section>
    </div>
  )
}
