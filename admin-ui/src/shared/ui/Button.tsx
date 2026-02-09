/**
 * Button Component
 *
 * SOTA-level button with:
 * - forwardRef support for composition
 * - Multiple variants and sizes
 * - Loading state with spinner
 * - Icon support (left/right positioning)
 * - Full width option
 * - Proper accessibility
 *
 * @example
 * ```tsx
 * // Primary button with icon
 * <Button variant="primary" icon={<Plus />}>
 *   Add Item
 * </Button>
 *
 * // Loading state
 * <Button loading>Saving...</Button>
 *
 * // Icon only (use IconButton for better semantics)
 * <Button variant="ghost" size="sm" icon={<X />} aria-label="Close" />
 * ```
 */
import { type ButtonHTMLAttributes, cloneElement, forwardRef, isValidElement, type ReactNode } from 'react'
import './Button.css'

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** Visual variant */
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger' | 'success' | 'link'
  /** Size variant */
  size?: 'sm' | 'md' | 'lg'
  /** Shows loading spinner and disables button */
  loading?: boolean
  /** Icon element (ReactNode) */
  icon?: ReactNode
  /** Icon position relative to children */
  iconPosition?: 'left' | 'right'
  /** Makes button full width */
  fullWidth?: boolean
}

function normalizeIcon(icon: ReactNode, options: { muted: boolean }): ReactNode {
  if (!isValidElement(icon)) return icon

  const props = icon.props as {
    className?: string
    strokeWidth?: number
    absoluteStrokeWidth?: boolean
  }

  const nextProps: {
    className?: string
    strokeWidth?: number
    absoluteStrokeWidth?: boolean
  } = {}

  // 아이콘 기본 두께를 통일해서 “정돈된” 인상을 만듭니다.
  // 명시적으로 strokeWidth가 들어온 경우는 존중합니다.
  if (props.strokeWidth == null) nextProps.strokeWidth = 1.6
  if (props.absoluteStrokeWidth == null) nextProps.absoluteStrokeWidth = true

  const toneClass = options.muted ? 'ui-icon--muted' : ''
  nextProps.className = [props.className, 'ui-icon', toneClass].filter(Boolean).join(' ')

  return cloneElement(icon, nextProps)
}

function ButtonIcon({ icon, isIconOnly }: { icon: ReactNode; isIconOnly: boolean }) {
  return (
    <span className={`ui-button__icon ${isIconOnly ? 'ui-button__icon--only' : ''}`}>
      {icon}
    </span>
  )
}

function ButtonContent({
  loading,
  icon,
  iconPosition,
  children,
}: Pick<ButtonProps, 'loading' | 'icon' | 'iconPosition' | 'children'>) {
  const isIconOnly = Boolean(icon && !children)

  if (loading) {
    return (
      <>
        <span className="ui-button__spinner" aria-hidden="true" />
        {children ? <span className="ui-button__text">{children}</span> : null}
      </>
    )
  }

  return (
    <>
      {icon && iconPosition === 'left' ? <ButtonIcon icon={icon} isIconOnly={isIconOnly} /> : null}
      {children ? <span className="ui-button__text">{children}</span> : null}
      {icon && iconPosition === 'right' ? <ButtonIcon icon={icon} isIconOnly={isIconOnly} /> : null}
    </>
  )
}

/**
 * Button component with forwardRef for ref forwarding
 */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      children,
      variant = 'secondary',
      size = 'md',
      loading = false,
      icon,
      iconPosition = 'left',
      fullWidth = false,
      className = '',
      disabled,
      type = 'button',
      ...props
    },
    ref
  ) => {
    const isSolidVariant = variant === 'primary' || variant === 'danger' || variant === 'success'
    const normalizedIcon = icon ? normalizeIcon(icon, { muted: !isSolidVariant }) : undefined

    const classNames = [
      'ui-button',
      `ui-button--${variant}`,
      `ui-button--${size}`,
      fullWidth ? 'ui-button--full-width' : '',
      loading ? 'ui-button--loading' : '',
      className,
    ]
      .filter(Boolean)
      .join(' ')

    return (
      <button
        ref={ref}
        type={type}
        className={classNames}
        disabled={disabled || loading}
        aria-busy={loading}
        aria-disabled={disabled || loading}
        {...props}
      >
        <ButtonContent loading={loading} icon={normalizedIcon} iconPosition={iconPosition}>
          {children}
        </ButtonContent>
      </button>
    )
  }
)

Button.displayName = 'Button'
