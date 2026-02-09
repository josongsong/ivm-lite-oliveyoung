/**
 * Resource Page - 리소스 페이지
 *
 * Getting Started, Changelog, Contributing 등 문서 페이지를 제공합니다.
 */

import { Link, useParams } from 'react-router-dom'
import { BookOpen, Check, Construction, Copy, FileCode, Package, Terminal, Wrench } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/shared/ui'

// ============================================================================
// Resource Data
// ============================================================================

interface ResourceInfo {
  title: string
  description: string
  icon: React.ReactNode
  content: React.ReactNode
}

// ============================================================================
// Getting Started Content
// ============================================================================

function GettingStartedContent() {
  const [copiedCommand, setCopiedCommand] = useState<string | null>(null)

  const copyToClipboard = (text: string, id: string) => {
    navigator.clipboard.writeText(text)
    setCopiedCommand(id)
    setTimeout(() => setCopiedCommand(null), 2000)
  }

  const CodeBlock = ({ code, id }: { code: string; id: string }) => (
    <div style={{
      position: 'relative',
      background: 'var(--bg-tertiary)',
      borderRadius: '8px',
      padding: '1rem',
      fontFamily: 'monospace',
      fontSize: '0.875rem',
    }}>
      <code>{code}</code>
      <Button
        variant="ghost"
        size="sm"
        style={{ position: 'absolute', top: '0.5rem', right: '0.5rem' }}
        onClick={() => copyToClipboard(code, id)}
      >
        {copiedCommand === id ? <Check size={14} /> : <Copy size={14} />}
      </Button>
    </div>
  )

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
      {/* Step 1 */}
      <section>
        <h2 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
          <Package size={20} style={{ color: 'var(--accent-cyan)' }} />
          1. 설치
        </h2>
        <p style={{ color: 'var(--text-secondary)', marginBottom: '1rem' }}>
          shared/ui 컴포넌트는 이 프로젝트에 포함되어 있습니다. 외부 프로젝트에서 사용하려면 복사하세요.
        </p>
        <CodeBlock
          code="cp -r admin-ui/src/shared/ui/* your-project/src/components/"
          id="copy-cmd"
        />
      </section>

      {/* Step 2 */}
      <section>
        <h2 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
          <Terminal size={20} style={{ color: 'var(--accent-cyan)' }} />
          2. 의존성 설치
        </h2>
        <p style={{ color: 'var(--text-secondary)', marginBottom: '1rem' }}>
          일부 컴포넌트는 lucide-react 아이콘을 사용합니다.
        </p>
        <CodeBlock
          code="pnpm add lucide-react"
          id="dep-cmd"
        />
      </section>

      {/* Step 3 */}
      <section>
        <h2 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
          <FileCode size={20} style={{ color: 'var(--accent-cyan)' }} />
          3. 사용 예시
        </h2>
        <CodeBlock
          code={`import { Button, Input, Select } from '@/shared/ui'

function MyComponent() {
  return (
    <div>
      <Button variant="primary">Click me</Button>
      <Input placeholder="Enter text..." />
    </div>
  )
}`}
          id="usage-cmd"
        />
      </section>

      {/* Component List */}
      <section>
        <h2 style={{ marginBottom: '1rem' }}>사용 가능한 컴포넌트</h2>
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))',
          gap: '0.5rem',
        }}>
          {['Button', 'IconButton', 'Input', 'TextArea', 'Select', 'Switch', 'Tabs', 'Modal', 'Table', 'Pagination', 'Loading', 'StatusBadge', 'Chip', 'Accordion', 'Label'].map((name) => (
            <Link
              key={name}
              to={`/design-system/components/actions/${name.toLowerCase()}`}
              style={{
                padding: '0.5rem 1rem',
                background: 'var(--bg-secondary)',
                borderRadius: '6px',
                textDecoration: 'none',
                color: 'var(--text-primary)',
                fontSize: '0.875rem',
                border: '1px solid var(--border-color)',
              }}
            >
              {name}
            </Link>
          ))}
        </div>
      </section>
    </div>
  )
}

// ============================================================================
// Changelog Content
// ============================================================================

