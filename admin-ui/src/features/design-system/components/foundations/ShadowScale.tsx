/**
 * ShadowScale Component
 * Phase 4-D: Shadows Foundation
 *
 * 그림자 레벨을 시각화하고 CSS 변수를 복사할 수 있습니다.
 */

import { Check, Copy, Layers } from 'lucide-react'
import { useClipboard } from '../../hooks/useClipboard'
import { shadows } from '../../data/tokens'
import './ShadowScale.css'

// ============================================================================
// Types
// ============================================================================

interface ShadowCardProps {
  name: string
  token: {
    value: string
    description: string
  }
  category: string
}

// ============================================================================
// Sub Components
// ============================================================================

function ShadowCard({ name, token, category }: ShadowCardProps) {
  const { copy, copied } = useClipboard()
  const cssVariable = `--shadow-${category}-${name}`

  const handleCopy = () => {
    copy(cssVariable)
  }

  return (
    <div className="shadow-card" onClick={handleCopy}>
      <div
        className="shadow-preview"
        style={{ boxShadow: token.value }}
      >
        <div className="shadow-preview-inner" />
      </div>
      <div className="shadow-info">
        <div className="shadow-name">{name}</div>
        <div className="shadow-description">{token.description}</div>
        <code className="shadow-variable">
          {copied ? (
            <>
              <Check size={12} /> Copied!
            </>
          ) : (
            <>
              <Copy size={12} /> {cssVariable}
            </>
          )}
        </code>
      </div>
    </div>
  )
}

interface ShadowCategoryProps {
  title: string
  description: string
  tokens: Record<string, { value: string; description: string }>
  category: string
}

function ShadowCategory({ title, description, tokens, category }: ShadowCategoryProps) {
  return (
    <section className="shadow-category">
      <h3 className="shadow-category-title">{title}</h3>
      <p className="shadow-category-description">{description}</p>
      <div className="shadow-grid">
        {Object.entries(tokens).map(([name, token]) => (
          <ShadowCard
            key={name}
            name={name}
            token={token}
            category={category}
          />
        ))}
      </div>
    </section>
  )
}

// ============================================================================
// Main Component
// ============================================================================

export function ShadowScale() {
  return (
    <div className="shadow-scale">
      <header className="shadow-header">
        <div className="shadow-header-icon">
          <Layers size={32} />
        </div>
        <div>
          <h1 className="shadow-title">Shadows</h1>
          <p className="shadow-subtitle">
            요소의 깊이를 표현하기 위한 그림자 시스템입니다.
            클릭하여 CSS 변수를 복사할 수 있습니다.
          </p>
        </div>
      </header>

      <ShadowCategory
        title="Elevation Shadows"
        description="요소의 높이를 표현하는 기본 그림자입니다. 카드, 모달, 드롭다운 등에 사용됩니다."
        tokens={shadows.elevation}
        category="elevation"
      />

      <ShadowCategory
        title="Inner Shadows"
        description="요소 내부의 깊이를 표현하는 내부 그림자입니다. 입력 필드에 사용됩니다."
        tokens={shadows.inner}
        category="inner"
      />

      <ShadowCategory
        title="Colored Shadows"
        description="강조를 위한 컬러 그림자입니다. 버튼 호버 효과 등에 사용됩니다."
        tokens={shadows.colored}
        category="colored"
      />

      <section className="shadow-usage">
        <h3 className="shadow-usage-title">Usage</h3>
        <pre className="shadow-usage-code">
          <code>{`/* CSS에서 사용 */
.card {
  box-shadow: var(--shadow-elevation-md);
}

.card:hover {
  box-shadow: var(--shadow-elevation-lg);
}

.input {
  box-shadow: var(--shadow-inner-base);
}

.button-primary:hover {
  box-shadow: var(--shadow-colored-primary);
}`}</code>
        </pre>
      </section>
    </div>
  )
}
