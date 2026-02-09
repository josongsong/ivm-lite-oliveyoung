/**
 * Switch Showcase Component
 *
 * Phase 3-D: Switch 컴포넌트 전시
 * Live Preview, Props Playground, 코드 스니펫 등을 제공
 */

import { useState } from 'react'
import { Button, Input, Switch, ToggleGroup } from '@/shared/ui'
import { PageHeader } from '../layout'
import './SwitchShowcase.css'

// ============================================================================
// Types
// ============================================================================

interface SwitchPlaygroundState {
  size: 'sm' | 'md' | 'lg'
  label: string
  description: string
  disabled: boolean
  labelPosition: 'left' | 'right'
}

// ============================================================================
// Mock Data
// ============================================================================

const switchMockData = {
  description: '온/오프 토글 상태를 표시하는 Switch 컴포넌트',
  antiPatterns: [
    {
      title: 'Switch로 다중 선택 구현',
      reason: 'Switch는 단일 온/오프용, 다중 선택은 Checkbox 그룹 사용',
      badCode: `<Switch label="Option A" />\n<Switch label="Option B" />\n<Switch label="Option C" />`,
      goodCode: `<CheckboxGroup\n  options={['Option A', 'Option B', 'Option C']}\n  value={selected}\n  onChange={setSelected}\n/>`,
    },
    {
      title: 'onChange 없이 checked만 설정',
      reason: '제어 컴포넌트로 동작하므로 onChange 필수',
      badCode: `<Switch checked={true} />`,
      goodCode: `<Switch checked={enabled} onChange={setEnabled} />`,
    },
    {
      title: 'label 없이 시각적 의미만 전달',
      reason: '접근성을 위해 label 또는 aria-label 필수',
      badCode: `<Switch checked={darkMode} onChange={setDarkMode} />`,
      goodCode: `<Switch\n  checked={darkMode}\n  onChange={setDarkMode}\n  label="다크 모드"\n/>`,
    },
  ],
  a11yScore: {
    overall: 94,
    categories: {
      colorContrast: 100,
      keyboardNavigation: 95,
      ariaLabels: 90,
      focusManagement: 92,
    },
    issues: [],
  },
}

// ============================================================================
// Live Preview Section
// ============================================================================

