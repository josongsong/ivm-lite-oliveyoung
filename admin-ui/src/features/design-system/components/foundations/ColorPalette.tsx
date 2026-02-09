/**
 * ColorPalette Component
 * Phase 4-A: 컬러 팔레트 시각화 및 CSS Variable 복사 기능
 */

import { Check, Copy } from 'lucide-react'
import { useState } from 'react'

import { useClipboard } from '../../hooks/useClipboard'
import { colorCategories } from '../../data/tokens/colors'
import type { ColorToken } from '../../data/types'

import './ColorPalette.css'

// ============================================================================
// ColorSwatch Component
// ============================================================================

interface ColorSwatchProps {
  token: ColorToken
  onCopy: (text: string) => void
  copied: boolean
  lastCopied: string | null
}

function ColorSwatch({ token, onCopy, copied, lastCopied }: ColorSwatchProps) {
  const [hovered, setHovered] = useState(false)
  const isCopied = copied && lastCopied === token.cssVar

  const handleClick = () => {
    onCopy(token.cssVar)
  }

  // 색상 밝기 계산하여 텍스트 색상 결정
  const isLightColor = isLight(token.value)

  return (
    <div
      className="color-swatch"
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onClick={handleClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => e.key === 'Enter' && handleClick()}
      aria-label={`Copy ${token.cssVar}`}
    >
      <div
        className="color-swatch-preview"
        style={{ backgroundColor: token.value }}
      >
        <div
          className={`color-swatch-overlay ${hovered ? 'visible' : ''}`}
          style={{ color: isLightColor ? '#18181b' : '#ffffff' }}
        >
          {isCopied ? (
            <Check size={16} />
          ) : (
            <Copy size={16} />
          )}
          <span className="color-swatch-copy-text">
            {isCopied ? 'Copied!' : 'Copy'}
          </span>
        </div>
      </div>
      <div className="color-swatch-info">
        <span className="color-swatch-name">{token.name}</span>
        <span className="color-swatch-value">{token.value}</span>
      </div>
    </div>
  )
}

// ============================================================================
// ColorCategory Component
// ============================================================================

interface ColorCategoryProps {
  category: {
    id: string
    name: string
    tokens: readonly ColorToken[]
  }
  onCopy: (text: string) => void
  copied: boolean
  lastCopied: string | null
}

function ColorCategory({ category, onCopy, copied, lastCopied }: ColorCategoryProps) {
  return (
    <div className="color-category">
      <h3 className="color-category-title">{category.name}</h3>
      <div className="color-category-grid">
        {category.tokens.map((token) => (
          <ColorSwatch
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
// ColorPalette Component
// ============================================================================

export function ColorPalette() {
  const { copy, copied, lastCopied } = useClipboard({
    successDuration: 1500,
  })

  return (
    <div className="color-palette">
      <div className="color-palette-header">
        <p className="color-palette-description">
          색상을 클릭하면 CSS Variable이 클립보드에 복사됩니다.
          예: <code>var(--color-primary-500)</code>
        </p>
      </div>

      <div className="color-palette-categories">
        {colorCategories.map((category) => (
          <ColorCategory
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

/**
 * 색상이 밝은지 판단 (텍스트 색상 결정용)
 */
function isLight(hex: string): boolean {
  const rgb = hexToRgb(hex)
  if (!rgb) return false

  // 상대 휘도 계산 (WCAG 공식)
  const luminance = (0.299 * rgb.r + 0.587 * rgb.g + 0.114 * rgb.b) / 255
  return luminance > 0.5
}

function hexToRgb(hex: string): { r: number; g: number; b: number } | null {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
  return result
    ? {
        r: parseInt(result[1], 16),
        g: parseInt(result[2], 16),
        b: parseInt(result[3], 16),
      }
    : null
}
