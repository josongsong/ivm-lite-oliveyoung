/**
 * MotionPreview Component
 * Phase 4-E: Motion Foundation
 *
 * 애니메이션 프리셋을 시각화하고 CSS 변수를 복사할 수 있습니다.
 */

import { useCallback, useState } from 'react'
import { Check, Copy, Play, RefreshCw, Zap } from 'lucide-react'
import { Button } from '@/shared/ui'
import { useClipboard } from '../../hooks/useClipboard'
import { motion } from '../../data/tokens'
import './MotionPreview.css'

// ============================================================================
// Types
// ============================================================================

interface DurationCardProps {
  name: string
  token: {
    value: string
    description: string
  }
}

interface EasingCardProps {
  name: string
  token: {
    value: string
    description: string
  }
}

interface PresetCardProps {
  name: string
  token: {
    keyframes: string
    properties: { animation: string }
    description: string
  }
}

// ============================================================================
// Sub Components
// ============================================================================

function DurationCard({ name, token }: DurationCardProps) {
  const { copy, copied } = useClipboard()
  const cssVariable = `--motion-duration-${name}`
  const [isAnimating, setIsAnimating] = useState(false)

  const handleCopy = (e: React.MouseEvent) => {
    e.stopPropagation()
    copy(cssVariable)
  }

  const handlePlay = () => {
    setIsAnimating(true)
    const duration = parseInt(token.value) || 200
    setTimeout(() => setIsAnimating(false), duration + 100)
  }

  return (
    <div className="motion-card duration-card">
      <div className="motion-card-header">
        <span className="motion-card-name">{name}</span>
        <span className="motion-card-value">{token.value}</span>
      </div>
      <div className="motion-card-description">{token.description}</div>
      <div className="motion-preview-area">
        <div
          className={`duration-preview-bar ${isAnimating ? 'animating' : ''}`}
          style={{ transitionDuration: token.value }}
        />
      </div>
      <div className="motion-card-actions">
        <Button
          icon={<Play size={14} />}
          onClick={handlePlay}
          title="재생"
          size="sm"
          variant="ghost"
          className="motion-action-btn"
        />
        <Button
          icon={copied ? <Check size={14} /> : <Copy size={14} />}
          onClick={handleCopy}
          title="복사"
          size="sm"
          variant="ghost"
          className="motion-action-btn"
        />
      </div>
    </div>
  )
}

function EasingCard({ name, token }: EasingCardProps) {
  const { copy, copied } = useClipboard()
  const cssVariable = `--motion-easing-${name}`
  const [isAnimating, setIsAnimating] = useState(false)

  const handleCopy = (e: React.MouseEvent) => {
    e.stopPropagation()
    copy(cssVariable)
  }

  const handlePlay = () => {
    setIsAnimating(false)
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        setIsAnimating(true)
        setTimeout(() => setIsAnimating(false), 1000)
      })
    })
  }

  return (
    <div className="motion-card easing-card">
      <div className="motion-card-header">
        <span className="motion-card-name">{name}</span>
      </div>
      <code className="motion-card-value-code">{token.value}</code>
      <div className="motion-card-description">{token.description}</div>
      <div className="motion-preview-area easing-preview">
        <div
          className={`easing-preview-dot ${isAnimating ? 'animating' : ''}`}
          style={{ transitionTimingFunction: token.value }}
        />
        <div className="easing-curve-bg" />
      </div>
      <div className="motion-card-actions">
        <Button
          icon={<Play size={14} />}
          onClick={handlePlay}
          title="재생"
          size="sm"
          variant="ghost"
          className="motion-action-btn"
        />
        <Button
          icon={copied ? <Check size={14} /> : <Copy size={14} />}
          onClick={handleCopy}
          title="복사"
          size="sm"
          variant="ghost"
          className="motion-action-btn"
        />
      </div>
    </div>
  )
}

