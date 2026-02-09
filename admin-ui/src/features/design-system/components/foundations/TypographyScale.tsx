/**
 * TypographyScale Component
 * Phase 4-B: 타이포그래피 스케일 시각화 및 CSS Variable 복사 기능
 */

import { Check, Copy, Type } from 'lucide-react'
import { useState } from 'react'

import { Button } from '@/shared/ui'
import { useClipboard } from '../../hooks/useClipboard'
import {
  fontFamilies,
  fontWeights,
  typographyCategories,
} from '../../data/tokens/typography'
import type { FontFamily, FontWeight } from '../../data/tokens/typography'
import type { TypographyToken } from '../../data/types'

import './TypographyScale.css'

// ============================================================================
// TypographySample Component
// ============================================================================

interface TypographySampleProps {
  token: TypographyToken
  onCopy: (text: string) => void
  copied: boolean
  lastCopied: string | null
}

function TypographySample({ token, onCopy, copied, lastCopied }: TypographySampleProps) {
  const [hovered, setHovered] = useState(false)
  const isCopied = copied && lastCopied === token.cssVar

  const handleCopy = () => {
    onCopy(token.cssVar)
  }

  const sampleText = getSampleText(token.name)

  return (
    <div
      className="typography-sample"
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      <div className="typography-sample-preview">
        <p
          className="typography-sample-text"
          style={{
            fontSize: token.fontSize,
            lineHeight: token.lineHeight,
            fontWeight: token.fontWeight,
            letterSpacing: token.letterSpacing,
            fontFamily: token.name.includes('code')
              ? "var(--font-mono, 'SF Mono', monospace)"
              : 'inherit',
          }}
        >
          {sampleText}
        </p>
      </div>

      <div className="typography-sample-info">
        <div className="typography-sample-header">
          <span className="typography-sample-name">{token.name}</span>
          <div className={`typography-sample-copy ${hovered ? 'visible' : ''}`}>
            <Button
              icon={isCopied ? <Check size={14} /> : <Copy size={14} />}
              onClick={handleCopy}
              aria-label={`Copy ${token.cssVar}`}
              size="sm"
              variant="ghost"
            />
            <span className="copy-text">{isCopied ? 'Copied!' : 'Copy'}</span>
          </div>
        </div>

        <div className="typography-sample-specs">
          <span className="typography-spec">
            <span className="spec-label">Size:</span>
            <span className="spec-value">{token.fontSize}</span>
          </span>
          <span className="typography-spec">
            <span className="spec-label">Line:</span>
            <span className="spec-value">{token.lineHeight}</span>
          </span>
          <span className="typography-spec">
            <span className="spec-label">Weight:</span>
            <span className="spec-value">{token.fontWeight}</span>
          </span>
          {token.letterSpacing != null && token.letterSpacing !== '' && (
            <span className="typography-spec">
              <span className="spec-label">Tracking:</span>
              <span className="spec-value">{token.letterSpacing}</span>
            </span>
          )}
        </div>

        {token.description != null && token.description !== '' && (
          <p className="typography-sample-description">{token.description}</p>
        )}
      </div>
    </div>
  )
}

// ============================================================================
// TypographyCategory Component
// ============================================================================

interface TypographyCategoryProps {
  category: {
    id: string
    name: string
    tokens: readonly TypographyToken[]
  }
  onCopy: (text: string) => void
  copied: boolean
  lastCopied: string | null
}

function TypographyCategory({ category, onCopy, copied, lastCopied }: TypographyCategoryProps) {
  return (
    <div className="typography-category">
      <h3 className="typography-category-title">{category.name}</h3>
      <div className="typography-category-list">
        {category.tokens.map((token) => (
          <TypographySample
            key={token.name}
            token={token}
            onCopy={onCopy}
            copied={copied}
            lastCopied={lastCopied}
          />
        ))}
      </div>
    </div>
  )
}

// ============================================================================
// FontFamilyCard Component
// ============================================================================

interface FontFamilyCardProps {
  family: FontFamily
  onCopy: (text: string) => void
  copied: boolean
  lastCopied: string | null
}

