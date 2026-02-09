/**
 * Input Showcase Component
 *
 * Phase 3-B: Input 컴포넌트 전시
 * Live Preview, Props Playground, 코드 스니펫 등을 제공
 */

import { useState } from 'react'
import { Eye, EyeOff, Mail, Search } from 'lucide-react'
import { Button, Input, Switch } from '@/shared/ui'
import { inputMockData } from '../../data/mockData'
import { PageHeader } from '../layout'
import './InputShowcase.css'

// ============================================================================
// Types
// ============================================================================

interface InputPlaygroundState {
  size: 'sm' | 'md' | 'lg'
  placeholder: string
  error: boolean
  errorMessage: string
  helperText: string
  disabled: boolean
  leftIcon: boolean
  rightIcon: boolean
}

// ============================================================================
// Live Preview Section
// ============================================================================

function LivePreview({ state }: { state: InputPlaygroundState }) {
  const [value, setValue] = useState('')
  const [showPassword, setShowPassword] = useState(false)

  return (
    <div className="input-showcase-preview">
      <div className="input-showcase-preview-area">
        <Input
          size={state.size}
          placeholder={state.placeholder}
          error={state.error}
          errorMessage={state.error ? state.errorMessage : undefined}
          helperText={!state.error ? state.helperText : undefined}
          disabled={state.disabled}
          leftIcon={state.leftIcon ? <Search size={16} /> : undefined}
          rightIcon={
            state.rightIcon ? (
              <Button
                icon={showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                size="sm"
                variant="ghost"
                onClick={() => setShowPassword(!showPassword)}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              />
            ) : undefined
          }
          type={state.rightIcon && !showPassword ? 'password' : 'text'}
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
  state: InputPlaygroundState
  onChange: (key: keyof InputPlaygroundState, value: unknown) => void
}) {
  return (
    <div className="input-showcase-controls">
      <h3 className="input-showcase-controls-title">Props Playground</h3>

      <div className="input-showcase-control-group">
        <label className="input-showcase-control-label">Size</label>
        <div className="input-showcase-control-options">
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

      <div className="input-showcase-control-group">
        <label className="input-showcase-control-label">Placeholder</label>
        <Input
          size="sm"
          value={state.placeholder}
          onChange={(e) => onChange('placeholder', e.target.value)}
        />
      </div>

      <div className="input-showcase-control-group">
        <label className="input-showcase-control-label">Helper Text</label>
        <Input
          size="sm"
          value={state.helperText}
          onChange={(e) => onChange('helperText', e.target.value)}
          placeholder="도움말 텍스트"
        />
      </div>

      <div className="input-showcase-control-group">
        <div className="input-showcase-control-row">
          <span className="input-showcase-control-label">Error State</span>
          <Switch
            checked={state.error}
            onChange={(checked) => onChange('error', checked)}
            size="sm"
          />
        </div>
      </div>

      {state.error ? (
        <div className="input-showcase-control-group">
          <label className="input-showcase-control-label">Error Message</label>
          <Input
            size="sm"
            value={state.errorMessage}
            onChange={(e) => onChange('errorMessage', e.target.value)}
            placeholder="에러 메시지"
          />
        </div>
      ) : null}

      <div className="input-showcase-control-group">
        <div className="input-showcase-control-row">
          <span className="input-showcase-control-label">Disabled</span>
          <Switch
            checked={state.disabled}
            onChange={(checked) => onChange('disabled', checked)}
            size="sm"
          />
        </div>
      </div>

      <div className="input-showcase-control-group">
        <div className="input-showcase-control-row">
          <span className="input-showcase-control-label">Left Icon (Search)</span>
          <Switch
            checked={state.leftIcon}
            onChange={(checked) => onChange('leftIcon', checked)}
            size="sm"
          />
        </div>
      </div>

      <div className="input-showcase-control-group">
        <div className="input-showcase-control-row">
          <span className="input-showcase-control-label">Right Icon (Password)</span>
          <Switch
            checked={state.rightIcon}
            onChange={(checked) => onChange('rightIcon', checked)}
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

function CodePreview({ state }: { state: InputPlaygroundState }) {
  const generateCode = () => {
    const props: string[] = []

    if (state.size !== 'md') props.push(`size="${state.size}"`)
    if (state.placeholder) props.push(`placeholder="${state.placeholder}"`)
    if (state.error) props.push('error')
    if (state.error && state.errorMessage) props.push(`errorMessage="${state.errorMessage}"`)
    if (!state.error && state.helperText) props.push(`helperText="${state.helperText}"`)
    if (state.disabled) props.push('disabled')
    if (state.leftIcon) props.push('leftIcon={<Search size={16} />}')
    if (state.rightIcon) props.push('rightIcon={<Eye size={16} />}')

    if (props.length === 0) {
      return '<Input />'
    }

    if (props.length <= 2) {
      return `<Input ${props.join(' ')} />`
    }

    return `<Input\n  ${props.join('\n  ')}\n/>`
  }

  return (
    <div className="input-showcase-code">
      <h3 className="input-showcase-code-title">Code</h3>
      <pre className="input-showcase-code-block">
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
    <div className="input-showcase-examples">
      <h3 className="input-showcase-section-title">Examples</h3>

      <div className="input-showcase-example-grid">
        {/* Basic Input */}
        <div className="input-showcase-example-card">
          <h4>Basic Input</h4>
          <p>기본 텍스트 입력</p>
          <div className="input-showcase-example-preview">
            <Input placeholder="이름을 입력하세요" />
          </div>
          <pre className="input-showcase-example-code">
            <code>{`<Input placeholder="이름을 입력하세요" />`}</code>
          </pre>
        </div>

        {/* With Error */}
        <div className="input-showcase-example-card">
          <h4>With Error</h4>
          <p>에러 상태 표시</p>
          <div className="input-showcase-example-preview">
            <Input error errorMessage="필수 입력 항목입니다" defaultValue="잘못된 값" />
          </div>
          <pre className="input-showcase-example-code">
            <code>{`<Input error errorMessage="필수 입력 항목입니다" />`}</code>
          </pre>
        </div>

        {/* With Helper Text */}
        <div className="input-showcase-example-card">
          <h4>With Helper Text</h4>
          <p>도움말 텍스트 포함</p>
          <div className="input-showcase-example-preview">
            <Input placeholder="비밀번호" helperText="영문, 숫자 조합 8자 이상" />
          </div>
          <pre className="input-showcase-example-code">
            <code>{`<Input helperText="영문, 숫자 조합 8자 이상" />`}</code>
          </pre>
        </div>

        {/* With Left Icon */}
        <div className="input-showcase-example-card">
          <h4>Search Input</h4>
          <p>검색 아이콘 포함</p>
          <div className="input-showcase-example-preview">
            <Input leftIcon={<Search size={16} />} placeholder="검색..." />
          </div>
          <pre className="input-showcase-example-code">
            <code>{`<Input leftIcon={<Search />} placeholder="검색..." />`}</code>
          </pre>
        </div>

        {/* Email Input */}
        <div className="input-showcase-example-card">
          <h4>Email Input</h4>
          <p>이메일 입력 필드</p>
          <div className="input-showcase-example-preview">
            <Input leftIcon={<Mail size={16} />} type="email" placeholder="example@email.com" />
          </div>
          <pre className="input-showcase-example-code">
            <code>{`<Input leftIcon={<Mail />} type="email" />`}</code>
          </pre>
        </div>

        {/* Sizes */}
        <div className="input-showcase-example-card">
          <h4>Sizes</h4>
          <p>다양한 크기</p>
          <div className="input-showcase-example-preview" style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <Input size="sm" placeholder="Small" />
            <Input size="md" placeholder="Medium" />
            <Input size="lg" placeholder="Large" />
          </div>
          <pre className="input-showcase-example-code">
            <code>{`<Input size="sm" />\n<Input size="md" />\n<Input size="lg" />`}</code>
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
    <div className="input-showcase-antipatterns">
      <h3 className="input-showcase-section-title">Anti-Patterns</h3>

      <div className="input-showcase-antipattern-list">
        {inputMockData.antiPatterns.map((pattern, index) => (
          <div key={index} className="input-showcase-antipattern-card">
            <h4>{pattern.title}</h4>
            <p className="input-showcase-antipattern-reason">{pattern.reason}</p>

            <div className="input-showcase-antipattern-comparison">
              <div className="input-showcase-antipattern-bad">
                <span className="input-showcase-antipattern-label bad">Bad</span>
                <pre><code>{pattern.badCode}</code></pre>
              </div>
              <div className="input-showcase-antipattern-good">
                <span className="input-showcase-antipattern-label good">Good</span>
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
  const { a11yScore } = inputMockData

  return (
    <div className="input-showcase-a11y">
      <h3 className="input-showcase-section-title">Accessibility</h3>

      <div className="input-showcase-a11y-score">
        <div className="input-showcase-a11y-overall">
          <span className="input-showcase-a11y-score-value">{a11yScore?.overall}</span>
          <span className="input-showcase-a11y-score-label">Overall Score</span>
        </div>

        <div className="input-showcase-a11y-categories">
          {a11yScore != null && Object.entries(a11yScore.categories).map(([key, value]) => (
            <div key={key} className="input-showcase-a11y-category">
              <span className="input-showcase-a11y-category-name">
                {key.replace(/([A-Z])/g, ' $1').trim()}
              </span>
              <div className="input-showcase-a11y-bar">
                <div
                  className="input-showcase-a11y-bar-fill"
                  style={{ width: `${value}%` }}
                />
              </div>
              <span className="input-showcase-a11y-category-value">{value}</span>
            </div>
          ))}
        </div>
      </div>

      {a11yScore?.issues != null && a11yScore.issues.length > 0 ? (
        <div className="input-showcase-a11y-issues">
          <h4>Known Issues</h4>
          {a11yScore.issues.map((issue) => (
            <div key={issue.id} className={`input-showcase-a11y-issue ${issue.severity}`}>
              <span className="input-showcase-a11y-issue-badge">{issue.severity}</span>
              <span className="input-showcase-a11y-issue-message">{issue.message}</span>
              {issue.suggestion != null ? (
                <p className="input-showcase-a11y-issue-suggestion">{issue.suggestion}</p>
              ) : null}
            </div>
          ))}
        </div>
      ) : null}
    </div>
  )
}

// ============================================================================
// Main Component
// ============================================================================

export function InputShowcase() {
  const [playgroundState, setPlaygroundState] = useState<InputPlaygroundState>({
    size: 'md',
    placeholder: 'Enter text...',
    error: false,
    errorMessage: '필수 입력 항목입니다',
    helperText: '',
    disabled: false,
    leftIcon: false,
    rightIcon: false,
  })

  const handleChange = (key: keyof InputPlaygroundState, value: unknown) => {
    setPlaygroundState((prev) => ({ ...prev, [key]: value }))
  }

  return (
    <div className="input-showcase">
      <PageHeader
        title="Input"
        description={inputMockData.description}
        stability="stable"
      />

      {/* Playground Section */}
      <section className="input-showcase-section">
        <div className="input-showcase-playground">
          <LivePreview state={playgroundState} />
          <ControlsPanel state={playgroundState} onChange={handleChange} />
        </div>
        <CodePreview state={playgroundState} />
      </section>

      {/* Examples Section */}
      <section className="input-showcase-section">
        <ExamplesSection />
      </section>

      {/* Anti-Patterns Section */}
      <section className="input-showcase-section">
        <AntiPatternsSection />
      </section>

      {/* A11y Section */}
      <section className="input-showcase-section">
        <A11ySection />
      </section>
    </div>
  )
}
