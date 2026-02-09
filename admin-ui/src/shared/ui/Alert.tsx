/**
 * Alert Component
 *
 * SOTA-level alert/banner with:
 * - Multiple variants (info, success, warning, error)
 * - Dismissible option
 * - Icon support
 * - Action buttons
 * - Animated entrance/exit
 *
 * @example
 * ```tsx
 * <Alert variant="success" title="Saved!" onDismiss={() => {}}>
 *   Your changes have been saved successfully.
 * </Alert>
 *
 * <Alert variant="warning" icon={<AlertTriangle />}>
 *   Please review before proceeding.
 * </Alert>
 * ```
 */
import { forwardRef, type HTMLAttributes, type ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { AlertTriangle, CheckCircle, Info, X, XCircle } from 'lucide-react'
import { IconButton } from './IconButton'
import './Alert.css'

export interface AlertProps extends Omit<HTMLAttributes<HTMLDivElement>, 'style'> {
  /** Alert variant */
  variant?: 'info' | 'success' | 'warning' | 'error'
  /** Alert title */
  title?: string
  /** Custom icon (defaults based on variant) */
  icon?: ReactNode
  /** Hide default icon */
  hideIcon?: boolean
  /** Make alert dismissible */
  dismissible?: boolean
  /** Callback when dismissed */
  onDismiss?: () => void
  /** Action buttons */
  actions?: ReactNode
  /** Size variant */
  size?: 'sm' | 'md' | 'lg'
  /** Visual style */
  styleVariant?: 'filled' | 'outline' | 'subtle'
  /** Animate on mount */
  animate?: boolean
}

const defaultIcons = {
  info: Info,
  success: CheckCircle,
  warning: AlertTriangle,
  error: XCircle,
}

export const Alert = forwardRef<HTMLDivElement, AlertProps>(
  (
    {
      children,
      variant = 'info',
      title,
      icon,
      hideIcon = false,
      dismissible = false,
      onDismiss,
      actions,
      size = 'md',
      styleVariant = 'subtle',
      animate = true,
      className = '',
      ...props
    },
    ref
  ) => {
    const DefaultIcon = defaultIcons[variant]
    const iconElement = icon || (!hideIcon && <DefaultIcon />)

    const content = (
      <div
        ref={ref}
        role="alert"
        className={`ui-alert ui-alert--${variant} ui-alert--${size} ui-alert--${styleVariant} ${className}`}
        {...props}
      >
        {iconElement ? <span className="ui-alert__icon">{iconElement}</span> : null}
        <div className="ui-alert__content">
          {title ? <strong className="ui-alert__title">{title}</strong> : null}
          {children ? <div className="ui-alert__message">{children}</div> : null}
        </div>
        {actions ? <div className="ui-alert__actions">{actions}</div> : null}
        {dismissible ? <IconButton
            icon={X}
            iconSize={14}
            variant="ghost"
            size="sm"
            onClick={onDismiss}
            aria-label="Dismiss"
            className="ui-alert__dismiss"
          /> : null}
      </div>
    )

    if (animate) {
      return (
        <AnimatePresence>
          <motion.div
            initial={{ opacity: 0, y: -10, height: 0 }}
            animate={{ opacity: 1, y: 0, height: 'auto' }}
            exit={{ opacity: 0, y: -10, height: 0 }}
            transition={{ duration: 0.2 }}
          >
            {content}
          </motion.div>
        </AnimatePresence>
      )
    }

    return content
  }
)

Alert.displayName = 'Alert'

// =============================================================================
// Banner (full-width variant)
// =============================================================================

export interface BannerProps extends AlertProps {
  /** Center content */
  centered?: boolean
}

export const Banner = forwardRef<HTMLDivElement, BannerProps>(
  ({ centered = true, className = '', ...props }, ref) => {
    return (
      <Alert
        ref={ref}
        className={`ui-banner ${centered ? 'ui-banner--centered' : ''} ${className}`}
        {...props}
      />
    )
  }
)

Banner.displayName = 'Banner'

// =============================================================================
// Inline Alert (compact variant)
// =============================================================================

export interface InlineAlertProps extends Omit<AlertProps, 'size' | 'dismissible'> {
  /** Alert variant */
  variant?: 'info' | 'success' | 'warning' | 'error'
}

export function InlineAlert({ className = '', ...props }: InlineAlertProps) {
  return <Alert size="sm" className={`ui-inline-alert ${className}`} {...props} />
}