function FontFamilyCard({ family, onCopy, copied, lastCopied }: FontFamilyCardProps) {
  const isCopied = copied && lastCopied === family.cssVar

  return (
    <div className="font-family-card">
      <div className="font-family-header">
        <span className="font-family-name">{family.name}</span>
        <Button
          icon={isCopied ? <Check size={14} /> : <Copy size={14} />}
          onClick={() => onCopy(family.cssVar)}
          aria-label={`Copy ${family.cssVar}`}
          size="sm"
          variant="ghost"
        />
      </div>
      <p
        className="font-family-preview"
        style={{ fontFamily: family.value }}
      >
        The quick brown fox jumps over the lazy dog
      </p>
      <p className="font-family-description">{family.description}</p>
      <code className="font-family-value">{family.value}</code>
    </div>
  )
}

// ============================================================================
// FontWeightCard Component
// ============================================================================

interface FontWeightCardProps {
  weight: FontWeight
  onCopy: (text: string) => void
  copied: boolean
  lastCopied: string | null
}

function FontWeightCard({ weight, onCopy, copied, lastCopied }: FontWeightCardProps) {
  const isCopied = copied && lastCopied === weight.cssVar

  return (
    <div className="font-weight-card">
      <div className="font-weight-header">
        <span className="font-weight-name">{weight.name}</span>
        <Button
          icon={isCopied ? <Check size={14} /> : <Copy size={14} />}
          onClick={() => onCopy(weight.cssVar)}
          aria-label={`Copy ${weight.cssVar}`}
          size="sm"
          variant="ghost"
        />
      </div>
      <p
        className="font-weight-preview"
        style={{ fontWeight: weight.value }}
      >
        Aa
      </p>
      <div className="font-weight-info">
        <span className="font-weight-value">{weight.value}</span>
        <span className="font-weight-description">{weight.description}</span>
      </div>
    </div>
  )
}

// ============================================================================
// TypographyScale Component
// ============================================================================

export function TypographyScale() {
  const { copy, copied, lastCopied } = useClipboard({
    successDuration: 1500,
  })

  return (
    <div className="typography-scale">
      <div className="typography-scale-header">
        <p className="typography-scale-description">
          타이포그래피 요소를 클릭하면 CSS Variable이 클립보드에 복사됩니다.
          예: <code>var(--font-heading-lg)</code>
        </p>
      </div>

      {/* Font Families */}
      <section className="typography-section">
        <h3 className="typography-section-title">
          <Type size={18} />
          Font Families
        </h3>
        <div className="font-families-grid">
          {fontFamilies.map((family) => (
            <FontFamilyCard
              key={family.name}
              family={family}
              onCopy={copy}
              copied={copied}
              lastCopied={lastCopied}
            />
          ))}
        </div>
      </section>

      {/* Font Weights */}
      <section className="typography-section">
        <h3 className="typography-section-title">Font Weights</h3>
        <div className="font-weights-grid">
          {fontWeights.map((weight) => (
            <FontWeightCard
              key={weight.name}
              weight={weight}
              onCopy={copy}
              copied={copied}
              lastCopied={lastCopied}
            />
          ))}
        </div>
      </section>

      {/* Typography Scale */}
      <div className="typography-categories">
        {typographyCategories.map((category) => (
          <TypographyCategory
            key={category.id}
            category={category}
            onCopy={copy}
            copied={copied}
            lastCopied={lastCopied}
          />
        ))}
      </div>
    </div>
  )
}

// ============================================================================
// Utility Functions
// ============================================================================

function getSampleText(tokenName: string): string {
  if (tokenName.includes('display')) {
    return 'Display Text'
  }
  if (tokenName.includes('heading')) {
    return 'Heading Text Example'
  }
  if (tokenName.includes('body')) {
    return 'The quick brown fox jumps over the lazy dog. This is sample body text to demonstrate the typography scale.'
  }
  if (tokenName.includes('label')) {
    return 'Label Text'
  }
  if (tokenName.includes('code')) {
    return 'const example = "code snippet";'
  }
  return 'Sample Text'
}
