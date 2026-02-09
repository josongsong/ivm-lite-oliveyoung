/**
 * Live Preview - 컴포넌트 실시간 미리보기
 * Phase 3-A: Props 변경에 따른 실시간 렌더링
 */

import { type ReactNode, useState } from 'react'
import { Check, Copy, Maximize2, Minimize2, RotateCcw } from 'lucide-react'
import { Button } from '@/shared/ui'
import { useTheme } from '@/shared/hooks/useTheme'
import { useClipboard } from '../../hooks/useClipboard'
import './LivePreview.css'

// ============================================================================
// Types
// ============================================================================

interface LivePreviewProps {
  /** 렌더링할 컴포넌트 */
  children: ReactNode
  /** 배경 옵션 */
  background?: 'light' | 'dark' | 'transparent' | 'checkerboard'
  /** 코드 스니펫 */
  code?: string
  /** 컴포넌트 이름 (표시용) */
  componentName?: string
  /** 에러 메시지 */
  error?: string
  /** 초기화 핸들러 */
  onReset?: () => void
}

// ============================================================================
// Sub Components
// ============================================================================

function PreviewToolbar({
  background,
  onBackgroundChange,
  isExpanded,
  onToggleExpand,
  onReset,
}: {
  background: string
  onBackgroundChange: (bg: 'light' | 'dark' | 'transparent' | 'checkerboard') => void
  isExpanded: boolean
  onToggleExpand: () => void
  onReset?: () => void
}) {
  const backgrounds: Array<{ value: 'light' | 'dark' | 'transparent' | 'checkerboard'; label: string }> = [
    { value: 'light', label: 'Light' },
    { value: 'dark', label: 'Dark' },
    { value: 'transparent', label: 'Grid' },
  ]

  return (
    <div className="live-preview-toolbar">
      <div className="live-preview-toolbar-left">
        {backgrounds.map((bg) => (
          <span
            key={bg.value}
            className={`live-preview-bg-option ${background === bg.value ? 'live-preview-bg-option--active' : ''}`}
            onClick={() => onBackgroundChange(bg.value)}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => e.key === 'Enter' && onBackgroundChange(bg.value)}
            title={bg.label}
          >
            {bg.label}
          </span>
        ))}
      </div>
      <div className="live-preview-toolbar-right">
        {onReset ? (
          <Button
            variant="ghost"
            size="sm"
            icon={<RotateCcw size={14} />}
            onClick={onReset}
            aria-label="Reset"
          />
        ) : null}
        <Button
          variant="ghost"
          size="sm"
          icon={isExpanded ? <Minimize2 size={14} /> : <Maximize2 size={14} />}
          onClick={onToggleExpand}
          aria-label={isExpanded ? 'Minimize' : 'Expand'}
        />
      </div>
    </div>
  )
}

function CodeBlock({ code }: { code: string }) {
  const { copy, copied } = useClipboard()

  return (
    <div className="live-preview-code">
      <div className="live-preview-code-header">
        <span className="live-preview-code-label">Code</span>
        <Button
          variant="ghost"
          size="sm"
          icon={copied ? <Check size={14} /> : <Copy size={14} />}
          onClick={() => copy(code)}
          aria-label="Copy code"
        >
          {copied ? 'Copied!' : 'Copy'}
        </Button>
      </div>
      <pre className="live-preview-code-content">
        <code>{code}</code>
      </pre>
    </div>
  )
}

// ============================================================================
// Main Component
// ============================================================================

export function LivePreview({
  children,
  background: initialBackground,
  code,
  error,
  onReset,
}: LivePreviewProps) {
  const { isDark } = useTheme()
  // 사용자가 설정한 테마를 기본값으로 사용
  const defaultBackground = initialBackground ?? (isDark ? 'dark' : 'light')
  const [background, setBackground] = useState<'light' | 'dark' | 'transparent' | 'checkerboard'>(defaultBackground)
  const [isExpanded, setIsExpanded] = useState(false)

  const previewClasses = [
    'live-preview-canvas',
    `live-preview-canvas--${background}`,
    isExpanded ? 'live-preview-canvas--expanded' : '',
  ].filter(Boolean).join(' ')

  return (
    <div className="live-preview">
      <PreviewToolbar
        background={background}
        onBackgroundChange={setBackground}
        isExpanded={isExpanded}
        onToggleExpand={() => setIsExpanded(!isExpanded)}
        onReset={onReset}
      />

      <div className={previewClasses}>
        {error ? (
          <div className="live-preview-error">
            <span className="live-preview-error-icon">!</span>
            <span className="live-preview-error-message">{error}</span>
          </div>
        ) : (
          <div className="live-preview-content">
            {children}
          </div>
        )}
      </div>

      {code ? <CodeBlock code={code} /> : null}
    </div>
  )
}

// ============================================================================
// Variant Preview Grid
// ============================================================================

interface VariantPreviewProps {
  /** 컴포넌트 이름 */
  title: string
  /** 변형 목록 */
  variants: Array<{
    label: string
    render: () => ReactNode
  }>
}

export function VariantPreview({ title, variants }: VariantPreviewProps) {
  return (
    <div className="variant-preview">
      <h4 className="variant-preview-title">{title}</h4>
      <div className="variant-preview-grid">
        {variants.map((variant) => (
          <div key={variant.label} className="variant-preview-item">
            <div className="variant-preview-component">
              {variant.render()}
            </div>
            <span className="variant-preview-label">{variant.label}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

// ============================================================================
// State Preview Grid
// ============================================================================

interface StatePreviewProps {
  /** 상태 목록 */
  states: Array<{
    label: string
    description?: string
    render: () => ReactNode
  }>
}

export function StatePreview({ states }: StatePreviewProps) {
  return (
    <div className="state-preview">
      {states.map((state) => (
        <div key={state.label} className="state-preview-item">
          <div className="state-preview-header">
            <span className="state-preview-label">{state.label}</span>
            {state.description ? (
              <span className="state-preview-description">{state.description}</span>
            ) : null}
          </div>
          <div className="state-preview-component">
            {state.render()}
          </div>
        </div>
      ))}
    </div>
  )
}