function PresetCard({ name, token }: PresetCardProps) {
  const { copy, copied } = useClipboard()
  const [isAnimating, setIsAnimating] = useState(false)
  const [animationKey, setAnimationKey] = useState(0)

  const handleCopy = (e: React.MouseEvent) => {
    e.stopPropagation()
    copy(token.properties.animation)
  }

  const handlePlay = useCallback(() => {
    setIsAnimating(false)
    setAnimationKey(k => k + 1)
    requestAnimationFrame(() => {
      setIsAnimating(true)
      // Extract duration from animation string
      const durationMatch = token.properties.animation.match(/(\d+)m?s/)
      const duration = durationMatch ? parseInt(durationMatch[1]) : 500
      const multiplier = token.properties.animation.includes('ms') ? 1 : 1000
      const isInfinite = token.properties.animation.includes('infinite')

      if (!isInfinite) {
        setTimeout(() => setIsAnimating(false), duration * multiplier + 100)
      }
    })
  }, [token.properties.animation])

  // Inject keyframes dynamically
  const keyframeName = `preset-${name}-${animationKey}`
  const keyframesWithName = token.keyframes.replace(/@keyframes \w+/, `@keyframes ${keyframeName}`)
  const animationWithName = token.properties.animation.replace(/^\w+/, keyframeName)

  return (
    <div className="motion-card preset-card">
      <style>{keyframesWithName}</style>
      <div className="motion-card-header">
        <span className="motion-card-name">{name}</span>
      </div>
      <div className="motion-card-description">{token.description}</div>
      <div className="motion-preview-area preset-preview">
        <div
          key={animationKey}
          className="preset-preview-box"
          style={{
            animation: isAnimating ? animationWithName : 'none',
          }}
        />
      </div>
      <code className="motion-card-animation">{token.properties.animation}</code>
      <div className="motion-card-actions">
        <Button
          icon={
            isAnimating && token.properties.animation.includes('infinite') ? (
              <RefreshCw size={14} />
            ) : (
              <Play size={14} />
            )
          }
          onClick={handlePlay}
          title={isAnimating && token.properties.animation.includes('infinite') ? '중지' : '재생'}
          size="sm"
          variant="ghost"
          className="motion-action-btn"
        />
        <Button
          icon={copied ? <Check size={14} /> : <Copy size={14} />}
          onClick={handleCopy}
          title="복사"
          size="sm"
          variant="ghost"
          className="motion-action-btn"
        />
      </div>
    </div>
  )
}

// ============================================================================
// Main Component
// ============================================================================

export function MotionPreview() {
  return (
    <div className="motion-scale">
      <header className="motion-header">
        <div className="motion-header-icon">
          <Zap size={32} />
        </div>
        <div>
          <h1 className="motion-title">Motion</h1>
          <p className="motion-subtitle">
            일관된 애니메이션을 위한 모션 시스템입니다.
            각 카드를 클릭하여 프리뷰하고 CSS를 복사할 수 있습니다.
          </p>
        </div>
      </header>

      {/* Duration */}
      <section className="motion-category">
        <h3 className="motion-category-title">Duration</h3>
        <p className="motion-category-description">
          애니메이션 지속 시간입니다. 인터랙션의 성격에 따라 적절한 duration을 선택하세요.
        </p>
        <div className="motion-grid">
          {Object.entries(motion.duration).map(([name, token]) => (
            <DurationCard key={name} name={name} token={token} />
          ))}
        </div>
      </section>

      {/* Easing */}
      <section className="motion-category">
        <h3 className="motion-category-title">Easing</h3>
        <p className="motion-category-description">
          애니메이션 가속도 곡선입니다. 자연스러운 움직임을 위해 적절한 easing을 선택하세요.
        </p>
        <div className="motion-grid easing-grid">
          {Object.entries(motion.easing).map(([name, token]) => (
            <EasingCard key={name} name={name} token={token} />
          ))}
        </div>
      </section>

      {/* Presets */}
      <section className="motion-category">
        <h3 className="motion-category-title">Animation Presets</h3>
        <p className="motion-category-description">
          자주 사용되는 애니메이션 프리셋입니다. 재생 버튼을 눌러 프리뷰하세요.
        </p>
        <div className="motion-grid preset-grid">
          {Object.entries(motion.presets).map(([name, token]) => (
            <PresetCard key={name} name={name} token={token} />
          ))}
        </div>
      </section>

      {/* Usage */}
      <section className="motion-usage">
        <h3 className="motion-usage-title">Usage</h3>
        <pre className="motion-usage-code">
          <code>{`/* Duration 사용 */
.button {
  transition: all var(--motion-duration-normal);
}

/* Easing 사용 */
.modal {
  transition: transform 200ms var(--motion-easing-ease-out);
}

/* Preset 적용 */
.dropdown {
  animation: fadeIn 200ms ease-out;
}

.toast {
  animation: slideUp 200ms ease-out;
}

/* 조합 사용 */
.card:hover {
  transform: translateY(-4px);
  transition: transform var(--motion-duration-fast) var(--motion-easing-ease-out);
}`}</code>
        </pre>
      </section>
    </div>
  )
}
