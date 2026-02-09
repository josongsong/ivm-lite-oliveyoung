/**
 * InfoRow Component
 *
 * SOTA-level key-value display with:
 * - Copyable values
 * - Badge support
 * - Monospace option
 * - Highlight states
 * - Inline editing support
 *
 * @example
 * ```tsx
 * <InfoRow label="Status" value="Active" badge badgeColor="success" />
 * <InfoRow label="ID" value="abc-123" mono copyable />
 * <InfoRow label="Duration" value="1.5s" highlight />
 * ```
 */
import { forwardRef, type HTMLAttributes, type ReactNode, useState } from 'react'
import { Check, Copy, ExternalLink } from 'lucide-react'
import { IconButton } from './IconButton'
import './InfoRow.css'

export interface InfoRowProps extends Omit<HTMLAttributes<HTMLDivElement>, 'children'> {
  /** Label text */
  label: string
  /** Value to display */
  value: ReactNode
  /** Use monospace font for value */
  mono?: boolean
  /** Highlight the value */
  highlight?: boolean
  /** Display value as badge */
  badge?: boolean
  /** Badge color (when badge=true) */
  badgeVariant?: 'default' | 'success' | 'warning' | 'error' | 'info' | 'pending' | 'processing'
  /** Custom badge color */
  badgeColor?: string
  /** Make value copyable */
  copyable?: boolean
  /** Custom copy value (if different from displayed value) */
  copyValue?: string
  /** Show external link icon */
  externalLink?: string
  /** Truncate long values */
  truncate?: boolean
  /** Size variant */
  size?: 'sm' | 'md' | 'lg'
  /** Layout direction */
  direction?: 'horizontal' | 'vertical'
}

export const InfoRow = forwardRef<HTMLDivElement, InfoRowProps>(
  (
    {
      label,
      value,
      mono = false,
      highlight = false,
      badge = false,
      badgeVariant = 'default',
      badgeColor,
      copyable = false,
      copyValue,
      externalLink,
      truncate = false,
      size = 'md',
      direction = 'horizontal',
      className = '',
      ...props
    },
    ref
  ) => {
    const [copied, setCopied] = useState(false)

    const handleCopy = async () => {
      const textToCopy = copyValue ?? (typeof value === 'string' ? value : String(value))
      try {
        await navigator.clipboard.writeText(textToCopy)
        setCopied(true)
        setTimeout(() => setCopied(false), 2000)
      } catch {
        // Fallback for older browsers
        const textarea = document.createElement('textarea')
        textarea.value = textToCopy
        document.body.appendChild(textarea)
        textarea.select()
        document.execCommand('copy')
        document.body.removeChild(textarea)
        setCopied(true)
        setTimeout(() => setCopied(false), 2000)
      }
    }

    const renderValue = () => {
      if (badge) {
        const style = badgeColor ? { backgroundColor: badgeColor } : undefined
        return (
          <span
            className={`ui-info-row__badge ui-info-row__badge--${badgeVariant}`}
            style={style}
          >
            {value}
          </span>
        )
      }

      const valueClasses = [
        'ui-info-row__value',
        mono ? 'ui-info-row__value--mono' : '',
        highlight ? 'ui-info-row__value--highlight' : '',
        truncate ? 'ui-info-row__value--truncate' : '',
      ]
        .filter(Boolean)
        .join(' ')

      return <span className={valueClasses}>{value}</span>
    }

    return (
      <div
        ref={ref}
        className={`ui-info-row ui-info-row--${size} ui-info-row--${direction} ${className}`}
        {...props}
      >
        <span className="ui-info-row__label">{label}</span>
        <div className="ui-info-row__content">
          {renderValue()}
          {(copyable || externalLink) ? <div className="ui-info-row__actions">
              {copyable ? <IconButton
                  icon={copied ? Check : Copy}
                  iconSize={12}
                  variant="ghost"
                  size="sm"
                  onClick={handleCopy}
                  aria-label={copied ? 'Copied' : 'Copy to clipboard'}
                  className={copied ? 'ui-info-row__copy--success' : ''}
                /> : null}
              {externalLink ? <IconButton
                  icon={ExternalLink}
                  iconSize={12}
                  variant="ghost"
                  size="sm"
                  onClick={() => window.open(externalLink, '_blank')}
                  aria-label="Open in new tab"
                /> : null}
            </div> : null}
        </div>
      </div>
    )
  }
)

InfoRow.displayName = 'InfoRow'

// =============================================================================
// InfoList - Container for multiple InfoRows
// =============================================================================

export interface InfoListProps extends HTMLAttributes<HTMLDivElement> {
  /** Size variant applied to all children */
  size?: 'sm' | 'md' | 'lg'
  /** Gap between items */
  gap?: 'sm' | 'md' | 'lg'
  /** Add dividers between items */
  dividers?: boolean
}

export function InfoList({
  children,
  size = 'md',
  gap = 'sm',
  dividers = false,
  className = '',
  ...props
}: InfoListProps) {
  return (
    <div
      className={`ui-info-list ui-info-list--gap-${gap} ${dividers ? 'ui-info-list--dividers' : ''} ${className}`}
      data-size={size}
      {...props}
    >
      {children}
    </div>
  )
}

// =============================================================================
// KeyValue - Simpler inline variant
// =============================================================================

export interface KeyValueProps extends HTMLAttributes<HTMLSpanElement> {
  label: string
  value: ReactNode
  mono?: boolean
}

export function KeyValue({ label, value, mono = false, className = '', ...props }: KeyValueProps) {
  return (
    <span className={`ui-key-value ${className}`} {...props}>
      <span className="ui-key-value__label">{label}:</span>
      <span className={`ui-key-value__value ${mono ? 'ui-key-value__value--mono' : ''}`}>
        {value}
      </span>
    </span>
  )
}
