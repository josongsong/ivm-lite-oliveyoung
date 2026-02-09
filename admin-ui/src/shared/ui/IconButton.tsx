/**
 * IconButton Component
 *
 * Specialized button for icon-only actions (common in tables, toolbars).
 * Replaces the scattered .btn-icon pattern from Outbox.css
 *
 * Features:
 * - Lucide icon type support
 * - Loading state with custom loading icon
 * - Tooltip support (native title)
 * - Variant-based styling
 * - Proper accessibility (aria-label 또는 tooltip 필수)
 *
 * **접근성 주의**: 아이콘만 있는 버튼은 스크린리더를 위해 `aria-label` 또는 `tooltip`이 필요합니다.
 * `tooltip`이 제공되면 자동으로 `aria-label`로 사용됩니다.
 *
 * @example
 * ```tsx
 * // aria-label 사용 (권장)
 * <IconButton
 *   icon={RotateCcw}
 *   onClick={handleRetry}
 *   aria-label="Retry"
 * />
 *
 * // tooltip 사용 (aria-label로 자동 변환)
 * <IconButton
 *   icon={Trash2}
 *   onClick={handleDelete}
 *   tooltip="Delete"
 * />
 *
 * // 둘 다 제공 가능 (aria-label 우선)
 * <IconButton
 *   icon={Settings}
 *   onClick={handleSettings}
 *   aria-label="Settings"
 *   tooltip="Open settings"
 * />
 *
 * // Loading state
 * <IconButton
 *   icon={RotateCcw}
 *   loadingIcon={Loader2}
 *   loading={isRetrying}
 *   onClick={handleRetry}
 *   aria-label="Retry"
 *   variant="success"
 * />
 * ```
 */
import { type ButtonHTMLAttributes, forwardRef } from 'react'
import type { LucideIcon } from 'lucide-react'
import './IconButton.css'

export interface IconButtonProps
  extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> {
  /** Lucide icon component */
  icon: LucideIcon
  /** Icon to show during loading (defaults to spinning version of icon) */
  loadingIcon?: LucideIcon
  /** Shows loading state */
  loading?: boolean
  /** Visual variant */
  variant?: 'default' | 'primary' | 'danger' | 'success' | 'warning' | 'ghost'
  /** Size variant */
  size?: 'sm' | 'md' | 'lg'
  /** Tooltip text (uses native title) */
  tooltip?: string
  /** Icon size in pixels (default: 16 for sm, 18 for md, 20 for lg) */
  iconSize?: number
  /** 아이콘 stroke 두께 (기본: 1.6) */
  strokeWidth?: number
}

// Default icon sizes per button size
const defaultIconSizes: Record<string, number> = {
  sm: 14,
  md: 16,
  lg: 18,
}

/**
 * IconButton component with forwardRef support
 */
export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(
  (
    {
      icon: Icon,
      loadingIcon: LoadingIcon,
      loading = false,
      variant = 'default',
      size = 'md',
      tooltip,
      iconSize,
      strokeWidth = 1.6,
      className = '',
      disabled,
      type = 'button',
      'aria-label': ariaLabel,
      ...props
    },
    ref
  ) => {
    // Resolve icon size
    const resolvedIconSize = iconSize ?? defaultIconSizes[size]

    // Build class names
    const classNames = [
      'ui-icon-button',
      `ui-icon-button--${variant}`,
      `ui-icon-button--${size}`,
      loading ? 'ui-icon-button--loading' : '',
      className,
    ]
      .filter(Boolean)
      .join(' ')

    // Choose which icon to render
    const IconComponent = loading && LoadingIcon ? LoadingIcon : Icon
    const isSpinning = loading

    return (
      <button
        ref={ref}
        type={type}
        className={classNames}
        disabled={disabled || loading}
        aria-label={ariaLabel || tooltip}
        aria-busy={loading}
        aria-disabled={disabled || loading}
        title={tooltip}
        {...props}
      >
        <IconComponent
          size={resolvedIconSize}
          strokeWidth={strokeWidth}
          absoluteStrokeWidth
          className={[
            'ui-icon',
            'ui-icon--muted',
            isSpinning ? 'ui-icon-button__icon--spinning' : '',
          ]
            .filter(Boolean)
            .join(' ')}
          aria-hidden="true"
        />
      </button>
    )
  }
)

IconButton.displayName = 'IconButton'
