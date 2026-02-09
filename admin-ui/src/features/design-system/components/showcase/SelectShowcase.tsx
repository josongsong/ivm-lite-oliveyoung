/**
 * Select Showcase Component
 * Phase 3-C: Select 컴포넌트 전시 페이지
 */
import { useState } from 'react'
import {
  AlertTriangle,
  Ban,
  Check,
  Code,
  Copy,
  Eye,
  FileText,
  Flag,
  Globe,
  Lightbulb,
  Palette,
  Settings,
  User,
} from 'lucide-react'
import { Button, Input, Select, Switch, Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui'
import { useClipboard } from '../../hooks/useClipboard'
import { selectMockData } from '../../data/mockData'
import { PageHeader } from '../../components/layout'
import './SelectShowcase.css'

// ============================================================================
// Sample Options
// ============================================================================

const basicOptions = [
  { value: 'opt1', label: 'Option 1' },
  { value: 'opt2', label: 'Option 2' },
  { value: 'opt3', label: 'Option 3' },
  { value: 'opt4', label: 'Option 4' },
]

const countryOptions = [
  { value: 'kr', label: '한국', icon: <Flag size={14} /> },
  { value: 'us', label: 'United States', icon: <Flag size={14} /> },
  { value: 'jp', label: '日本', icon: <Flag size={14} /> },
  { value: 'cn', label: '中国', icon: <Flag size={14} /> },
]

const statusOptions = [
  { value: 'active', label: 'Active' },
  { value: 'pending', label: 'Pending' },
  { value: 'inactive', label: 'Inactive', disabled: true },
  { value: 'archived', label: 'Archived', disabled: true },
]

const roleOptions = [
  { value: 'admin', label: 'Administrator', icon: <User size={14} /> },
  { value: 'editor', label: 'Editor', icon: <User size={14} /> },
  { value: 'viewer', label: 'Viewer', icon: <User size={14} /> },
]

const colorOptions = [
  { value: 'red', label: 'Red' },
  { value: 'blue', label: 'Blue' },
  { value: 'green', label: 'Green' },
  { value: 'purple', label: 'Purple' },
]

// ============================================================================
// Preview Tab Component
// ============================================================================

function PreviewTab() {
  const [basicValue, setBasicValue] = useState('')
  const [countryValue, setCountryValue] = useState('')
  const [statusValue, setStatusValue] = useState('active')
  const [roleValue, setRoleValue] = useState('')
  const [colorValue, setColorValue] = useState('')

  return (
    <>
      <section className="showcase-section">
        <h3 className="section-title">Sizes</h3>
        <p className="section-description">Select는 sm, md, lg 3가지 사이즈를 지원합니다.</p>
        <div className="preview-grid">
          <div className="preview-item">
            <span className="preview-label">Small</span>
            <Select value={basicValue} onChange={setBasicValue} options={basicOptions} size="sm" placeholder="Small" />
          </div>
          <div className="preview-item">
            <span className="preview-label">Medium</span>
            <Select value={basicValue} onChange={setBasicValue} options={basicOptions} size="md" placeholder="Medium" />
          </div>
          <div className="preview-item">
            <span className="preview-label">Large</span>
            <Select value={basicValue} onChange={setBasicValue} options={basicOptions} size="lg" placeholder="Large" />
          </div>
        </div>
      </section>

      <section className="showcase-section">
        <h3 className="section-title">With Icons</h3>
        <p className="section-description">아이콘을 통해 Select의 용도를 명확히 할 수 있습니다.</p>
        <div className="preview-grid">
          <div className="preview-item">
            <span className="preview-label">국가 선택</span>
            <Select value={countryValue} onChange={setCountryValue} options={countryOptions} icon={<Globe size={16} />} placeholder="Select country" />
          </div>
          <div className="preview-item">
            <span className="preview-label">역할 선택</span>
            <Select value={roleValue} onChange={setRoleValue} options={roleOptions} icon={<User size={16} />} placeholder="Select role" />
          </div>
          <div className="preview-item">
            <span className="preview-label">색상 선택</span>
            <Select value={colorValue} onChange={setColorValue} options={colorOptions} icon={<Palette size={16} />} placeholder="Select color" />
          </div>
        </div>
      </section>

      <section className="showcase-section">
        <h3 className="section-title">States</h3>
        <p className="section-description">disabled 옵션과 개별 옵션의 disabled 상태를 지원합니다.</p>
        <div className="preview-grid">
          <div className="preview-item">
            <span className="preview-label">Normal</span>
            <Select value={statusValue} onChange={setStatusValue} options={statusOptions} placeholder="Select status" />
          </div>
          <div className="preview-item">
            <span className="preview-label">Disabled</span>
            <Select value="active" onChange={() => {}} options={statusOptions} disabled placeholder="Disabled" />
          </div>
          <div className="preview-item">
            <span className="preview-label">With Disabled Options</span>
            <Select value={statusValue} onChange={setStatusValue} options={statusOptions} placeholder="Some options disabled" />
          </div>
        </div>
      </section>
    </>
  )
}

// ============================================================================
// Playground Tab Component
// ============================================================================

function PlaygroundTab() {
  const { copy, copied } = useClipboard()
  const [playgroundValue, setPlaygroundValue] = useState('')
  const [playgroundSize, setPlaygroundSize] = useState<'sm' | 'md' | 'lg'>('md')
  const [playgroundDisabled, setPlaygroundDisabled] = useState(false)
  const [playgroundWithIcon, setPlaygroundWithIcon] = useState(false)
  const [playgroundPlaceholder, setPlaygroundPlaceholder] = useState('Select an option...')

  const handleDisabledChange = (checked: boolean) => setPlaygroundDisabled(checked)
  const handleWithIconChange = (checked: boolean) => setPlaygroundWithIcon(checked)

  const generateCode = () => {
    const props: string[] = ['value={value}', 'onChange={setValue}']
    if (playgroundPlaceholder !== 'Select...') props.push(`placeholder="${playgroundPlaceholder}"`)
    if (playgroundSize !== 'md') props.push(`size="${playgroundSize}"`)
    if (playgroundDisabled) props.push('disabled')
    if (playgroundWithIcon) props.push('icon={<Globe size={16} />}')

    return `import { Select } from '@/shared/ui'

const options = [
  { value: 'opt1', label: 'Option 1' },
  { value: 'opt2', label: 'Option 2' },
  { value: 'opt3', label: 'Option 3' },
]

function MyComponent() {
  const [value, setValue] = useState('')

  return (
    <Select
      ${props.join('\n      ')}
      options={options}
    />
  )
}`
  }

  const codeSnippet = generateCode()

  return (
    <div className="playground-container">
      <div className="playground-preview">
        <div className="playground-canvas">
          <Select
            value={playgroundValue}
            onChange={setPlaygroundValue}
            options={basicOptions}
            size={playgroundSize}
            disabled={playgroundDisabled}
            placeholder={playgroundPlaceholder}
            icon={playgroundWithIcon ? <Globe size={16} /> : undefined}
          />
        </div>
      </div>

      <div className="playground-controls">
        <h4 className="controls-title">Props</h4>
        <div className="control-group">
          <label className="control-label">Size</label>
          <Select
            value={playgroundSize}
            onChange={(v) => setPlaygroundSize(v as 'sm' | 'md' | 'lg')}
            options={[{ value: 'sm', label: 'Small' }, { value: 'md', label: 'Medium' }, { value: 'lg', label: 'Large' }]}
            size="sm"
          />
        </div>
        <div className="control-group">
          <label className="control-label">Placeholder</label>
          <Input size="sm" value={playgroundPlaceholder} onChange={(e) => setPlaygroundPlaceholder(e.target.value)} />
        </div>
        <div className="control-group">
          <label className="control-label">Disabled</label>
          <Switch checked={playgroundDisabled} onChange={handleDisabledChange} />
        </div>
        <div className="control-group">
          <label className="control-label">With Icon</label>
          <Switch checked={playgroundWithIcon} onChange={handleWithIconChange} />
        </div>
      </div>

      <div className="playground-code">
        <div className="code-header">
          <span className="code-title"><Code size={14} /> Code</span>
          <Button variant="ghost" size="sm" onClick={() => copy(codeSnippet)} icon={copied ? <Check size={14} /> : <Copy size={14} />}>
            {copied ? 'Copied!' : 'Copy'}
          </Button>
        </div>
        <pre className="code-block"><code>{codeSnippet}</code></pre>
      </div>
    </div>
  )
}

// ============================================================================
// Examples Tab Component
// ============================================================================

function ExamplesTab() {
  const { copy } = useClipboard()

  return (
    <>
      <section className="showcase-section">
        <h3 className="section-title"><Lightbulb size={18} /> 사용 예시</h3>
        <div className="examples-grid">
          {selectMockData.examples.map((example, index) => (
            <div key={index} className="example-card">
              <div className="example-header">
                <h4 className="example-title">{example.title}</h4>
                <Button variant="ghost" size="sm" onClick={() => copy(example.code)} icon={<Copy size={14} />} />
              </div>
              <p className="example-description">{example.description}</p>
              {example.context ? <span className="example-context">{example.context}</span> : null}
              <pre className="example-code"><code>{example.code}</code></pre>
            </div>
          ))}
        </div>
      </section>

      <section className="showcase-section">
        <h3 className="section-title danger"><Ban size={18} /> Anti-patterns</h3>
        <div className="antipatterns-grid">
          {selectMockData.antiPatterns.map((pattern, index) => (
            <div key={index} className="antipattern-card">
              <h4 className="antipattern-title"><AlertTriangle size={16} /> {pattern.title}</h4>
              <p className="antipattern-reason">{pattern.reason}</p>
              <div className="antipattern-comparison">
                <div className="code-bad">
                  <span className="code-label">Bad</span>
                  <pre><code>{pattern.badCode}</code></pre>
                </div>
                <div className="code-good">
                  <span className="code-label">Good</span>
                  <pre><code>{pattern.goodCode}</code></pre>
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>
    </>
  )
}

// ============================================================================
// API Tab Component
// ============================================================================

function ApiTab() {
  return (
    <>
      <section className="showcase-section">
        <h3 className="section-title">Props</h3>
        <div className="props-table-wrapper">
          <table className="props-table">
            <thead>
              <tr><th>Name</th><th>Type</th><th>Default</th><th>Description</th></tr>
            </thead>
            <tbody>
              <tr><td><code>value</code></td><td><code>string</code></td><td>-</td><td>선택된 값 (required)</td></tr>
              <tr><td><code>onChange</code></td><td><code>(value: string) =&gt; void</code></td><td>-</td><td>값 변경 핸들러 (required)</td></tr>
              <tr><td><code>options</code></td><td><code>SelectOption[]</code></td><td>-</td><td>옵션 배열 (required)</td></tr>
              <tr><td><code>placeholder</code></td><td><code>string</code></td><td><code>&quot;Select...&quot;</code></td><td>플레이스홀더 텍스트</td></tr>
              <tr><td><code>size</code></td><td><code>&quot;sm&quot; | &quot;md&quot; | &quot;lg&quot;</code></td><td><code>&quot;md&quot;</code></td><td>Select 크기</td></tr>
              <tr><td><code>disabled</code></td><td><code>boolean</code></td><td><code>false</code></td><td>비활성화 상태</td></tr>
              <tr><td><code>icon</code></td><td><code>ReactNode</code></td><td>-</td><td>좌측 아이콘</td></tr>
              <tr><td><code>className</code></td><td><code>string</code></td><td>-</td><td>추가 CSS 클래스</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <section className="showcase-section">
        <h3 className="section-title">SelectOption Type</h3>
        <pre className="code-block">
          <code>{`interface SelectOption {
  value: string
  label: string
  icon?: ReactNode
  disabled?: boolean
}`}</code>
        </pre>
      </section>

      <section className="showcase-section">
        <h3 className="section-title">Keyboard Navigation</h3>
        <div className="keyboard-guide">
          <div className="keyboard-item"><kbd>Enter</kbd> / <kbd>Space</kbd><span>드롭다운 열기/닫기</span></div>
          <div className="keyboard-item"><kbd>↑</kbd> <kbd>↓</kbd><span>옵션 이동</span></div>
          <div className="keyboard-item"><kbd>Escape</kbd><span>드롭다운 닫기</span></div>
        </div>
      </section>

      <section className="showcase-section">
        <h3 className="section-title">Accessibility</h3>
        <div className="a11y-info">
          <div className="a11y-score">
            <span className="score-value">{selectMockData.a11yScore?.overall ?? '-'}</span>
            <span className="score-label">A11y Score</span>
          </div>
          <ul className="a11y-features">
            <li><code>aria-haspopup=&quot;listbox&quot;</code> - 드롭다운 타입 표시</li>
            <li><code>aria-expanded</code> - 열림/닫힘 상태</li>
            <li><code>role=&quot;listbox&quot;</code> / <code>role=&quot;option&quot;</code> - 역할 정의</li>
            <li><code>aria-selected</code> - 선택된 옵션 표시</li>
            <li>완전한 키보드 네비게이션 지원</li>
          </ul>
        </div>
      </section>
    </>
  )
}

// ============================================================================
// Main Component
// ============================================================================

export function SelectShowcase() {
  const [activeTab, setActiveTab] = useState('preview')

  return (
    <div className="select-showcase">
      <PageHeader
        title="Select"
        description={selectMockData.description}
        stability={selectMockData.stability}
        sourceLink="https://github.com/oliveyoung/ivm-lite/blob/main/admin-ui/src/shared/ui/Select.tsx"
      />

      <Tabs value={activeTab} onValueChange={setActiveTab} className="showcase-tabs">
        <TabsList>
          <TabsTrigger value="preview"><Eye size={14} /> Preview</TabsTrigger>
          <TabsTrigger value="playground"><Settings size={14} /> Playground</TabsTrigger>
          <TabsTrigger value="examples"><Lightbulb size={14} /> Examples</TabsTrigger>
          <TabsTrigger value="api"><FileText size={14} /> API</TabsTrigger>
        </TabsList>

        <TabsContent value="preview"><PreviewTab /></TabsContent>
        <TabsContent value="playground"><PlaygroundTab /></TabsContent>
        <TabsContent value="examples"><ExamplesTab /></TabsContent>
        <TabsContent value="api"><ApiTab /></TabsContent>
      </Tabs>
    </div>
  )
}