function ChangelogContent() {
  const changes = [
    {
      version: '1.0.0',
      date: '2026-02-01',
      items: [
        { type: 'added', text: 'Design System Catalog 초기 버전 출시' },
        { type: 'added', text: 'Foundations: Colors, Typography, Spacing, Shadows, Motion' },
        { type: 'added', text: 'Components: Button, Input, Select Showcase' },
      ],
    },
    {
      version: '0.9.0',
      date: '2026-01-15',
      items: [
        { type: 'added', text: 'shared/ui 컴포넌트 라이브러리 구축' },
        { type: 'added', text: 'Button, Input, Select, Modal, Table 등 기본 컴포넌트' },
      ],
    },
  ]

  const typeColors: Record<string, string> = {
    added: 'var(--accent-green)',
    changed: 'var(--accent-cyan)',
    fixed: 'var(--accent-yellow)',
    removed: 'var(--accent-red)',
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
      {changes.map((release) => (
        <section key={release.version}>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: '1rem', marginBottom: '1rem' }}>
            <h2 style={{ margin: 0, color: 'var(--accent-purple)' }}>v{release.version}</h2>
            <span style={{ color: 'var(--text-muted)', fontSize: '0.875rem' }}>{release.date}</span>
          </div>
          <ul style={{ margin: 0, paddingLeft: '1.5rem', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            {release.items.map((item, idx) => (
              <li key={idx} style={{ color: 'var(--text-secondary)' }}>
                <span style={{
                  color: typeColors[item.type] || 'var(--text-primary)',
                  fontWeight: 600,
                  textTransform: 'uppercase',
                  fontSize: '0.75rem',
                  marginRight: '0.5rem',
                }}>
                  [{item.type}]
                </span>
                {item.text}
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}

// ============================================================================
// Resources Registry
// ============================================================================

const RESOURCES: Record<string, ResourceInfo> = {
  'getting-started': {
    title: 'Getting Started',
    description: 'Design System을 프로젝트에 적용하는 방법을 안내합니다.',
    icon: <BookOpen size={32} />,
    content: <GettingStartedContent />,
  },
  changelog: {
    title: 'Changelog',
    description: 'Design System의 변경 이력입니다.',
    icon: <FileCode size={32} />,
    content: <ChangelogContent />,
  },
  contributing: {
    title: 'Contributing',
    description: 'Design System에 기여하는 방법을 안내합니다.',
    icon: <Wrench size={32} />,
    content: null, // 추후 구현
  },
}

// ============================================================================
// Main Component
// ============================================================================

export function ResourcePage() {
  const { resource } = useParams<{ resource: string }>()
  const resourceInfo = resource ? RESOURCES[resource] : null

  if (!resourceInfo) {
    return (
      <div className="ds-placeholder">
        <Construction size={48} className="ds-placeholder-icon" />
        <h2 className="ds-placeholder-title">리소스를 찾을 수 없습니다</h2>
        <p className="ds-placeholder-description">
          요청한 리소스 "{resource}"을 찾을 수 없습니다.
        </p>
        <Link
          to="/design-system/resources/getting-started"
          style={{
            marginTop: '1rem',
            color: 'var(--accent-cyan)',
            textDecoration: 'none',
          }}
        >
          ← Getting Started로 이동
        </Link>
      </div>
    )
  }

  return (
    <div className="ds-section">
      <header className="ds-section-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.5rem' }}>
          <span style={{ color: 'var(--accent-cyan)' }}>{resourceInfo.icon}</span>
          <h1 className="ds-section-title" style={{ margin: 0 }}>{resourceInfo.title}</h1>
        </div>
        <p className="ds-section-description">{resourceInfo.description}</p>
      </header>

      {resourceInfo.content ? (
        resourceInfo.content
      ) : (
        <div className="ds-placeholder" style={{ minHeight: '300px' }}>
          <Construction size={48} className="ds-placeholder-icon" />
          <h2 className="ds-placeholder-title">추후 구현 예정</h2>
          <p className="ds-placeholder-description">
            이 섹션은 추후 상세 구현될 예정입니다.
          </p>
        </div>
      )}
    </div>
  )
}
