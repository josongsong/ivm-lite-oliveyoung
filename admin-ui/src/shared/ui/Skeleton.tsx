/**
 * Skeleton Component
 *
 * SOTA-level loading placeholder with:
 * - Multiple shape variants
 * - Animated shimmer effect
 * - Composable skeleton layouts
 * - Preset patterns (text, avatar, card)
 *
 * @example
 * ```tsx
 * <Skeleton width={200} height={20} />
 * <Skeleton variant="circle" size={40} />
 * <Skeleton variant="text" lines={3} />
 *
 * <SkeletonCard />
 * <SkeletonTable rows={5} />
 * ```
 */
import { forwardRef, type HTMLAttributes } from 'react'
import './Skeleton.css'

export interface SkeletonProps extends HTMLAttributes<HTMLDivElement> {
  /** Shape variant */
  variant?: 'rect' | 'circle' | 'text'
  /** Width (number = px, string = any CSS unit) */
  width?: number | string
  /** Height (number = px, string = any CSS unit) */
  height?: number | string
  /** Size for circle variant (number = px) */
  size?: number
  /** Border radius override */
  radius?: number | string
  /** Disable animation */
  animate?: boolean
  /** Number of lines for text variant */
  lines?: number
}

export const Skeleton = forwardRef<HTMLDivElement, SkeletonProps>(
  (
    {
      variant = 'rect',
      width,
      height,
      size,
      radius,
      animate = true,
      lines = 1,
      className = '',
      style,
      ...props
    },
    ref
  ) => {
    // Text variant renders multiple lines
    if (variant === 'text' && lines > 1) {
      return (
        <div ref={ref} className={`ui-skeleton-text ${className}`} {...props}>
          {Array.from({ length: lines }).map((_, i) => (
            <div
              key={i}
              className={`ui-skeleton ui-skeleton--text ${animate ? 'ui-skeleton--animated' : ''}`}
              style={{
                width: i === lines - 1 ? '75%' : '100%', // Last line shorter
                ...style,
              }}
            />
          ))}
        </div>
      )
    }

    const computedWidth = size ?? width
    const computedHeight = size ?? height
    const computedRadius = variant === 'circle' ? '50%' : radius

    return (
      <div
        ref={ref}
        className={`ui-skeleton ui-skeleton--${variant} ${animate ? 'ui-skeleton--animated' : ''} ${className}`}
        style={{
          width: typeof computedWidth === 'number' ? `${computedWidth}px` : computedWidth,
          height: typeof computedHeight === 'number' ? `${computedHeight}px` : computedHeight,
          borderRadius: typeof computedRadius === 'number' ? `${computedRadius}px` : computedRadius,
          ...style,
        }}
        {...props}
      />
    )
  }
)

Skeleton.displayName = 'Skeleton'

// =============================================================================
// Skeleton Presets
// =============================================================================

export interface SkeletonAvatarProps extends Omit<SkeletonProps, 'variant' | 'size'> {
  size?: 'sm' | 'md' | 'lg' | number
}

export function SkeletonAvatar({ size = 'md', ...props }: SkeletonAvatarProps) {
  const sizeMap = { sm: 32, md: 40, lg: 56 }
  const pixelSize = typeof size === 'number' ? size : sizeMap[size]
  return <Skeleton variant="circle" size={pixelSize} {...props} />
}

export interface SkeletonTextProps extends Omit<SkeletonProps, 'variant'> {
  lines?: number
}

export function SkeletonText({ lines = 3, ...props }: SkeletonTextProps) {
  return <Skeleton variant="text" lines={lines} height={14} {...props} />
}

export interface SkeletonButtonProps extends Omit<SkeletonProps, 'variant' | 'size'> {
  size?: 'sm' | 'md' | 'lg'
}

export function SkeletonButton({ size = 'md', ...props }: SkeletonButtonProps) {
  const sizeMap = { sm: { w: 60, h: 28 }, md: { w: 80, h: 36 }, lg: { w: 100, h: 44 } }
  const dims = sizeMap[size]
  return <Skeleton width={dims.w} height={dims.h} radius={6} {...props} />
}

export interface SkeletonCardProps extends HTMLAttributes<HTMLDivElement> {}

export function SkeletonCard({ className = '', ...props }: SkeletonCardProps) {
  return (
    <div className={`ui-skeleton-card ${className}`} {...props}>
      <Skeleton height={140} radius={8} />
      <div className="ui-skeleton-card__body">
        <Skeleton height={20} width="60%" />
        <SkeletonText lines={2} />
      </div>
    </div>
  )
}

export interface SkeletonTableProps extends HTMLAttributes<HTMLDivElement> {
  rows?: number
  columns?: number
}

export function SkeletonTable({
  rows = 5,
  columns = 4,
  className = '',
  ...props
}: SkeletonTableProps) {
  return (
    <div className={`ui-skeleton-table ${className}`} {...props}>
      {/* Header */}
      <div className="ui-skeleton-table__row ui-skeleton-table__header">
        {Array.from({ length: columns }).map((_, i) => (
          <Skeleton key={i} height={16} />
        ))}
      </div>
      {/* Body rows */}
      {Array.from({ length: rows }).map((_, rowIndex) => (
        <div key={rowIndex} className="ui-skeleton-table__row">
          {Array.from({ length: columns }).map((_, colIndex) => (
            <Skeleton key={colIndex} height={14} />
          ))}
        </div>
      ))}
    </div>
  )
}

export interface SkeletonListProps extends HTMLAttributes<HTMLDivElement> {
  items?: number
  avatar?: boolean
}

export function SkeletonList({
  items = 3,
  avatar = true,
  className = '',
  ...props
}: SkeletonListProps) {
  return (
    <div className={`ui-skeleton-list ${className}`} {...props}>
      {Array.from({ length: items }).map((_, i) => (
        <div key={i} className="ui-skeleton-list__item">
          {avatar ? <SkeletonAvatar size="md" /> : null}
          <div className="ui-skeleton-list__content">
            <Skeleton height={16} width="40%" />
            <Skeleton height={12} width="70%" />
          </div>
        </div>
      ))}
    </div>
  )
}