function LivePreview({ state }: { state: SwitchPlaygroundState }) {
  const [checked, setChecked] = useState(false)

  return (
    <div className="switch-showcase-preview">
      <div className="switch-showcase-preview-area">
        <Switch
          size={state.size}
          checked={checked}
          onChange={setChecked}
          label={state.label || undefined}
          description={state.description || undefined}
          disabled={state.disabled}
          labelPosition={state.labelPosition}
        />
      </div>
      <div className="switch-showcase-preview-status">
        State: <strong>{checked ? 'ON' : 'OFF'}</strong>
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
  state: SwitchPlaygroundState
  onChange: (key: keyof SwitchPlaygroundState, value: unknown) => void
}) {
  return (
    <div className="switch-showcase-controls">
      <h3 className="switch-showcase-controls-title">Props Playground</h3>

      <div className="switch-showcase-control-group">
        <label className="switch-showcase-control-label">Size</label>
        <div className="switch-showcase-control-options">
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

      <div className="switch-showcase-control-group">
        <label className="switch-showcase-control-label">Label</label>
        <Input
          size="sm"
          value={state.label}
          onChange={(e) => onChange('label', e.target.value)}
          placeholder="라벨 텍스트"
        />
      </div>

      <div className="switch-showcase-control-group">
        <label className="switch-showcase-control-label">Description</label>
        <Input
          size="sm"
          value={state.description}
          onChange={(e) => onChange('description', e.target.value)}
          placeholder="설명 텍스트"
        />
      </div>

      <div className="switch-showcase-control-group">
        <label className="switch-showcase-control-label">Label Position</label>
        <div className="switch-showcase-control-options">
          {(['left', 'right'] as const).map((pos) => (
            <Button
              key={pos}
              size="sm"
              variant={state.labelPosition === pos ? 'primary' : 'ghost'}
              onClick={() => onChange('labelPosition', pos)}
            >
              {pos.charAt(0).toUpperCase() + pos.slice(1)}
            </Button>
          ))}
        </div>
      </div>

      <div className="switch-showcase-control-group">
        <div className="switch-showcase-control-row">
          <span className="switch-showcase-control-label">Disabled</span>
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

function CodePreview({ state }: { state: SwitchPlaygroundState }) {
  const generateCode = () => {
    const props: string[] = ['checked={enabled}', 'onChange={setEnabled}']

    if (state.size !== 'md') props.push(`size="${state.size}"`)
    if (state.label) props.push(`label="${state.label}"`)
    if (state.description) props.push(`description="${state.description}"`)
    if (state.labelPosition !== 'right') props.push(`labelPosition="${state.labelPosition}"`)
    if (state.disabled) props.push('disabled')

    if (props.length <= 3) {
      return `<Switch ${props.join(' ')} />`
    }

    return `<Switch\n  ${props.join('\n  ')}\n/>`
  }

  return (
    <div className="switch-showcase-code">
      <h3 className="switch-showcase-code-title">Code</h3>
      <pre className="switch-showcase-code-block">
        <code>{generateCode()}</code>
      </pre>
    </div>
  )
}

// ============================================================================
// Examples Section
// ============================================================================

function ExamplesSection() {
  const [enabled, setEnabled] = useState(false)
  const [notifications, setNotifications] = useState(true)
  const [toggleValue, setToggleValue] = useState('option1')

  return (
    <div className="switch-showcase-examples">
      <h3 className="switch-showcase-section-title">Examples</h3>

      <div className="switch-showcase-example-grid">
        {/* Basic Switch */}
        <div className="switch-showcase-example-card">
          <h4>Basic Switch</h4>
          <p>기본 토글 스위치</p>
          <div className="switch-showcase-example-preview">
            <Switch checked={enabled} onChange={setEnabled} />
          </div>
          <pre className="switch-showcase-example-code">
            <code>{`<Switch checked={enabled} onChange={setEnabled} />`}</code>
          </pre>
        </div>

        {/* With Label */}
        <div className="switch-showcase-example-card">
          <h4>With Label</h4>
          <p>라벨이 있는 스위치</p>
          <div className="switch-showcase-example-preview">
            <Switch
              checked={notifications}
              onChange={setNotifications}
              label="알림 받기"
            />
          </div>
          <pre className="switch-showcase-example-code">
            <code>{`<Switch label="알림 받기" checked={enabled} onChange={setEnabled} />`}</code>
          </pre>
        </div>

        {/* With Description */}
        <div className="switch-showcase-example-card">
          <h4>With Description</h4>
          <p>설명 포함</p>
          <div className="switch-showcase-example-preview">
            <Switch
              label="마케팅 수신"
              description="이벤트, 프로모션 정보를 받습니다"
            />
          </div>
          <pre className="switch-showcase-example-code">
            <code>{`<Switch\n  label="마케팅 수신"\n  description="이벤트, 프로모션 정보를 받습니다"\n/>`}</code>
          </pre>
        </div>

        {/* Sizes */}
        <div className="switch-showcase-example-card">
          <h4>Sizes</h4>
          <p>다양한 크기</p>
          <div className="switch-showcase-example-preview" style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
            <Switch size="sm" label="SM" defaultChecked />
            <Switch size="md" label="MD" defaultChecked />
            <Switch size="lg" label="LG" defaultChecked />
          </div>
          <pre className="switch-showcase-example-code">
            <code>{`<Switch size="sm" />\n<Switch size="md" />\n<Switch size="lg" />`}</code>
          </pre>
        </div>

        {/* Disabled */}
        <div className="switch-showcase-example-card">
          <h4>Disabled States</h4>
          <p>비활성화 상태</p>
          <div className="switch-showcase-example-preview" style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
            <Switch disabled label="Off" />
            <Switch disabled defaultChecked label="On" />
          </div>
          <pre className="switch-showcase-example-code">
            <code>{`<Switch disabled />\n<Switch disabled checked />`}</code>
          </pre>
        </div>

        {/* Toggle Group */}
        <div className="switch-showcase-example-card">
          <h4>Toggle Group</h4>
          <p>라디오 형태 토글</p>
          <div className="switch-showcase-example-preview">
            <ToggleGroup
              options={[
                { value: 'option1', label: '일간' },
                { value: 'option2', label: '주간' },
                { value: 'option3', label: '월간' },
              ]}
              value={toggleValue}
              onChange={setToggleValue}
            />
          </div>
          <pre className="switch-showcase-example-code">
            <code>{`<ToggleGroup\n  options={[...]}\n  value={value}\n  onChange={setValue}\n/>`}</code>
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
    <div className="switch-showcase-antipatterns">
      <h3 className="switch-showcase-section-title">Anti-Patterns</h3>

      <div className="switch-showcase-antipattern-list">
        {switchMockData.antiPatterns.map((pattern, index) => (
          <div key={index} className="switch-showcase-antipattern-card">
            <h4>{pattern.title}</h4>
            <p className="switch-showcase-antipattern-reason">{pattern.reason}</p>

            <div className="switch-showcase-antipattern-comparison">
              <div className="switch-showcase-antipattern-bad">
                <span className="switch-showcase-antipattern-label bad">Bad</span>
                <pre><code>{pattern.badCode}</code></pre>
              </div>
              <div className="switch-showcase-antipattern-good">
                <span className="switch-showcase-antipattern-label good">Good</span>
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
  const { a11yScore } = switchMockData

  return (
    <div className="switch-showcase-a11y">
      <h3 className="switch-showcase-section-title">Accessibility</h3>

      <div className="switch-showcase-a11y-score">
        <div className="switch-showcase-a11y-overall">
          <span className="switch-showcase-a11y-score-value">{a11yScore.overall}</span>
          <span className="switch-showcase-a11y-score-label">Overall Score</span>
        </div>

        <div className="switch-showcase-a11y-categories">
          {Object.entries(a11yScore.categories).map(([key, value]) => (
            <div key={key} className="switch-showcase-a11y-category">
              <span className="switch-showcase-a11y-category-name">
                {key.replace(/([A-Z])/g, ' $1').trim()}
              </span>
              <div className="switch-showcase-a11y-bar">
                <div
                  className="switch-showcase-a11y-bar-fill"
                  style={{ width: `${value}%` }}
                />
              </div>
              <span className="switch-showcase-a11y-category-value">{value}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="switch-showcase-a11y-best-practices">
        <h4>Best Practices</h4>
        <ul>
          <li>항상 <code>label</code> 또는 <code>aria-label</code>을 제공하세요</li>
          <li>Switch는 즉시 효과가 적용되는 설정에 사용하세요</li>
          <li>확인이 필요한 경우 Checkbox + Submit 패턴을 사용하세요</li>
          <li>role="switch"와 aria-checked가 자동으로 적용됩니다</li>
        </ul>
      </div>
    </div>
  )
}

// ============================================================================
// Main Component
// ============================================================================

export function SwitchShowcase() {
  const [playgroundState, setPlaygroundState] = useState<SwitchPlaygroundState>({
    size: 'md',
    label: '알림 설정',
    description: '',
    disabled: false,
    labelPosition: 'right',
  })

  const handleChange = (key: keyof SwitchPlaygroundState, value: unknown) => {
    setPlaygroundState((prev) => ({ ...prev, [key]: value }))
  }

  return (
    <div className="switch-showcase">
      <PageHeader
        title="Switch"
        description={switchMockData.description}
        stability="stable"
      />

      {/* Playground Section */}
      <section className="switch-showcase-section">
        <div className="switch-showcase-playground">
          <LivePreview state={playgroundState} />
          <ControlsPanel state={playgroundState} onChange={handleChange} />
        </div>
        <CodePreview state={playgroundState} />
      </section>

      {/* Examples Section */}
      <section className="switch-showcase-section">
        <ExamplesSection />
      </section>

      {/* Anti-Patterns Section */}
      <section className="switch-showcase-section">
        <AntiPatternsSection />
      </section>

      {/* A11y Section */}
      <section className="switch-showcase-section">
        <A11ySection />
      </section>
    </div>
  )
}
