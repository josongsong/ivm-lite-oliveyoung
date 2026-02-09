/**
 * EmptyState Component
 *
 * SOTA-level empty state display with:
 * - Customizable icon
 * - Title and description
 * - Optional action button
 * - Multiple variants (default, compact, inline)
 * - Animation support
 *
 * @example
 * ```tsx
 * <EmptyState
 *   icon={<Inbox />}
 *   title="No data found"
 *   description="Try adjusting your filters"
 *   action={<Button>Clear Filters</Button>}
 * />
 * ```
 */
import { forwardRef, type HTMLAttributes, type ReactNode } from 'react'
import { motion } from 'framer-motion'
import { Inbox } from 'lucide-react'
import { Button } from './Button'
import './EmptyState.css'

export interface EmptyStateProps extends HTMLAttributes<HTMLDivElement> {
  /** Icon to display */
  icon?: ReactNode
  /** Title text */
  title: string
  /** Description text */
  description?: string
  /** Action button or element */
  action?: ReactNode
  /** Visual variant */
  variant?: 'default' | 'compact' | 'inline' | 'card'
  /** Size variant */
  size?: 'sm' | 'md' | 'lg'
  /** Animate on mount */
  animate?: boolean
}

export const EmptyState = forwardRef<HTMLDivElement, EmptyStateProps>(
  (
    {
      icon,
      title,
      description,
      action,
      variant = 'default',
      size = 'md',
      animate = true,
      className = '',
      ...props
    },
    ref
  ) => {
    const content = (
      <>
        <div className="ui-empty-state__icon">{icon || <Inbox />}</div>
        <div className="ui-empty-state__text">
          <h3 className="ui-empty-state__title">{title}</h3>
          {description ? <p className="ui-empty-state__description">{description}</p> : null}
        </div>
        {action ? <div className="ui-empty-state__action">{action}</div> : null}
      </>
    )

    const classNames = `ui-empty-state ui-empty-state--${variant} ui-empty-state--${size} ${className}`

    if (animate) {
      return (
        <motion.div
          ref={ref}
          className={classNames}
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3 }}
        >
          {content}
        </motion.div>
      )
    }

    return (
      <div ref={ref} className={classNames} {...props}>
        {content}
      </div>
    )
  }
)

EmptyState.displayName = 'EmptyState'

// =============================================================================
// Preset Empty States
// =============================================================================

export interface NoResultsProps extends Omit<EmptyStateProps, 'title'> {
  title?: string
  searchTerm?: string
  onClear?: () => void
}

export function NoResults({
  title = 'No results found',
  searchTerm,
  description,
  onClear,
  ...props
}: NoResultsProps) {
  const desc = description || (searchTerm ? `No matches for "${searchTerm}"` : 'Try adjusting your search or filters')

  return (
    <EmptyState
      title={title}
      description={desc}
      action={
        onClear ? <Button variant="link" onClick={onClear}>
            Clear search
          </Button> : null
      }
      {...props}
    />
  )
}

export interface NoDataProps extends Omit<EmptyStateProps, 'title'> {
  title?: string
  /** Alias for title (convenience prop) */
  message?: string
}

export function NoData({ title, message, ...props }: NoDataProps) {
  return <EmptyState title={message ?? title ?? 'No data available'} {...props} />
}

export interface ErrorStateProps extends Omit<EmptyStateProps, 'title'> {
  title?: string
  error?: Error | string
  onRetry?: () => void
}

export function ErrorState({
  title = 'Something went wrong',
  error,
  description,
  onRetry,
  ...props
}: ErrorStateProps) {
  const errorMessage = error instanceof Error ? error.message : error
  const desc = description || errorMessage || 'An unexpected error occurred'

  return (
    <EmptyState
      title={title}
      description={desc}
      action={
        onRetry ? <Button variant="link" onClick={onRetry}>
            Try again
          </Button> : null
      }
      {...props}
    />
  )
}

export interface LoadingStateProps extends Omit<EmptyStateProps, 'title' | 'icon'> {
  title?: string
}

export function LoadingState({
  title = 'Loading...',
  description,
  ...props
}: LoadingStateProps) {
  return (
    <EmptyState
      icon={<div className="ui-empty-state__spinner" />}
      title={title}
      description={description}
      {...props}
    />
  )
}

export interface ConnectionStateProps extends Omit<EmptyStateProps, 'title'> {
  title?: string
  onRetry?: () => void
}

export function ConnectionState({
  title = 'Connection lost',
  description = 'Please check your connection and try again',
  onRetry,
  ...props
}: ConnectionStateProps) {
  return (
    <EmptyState
      title={title}
      description={description}
      action={
        onRetry ? <Button variant="link" onClick={onRetry}>
            Retry connection
          </Button> : null
      }
      {...props}
    />
  )
}
