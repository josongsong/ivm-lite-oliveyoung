/**
 * Section Component System
 *
 * SOTA-level section components with:
 * - CollapsibleSection with animation
 * - GroupPanel for grouping related content
 * - SectionHeader for consistent headers
 * - Proper accessibility (ARIA expanded states)
 *
 * @example
 * ```tsx
 * // Collapsible section
 * <CollapsibleSection
 *   title="Details"
 *   icon={<Info />}
 *   defaultExpanded={true}
 *   count={5}
 * >
 *   Content here
 * </CollapsibleSection>
 *
 * // Group panel
 * <GroupPanel title="Settings" subtitle="Configure your preferences">
 *   Content
 * </GroupPanel>
 * ```
 */
import { forwardRef, type HTMLAttributes, type ReactNode, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { ChevronDown, ChevronRight } from 'lucide-react'
import './Section.css'

// =============================================================================
// Section Header
// =============================================================================

export interface SectionHeaderProps extends HTMLAttributes<HTMLDivElement> {
  /** Title text */
  title: string
  /** Subtitle or description */
  subtitle?: string
  /** Leading icon */
  icon?: ReactNode
  /** Count badge */
  count?: number
  /** Right side actions */
  actions?: ReactNode
  /** Size variant */
  size?: 'sm' | 'md' | 'lg'
}

export const SectionHeader = forwardRef<HTMLDivElement, SectionHeaderProps>(
  (
    { title, subtitle, icon, count, actions, size = 'md', className = '', ...props },
    ref
  ) => {
    return (
      <div
        ref={ref}
        className={`ui-section-header ui-section-header--${size} ${className}`}
        {...props}
      >
        <div className="ui-section-header__left">
          {icon ? <span className="ui-section-header__icon">{icon}</span> : null}
          <div className="ui-section-header__text">
            <span className="ui-section-header__title">{title}</span>
            {subtitle ? <span className="ui-section-header__subtitle">{subtitle}</span> : null}
          </div>
          {count !== undefined && (
            <span className="ui-section-header__count">{count}</span>
          )}
        </div>
        {actions ? <div className="ui-section-header__actions">{actions}</div> : null}
      </div>
    )
  }
)

SectionHeader.displayName = 'SectionHeader'

// =============================================================================
// Collapsible Section
// =============================================================================

export interface CollapsibleSectionProps extends HTMLAttributes<HTMLDivElement> {
  /** Section title */
  title: string
  /** Leading icon */
  icon?: ReactNode
  /** Count badge */
  count?: number
  /** Error state */
  error?: boolean
  /** Warning state */
  warning?: boolean
  /** Default expanded state */
  defaultExpanded?: boolean
  /** Controlled expanded state */
  expanded?: boolean
  /** Callback when expanded state changes */
  onExpandedChange?: (expanded: boolean) => void
  /** Section content */
  children: ReactNode
  /** Size variant */
  size?: 'sm' | 'md' | 'lg'
}

export const CollapsibleSection = forwardRef<HTMLDivElement, CollapsibleSectionProps>(
  (
    {
      title,
      icon,
      count,
      error = false,
      warning = false,
      defaultExpanded = false,
      expanded: controlledExpanded,
      onExpandedChange,
      children,
      size = 'md',
      className = '',
      ...props
    },
    ref
  ) => {
    const [internalExpanded, setInternalExpanded] = useState(defaultExpanded)
    const isControlled = controlledExpanded !== undefined
    const expanded = isControlled ? controlledExpanded : internalExpanded

    const handleToggle = () => {
      const newExpanded = !expanded
      if (!isControlled) {
        setInternalExpanded(newExpanded)
      }
      onExpandedChange?.(newExpanded)
    }

    const stateClass = error ? 'ui-collapsible--error' : warning ? 'ui-collapsible--warning' : ''

    return (
      <div
        ref={ref}
        className={`ui-collapsible ui-collapsible--${size} ${stateClass} ${className}`}
        {...props}
      >
        <button
          type="button"
          className="ui-collapsible__trigger"
          onClick={handleToggle}
          aria-expanded={expanded}
          aria-controls={`section-${title.replace(/\s+/g, '-').toLowerCase()}`}
        >
          <span className="ui-collapsible__chevron">
            {expanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
          </span>
          {icon ? <span className="ui-collapsible__icon">{icon}</span> : null}
          <span className="ui-collapsible__title">{title}</span>
          {count !== undefined && <span className="ui-collapsible__count">{count}</span>}
        </button>

        <AnimatePresence initial={false}>
          {expanded ? <motion.div
              id={`section-${title.replace(/\s+/g, '-').toLowerCase()}`}
              className="ui-collapsible__content"
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              transition={{ duration: 0.2, ease: 'easeInOut' }}
            >
              <div className="ui-collapsible__inner">{children}</div>
            </motion.div> : null}
        </AnimatePresence>
      </div>
    )
  }
)

CollapsibleSection.displayName = 'CollapsibleSection'

// =============================================================================
// Group Panel
// =============================================================================

export interface GroupPanelProps extends HTMLAttributes<HTMLDivElement> {
  /** Panel title */
  title?: string
  /** Subtitle or description */
  subtitle?: string
  /** Leading icon */
  icon?: ReactNode
  /** Right side actions */
  actions?: ReactNode
  /** Visual variant */
  variant?: 'default' | 'outlined' | 'filled'
  /** Remove content padding */
  noPadding?: boolean
}

export const GroupPanel = forwardRef<HTMLDivElement, GroupPanelProps>(
  (
    {
      title,
      subtitle,
      icon,
      actions,
      variant = 'default',
      noPadding = false,
      children,
      className = '',
      ...props
    },
    ref
  ) => {
    return (
      <div
        ref={ref}
        className={`ui-group-panel ui-group-panel--${variant} ${className}`}
        {...props}
      >
        {(title || actions) ? <SectionHeader
            title={title || ''}
            subtitle={subtitle}
            icon={icon}
            actions={actions}
          /> : null}
        <div className={`ui-group-panel__content ${noPadding ? 'ui-group-panel__content--no-padding' : ''}`}>
          {children}
        </div>
      </div>
    )
  }
)

GroupPanel.displayName = 'GroupPanel'

// =============================================================================
// Divider
// =============================================================================

export interface DividerProps extends HTMLAttributes<HTMLDivElement> {
  /** Orientation */
  orientation?: 'horizontal' | 'vertical'
  /** Label text */
  label?: string
}

export function Divider({
  orientation = 'horizontal',
  label,
  className = '',
  ...props
}: DividerProps) {
  if (label) {
    return (
      <div
        className={`ui-divider ui-divider--${orientation} ui-divider--labeled ${className}`}
        role="separator"
        {...props}
      >
        <span className="ui-divider__line" />
        <span className="ui-divider__label">{label}</span>
        <span className="ui-divider__line" />
      </div>
    )
  }

  return (
    <div
      className={`ui-divider ui-divider--${orientation} ${className}`}
      role="separator"
      {...props}
    />
  )
}
