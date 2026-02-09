/**
 * SpacingScale Component
 * Phase 4-C: Spacing Foundation
 *
 * 간격 시스템을 시각화하고 CSS 변수를 복사할 수 있습니다.
 */

import { Check, Copy, Maximize2 } from 'lucide-react'
import { useClipboard } from '../../hooks/useClipboard'
import { spacing } from '../../data/tokens'
import './SpacingScale.css'

// ============================================================================
// Types
// ============================================================================

interface SpacingCardProps {
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

function SpacingCard({ name, token, category }: SpacingCardProps) {
  const { copy, copied } = useClipboard()
  const cssVariable = `--spacing-${category === 'base' ? name : `${category}-${name}`}`
  const pixelValue = parseInt(token.value) || 0

  const handleCopy = () => {
    copy(cssVariable)
  }

  return (
    <div className="spacing-card" onClick={handleCopy}>
      <div className="spacing-preview">
        <div
          className="spacing-bar"
          style={{ width: `${Math.min(pixelValue, 128)}px` }}
        />
      </div>
      <div className="spacing-info">
        <div className="spacing-header">
          <span className="spacing-name">{name}</span>
          <span className="spacing-value">{token.value}</span>
        </div>
        <div className="spacing-description">{token.description}</div>
        <code className="spacing-variable">
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

interface SpacingCategoryProps {
  title: string
  description: string
  tokens: Record<string, { value: string; description: string }>
  category: string
}

function SpacingCategory({ title, description, tokens, category }: SpacingCategoryProps) {
  return (
    <section className="spacing-category">
      <h3 className="spacing-category-title">{title}</h3>
      <p className="spacing-category-description">{description}</p>
      <div className="spacing-grid">
        {Object.entries(tokens).map(([name, token]) => (
          <SpacingCard
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

export function SpacingScale() {
  return (
    <div className="spacing-scale">
      <header className="spacing-header-section">
        <div className="spacing-header-icon">
          <Maximize2 size={32} />
        </div>
        <div>
          <h1 className="spacing-title">Spacing</h1>
          <p className="spacing-subtitle">
            일관된 간격을 위한 4px 기반 스페이싱 시스템입니다.
            클릭하여 CSS 변수를 복사할 수 있습니다.
          </p>
        </div>
      </header>

      <SpacingCategory
        title="Base Scale"
        description="4px 기반의 기본 간격 스케일입니다. 모든 간격은 이 스케일을 기반으로 합니다."
        tokens={spacing.base}
        category="base"
      />

      <SpacingCategory
        title="Semantic Spacing"
        description="용도별로 정의된 시맨틱 간격입니다. 일관된 UI를 위해 이 값들을 사용하세요."
        tokens={spacing.semantic}
        category="semantic"
      />

      <section className="spacing-usage">
        <h3 className="spacing-usage-title">Usage</h3>
        <pre className="spacing-usage-code">
          <code>{`/* Base Scale 사용 */
.element {
  padding: var(--spacing-4);        /* 16px */
  margin-bottom: var(--spacing-8);  /* 32px */
  gap: var(--spacing-2);            /* 8px */
}

/* Semantic Spacing 사용 */
.card {
  padding: var(--spacing-semantic-card);
  gap: var(--spacing-semantic-card-gap);
}

.button {
  padding: var(--spacing-semantic-button-y) var(--spacing-semantic-button-x);
}

.stack {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-semantic-stack-md);
}

.inline {
  display: flex;
  gap: var(--spacing-semantic-inline-sm);
}`}</code>
        </pre>
      </section>
    </div>
  )
}
