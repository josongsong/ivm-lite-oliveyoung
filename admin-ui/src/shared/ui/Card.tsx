/**
 * Card Component System
 *
 * SOTA-level card system with:
 * - Base Card with multiple variants
 * - StatsCard for metrics display
 * - InfoCard for key-value display
 * - Proper accessibility and animations
 *
 * @example
 * ```tsx
 * // Base card
 * <Card>Content</Card>
 *
 * // Stats card
 * <StatsCard
 *   icon={<Database />}
 *   title="Total Records"
 *   value={1234}
 *   subtitle="Last 24h"
 *   trend={{ value: 12, direction: 'up' }}
 * />
 *
 * // Info card with header and actions
 * <Card variant="elevated" header="Settings" actions={<IconButton icon={<Edit />} />}>
 *   Content
 * </Card>
 * ```
 */
import { forwardRef, type HTMLAttributes, type ReactNode } from 'react'
import { motion, type MotionProps } from 'framer-motion'
import { Minus, TrendingDown, TrendingUp } from 'lucide-react'
import './Card.css'

// =============================================================================
// Utility functions
// =============================================================================

function buildCardClassName(
  variant: string,
  noPadding: boolean,
  hoverable: boolean,
  clickable: boolean,
  status: string | undefined,
  className: string
): string {
  return [
    'ui-card',
    `ui-card--${variant}`,
    noPadding ? 'ui-card--no-padding' : '',
    hoverable ? 'ui-card--hoverable' : '',
    clickable ? 'ui-card--clickable' : '',
    status ? `ui-card--status-${status}` : '',
    className,
  ]
    .filter(Boolean)
    .join(' ')
}

// =============================================================================
// Base Card
// =============================================================================

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  /** Visual variant */
  variant?: 'default' | 'elevated' | 'outlined' | 'ghost' | 'glass'
  /** Card header title */
  header?: ReactNode
  /** Header actions (right side) */
  actions?: ReactNode
  /** Remove default padding */
  noPadding?: boolean
  /** Enable hover effect */
  hoverable?: boolean
  /** Make card clickable */
  clickable?: boolean
  /** Status indicator color */
  status?: 'success' | 'warning' | 'error' | 'info' | 'pending' | 'processing'
  /** Animate on mount */
  animate?: boolean
  /** Animation delay in seconds */
  animationDelay?: number
}

export const Card = forwardRef<HTMLDivElement, CardProps>(
  (
    {
      children,
      variant = 'default',
      header,
      actions,
      noPadding = false,
      hoverable = false,
      clickable = false,
      status,
      animate = false,
      animationDelay = 0,
      className = '',
      onClick,
      ...props
    },
    ref
  ) => {
    const cardClassName = buildCardClassName(variant, noPadding, hoverable, clickable, status, className)

    const motionProps: MotionProps = animate
      ? {
          initial: { opacity: 0, y: 20 },
          animate: { opacity: 1, y: 0 },
          transition: { delay: animationDelay, duration: 0.3 },
        }
      : {}

    const content = (
      <>
        {(header || actions) ? <div className="ui-card__header">
            {header ? <div className="ui-card__header-title">{header}</div> : null}
            {actions ? <div className="ui-card__header-actions">{actions}</div> : null}
          </div> : null}
        <div className="ui-card__content">{children}</div>
      </>
    )

    if (animate) {
      return (
        <motion.div
          ref={ref}
          className={cardClassName}
          onClick={onClick}
          role={clickable ? 'button' : undefined}
          tabIndex={clickable ? 0 : undefined}
          {...motionProps}
          {...(props as MotionProps)}
        >
          {content}
        </motion.div>
      )
    }

    return (
      <div
        ref={ref}
        className={cardClassName}
        onClick={onClick}
        role={clickable ? 'button' : undefined}
        tabIndex={clickable ? 0 : undefined}
        {...props}
      >
        {content}
      </div>
    )
  }
)

Card.displayName = 'Card'

// =============================================================================
// Stats Card
// =============================================================================

export interface StatsCardProps extends Omit<CardProps, 'header' | 'children'> {
  /** Icon displayed in the card */
  icon?: ReactNode
  /** Card title */
  title: string
  /** Main value to display */
  value: string | number
  /** Subtitle or additional info */
  subtitle?: string
  /** Trend indicator */
  trend?: {
    value: number
    direction: 'up' | 'down' | 'neutral'
    label?: string
  }
  /** Format value as locale string */
  formatValue?: boolean
  /** Value size */
  valueSize?: 'sm' | 'md' | 'lg' | 'xl'
}

export const StatsCard = forwardRef<HTMLDivElement, StatsCardProps>(
  (
    {
      icon,
      title,
      value,
      subtitle,
      trend,
      formatValue = true,
      valueSize = 'lg',
      variant = 'default',
      animate = true,
      animationDelay = 0,
      className = '',
      ...props
    },
    ref
  ) => {
    const formattedValue =
      formatValue && typeof value === 'number' ? value.toLocaleString() : value

    const TrendIcon =
      trend?.direction === 'up'
        ? TrendingUp
        : trend?.direction === 'down'
          ? TrendingDown
          : Minus

    return (
      <Card
        ref={ref}
        variant={variant}
        animate={animate}
        animationDelay={animationDelay}
        className={`ui-stats-card ${className}`}
        {...props}
      >
        <div className="ui-stats-card__header">
          {icon ? <span className="ui-stats-card__icon">{icon}</span> : null}
          <span className="ui-stats-card__title">{title}</span>
        </div>
        <div className="ui-stats-card__body">
          <span className={`ui-stats-card__value ui-stats-card__value--${valueSize}`}>
            {formattedValue}
          </span>
          {trend ? <span
              className={`ui-stats-card__trend ui-stats-card__trend--${trend.direction}`}
            >
              <TrendIcon size={14} />
              <span>{Math.abs(trend.value)}%</span>
              {trend.label ? <span className="ui-stats-card__trend-label">{trend.label}</span> : null}
            </span> : null}
        </div>
        {subtitle ? <span className="ui-stats-card__subtitle">{subtitle}</span> : null}
      </Card>
    )
  }
)

StatsCard.displayName = 'StatsCard'

// =============================================================================
// Breakdown Item (for use inside StatsCard)
// =============================================================================

export interface BreakdownItemProps {
  label: string
  value: string | number
  highlighted?: boolean
  onClick?: () => void
}

export function BreakdownItem({ label, value, highlighted, onClick }: BreakdownItemProps) {
  const formattedValue = typeof value === 'number' ? value.toLocaleString() : value

  return (
    <div
      className={`ui-breakdown-item ${highlighted ? 'ui-breakdown-item--highlighted' : ''} ${onClick ? 'ui-breakdown-item--clickable' : ''}`}
      onClick={onClick}
      onKeyDown={onClick ? (e) => (e.key === 'Enter' || e.key === ' ') && onClick() : undefined}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
    >
      <span className="ui-breakdown-item__label">{label}</span>
      <span className="ui-breakdown-item__value">{formattedValue}</span>
    </div>
  )
}

// =============================================================================
// Stats Grid (container for multiple StatsCards)
// =============================================================================

export interface StatsGridProps extends HTMLAttributes<HTMLDivElement> {
  /** Number of columns */
  columns?: 2 | 3 | 4 | 5
}

export function StatsGrid({ children, columns = 3, className = '', ...props }: StatsGridProps) {
  return (
    <div className={`ui-stats-grid ui-stats-grid--cols-${columns} ${className}`} {...props}>
      {children}
    </div>
  )
}
