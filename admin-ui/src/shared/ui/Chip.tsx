/**
 * Chip Component
 *
 * 칩/태그 컴포넌트:
 * - 다양한 variant (색상)
 * - 크기 조절 가능
 * - 선택 가능 (onClick)
 * - 제거 가능 (onRemove)
 * - 아이콘 지원
 *
 * @example
 * ```tsx
 * // 기본 칩
 * <Chip>Tag</Chip>
 *
 * // 선택 가능한 칩
 * <Chip selected onClick={() => {}}>Selected</Chip>
 *
 * // 제거 가능한 칩
 * <Chip onRemove={() => {}}>Removable</Chip>
 *
 * // 아이콘과 함께
 * <Chip icon={<Check />}>With Icon</Chip>
 * ```
 */
import { forwardRef, type ReactNode } from 'react'
import './Chip.css'

type ChipVariant = 'default' | 'primary' | 'success' | 'warning' | 'error' | 'info'
type ChipSize = 'sm' | 'md' | 'lg'

export interface ChipProps {
  /** Chip content */
  children: ReactNode
  /** Visual variant */
  variant?: ChipVariant
  /** Size variant */
  size?: ChipSize
  /** Selected state */
  selected?: boolean
  /** Disable chip */
  disabled?: boolean
  /** Click handler (makes chip interactive) */
  onClick?: () => void
  /** Remove handler (shows remove button) */
  onRemove?: () => void
  /** Icon displayed before content */
  icon?: ReactNode
  /** Additional CSS class */
  className?: string
}

export const Chip = forwardRef<HTMLButtonElement, ChipProps>(
  (
    {
      children,
      variant = 'default',
      size = 'md',
      selected = false,
      disabled = false,
      onClick,
      onRemove,
      icon,
      className = '',
    },
    ref
  ) => {
    const isInteractive = onClick !== undefined

    const chipContent = (
      <>
        {icon ? <span className="chip-icon">{icon}</span> : null}
        <span className="chip-label">{children}</span>
        {onRemove ? (
          <button
            type="button"
            className="chip-remove"
            onClick={(e) => {
              e.stopPropagation()
              onRemove()
            }}
            disabled={disabled}
            aria-label="Remove"
          >
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path
                d="M4 4L10 10M10 4L4 10"
                stroke="currentColor"
                strokeWidth="1.5"
                strokeLinecap="round"
              />
            </svg>
          </button>
        ) : null}
      </>
    )

    const classNames = [
      'chip',
      `chip-${variant}`,
      `chip-${size}`,
      selected && 'selected',
      disabled && 'disabled',
      isInteractive && 'interactive',
      className,
    ]
      .filter(Boolean)
      .join(' ')

    if (isInteractive) {
      return (
        <button
          ref={ref}
          type="button"
          className={classNames}
          onClick={onClick}
          disabled={disabled}
        >
          {chipContent}
        </button>
      )
    }

    return <span className={classNames}>{chipContent}</span>
  }
)

Chip.displayName = 'Chip'

export interface ChipGroupProps {
  /** Chip children */
  children: ReactNode
  /** Additional CSS class */
  className?: string
}

export function ChipGroup({ children, className = '' }: ChipGroupProps) {
  return <div className={`chip-group ${className}`}>{children}</div>
}
