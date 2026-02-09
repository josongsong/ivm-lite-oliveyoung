/**
 * Tooltip Component
 *
 * SOTA-level tooltip with:
 * - Pure CSS positioning (no external dependencies)
 * - Multiple placement options
 * - Delay support
 * - Custom content support
 * - Accessible (role="tooltip")
 *
 * @example
 * ```tsx
 * <Tooltip content="This is a tooltip">
 *   <Button>Hover me</Button>
 * </Tooltip>
 *
 * <Tooltip content="Bottom tooltip" placement="bottom" delay={200}>
 *   <IconButton icon={<Info />} />
 * </Tooltip>
 * ```
 */
import {
  cloneElement,
  forwardRef,
  type HTMLAttributes,
  type ReactElement,
  type ReactNode,
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react'
import { createPortal } from 'react-dom'
import './Tooltip.css'

export interface TooltipProps extends Omit<HTMLAttributes<HTMLDivElement>, 'content'> {
  /** Tooltip content */
  content: ReactNode
  /** Placement relative to trigger */
  placement?: 'top' | 'bottom' | 'left' | 'right'
  /** Delay before showing (ms) */
  delay?: number
  /** Delay before hiding (ms) */
  hideDelay?: number
  /** Trigger element */
  children: ReactElement<{
    onMouseEnter?: (e: React.MouseEvent) => void
    onMouseLeave?: (e: React.MouseEvent) => void
    onFocus?: (e: React.FocusEvent) => void
    onBlur?: (e: React.FocusEvent) => void
  }>
  /** Disable tooltip */
  disabled?: boolean
  /** Maximum width */
  maxWidth?: number
}

export const Tooltip = forwardRef<HTMLDivElement, TooltipProps>(
  (
    {
      content,
      placement = 'top',
      delay = 100,
      hideDelay = 0,
      children,
      disabled = false,
      maxWidth = 250,
      className = '',
      ...props
    },
    ref
  ) => {
    const [visible, setVisible] = useState(false)
    const [position, setPosition] = useState({ top: 0, left: 0 })
    const triggerRef = useRef<HTMLElement>(null)
    const tooltipRef = useRef<HTMLDivElement>(null)
    const showTimeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
    const hideTimeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

    const calculatePosition = useCallback(() => {
      if (!triggerRef.current || !tooltipRef.current) return

      const triggerRect = triggerRef.current.getBoundingClientRect()
      const tooltipRect = tooltipRef.current.getBoundingClientRect()
      const gap = 8

      let top = 0
      let left = 0

      switch (placement) {
        case 'top':
          top = triggerRect.top - tooltipRect.height - gap
          left = triggerRect.left + (triggerRect.width - tooltipRect.width) / 2
          break
        case 'bottom':
          top = triggerRect.bottom + gap
          left = triggerRect.left + (triggerRect.width - tooltipRect.width) / 2
          break
        case 'left':
          top = triggerRect.top + (triggerRect.height - tooltipRect.height) / 2
          left = triggerRect.left - tooltipRect.width - gap
          break
        case 'right':
          top = triggerRect.top + (triggerRect.height - tooltipRect.height) / 2
          left = triggerRect.right + gap
          break
      }

      // Keep tooltip within viewport
      const padding = 8
      left = Math.max(padding, Math.min(left, window.innerWidth - tooltipRect.width - padding))
      top = Math.max(padding, Math.min(top, window.innerHeight - tooltipRect.height - padding))

      setPosition({ top, left })
    }, [placement])

    const showTooltip = () => {
      if (disabled) return
      clearTimeout(hideTimeoutRef.current)
      showTimeoutRef.current = setTimeout(() => {
        setVisible(true)
      }, delay)
    }

    const hideTooltip = () => {
      clearTimeout(showTimeoutRef.current)
      hideTimeoutRef.current = setTimeout(() => {
        setVisible(false)
      }, hideDelay)
    }

    useEffect(() => {
      if (visible) {
        calculatePosition()
        window.addEventListener('scroll', calculatePosition, true)
        window.addEventListener('resize', calculatePosition)
      }
      return () => {
        window.removeEventListener('scroll', calculatePosition, true)
        window.removeEventListener('resize', calculatePosition)
      }
    }, [visible, calculatePosition])

    useEffect(() => {
      return () => {
        clearTimeout(showTimeoutRef.current)
        clearTimeout(hideTimeoutRef.current)
      }
    }, [])

    // Clone child with event handlers
    const childProps = children.props as Record<string, unknown>
    const trigger = cloneElement(children as ReactElement<Record<string, unknown>>, {
      ref: triggerRef as unknown,
      onMouseEnter: (e: React.MouseEvent) => {
        showTooltip()
        const handler = childProps.onMouseEnter as ((e: React.MouseEvent) => void) | undefined
        handler?.(e)
      },
      onMouseLeave: (e: React.MouseEvent) => {
        hideTooltip()
        const handler = childProps.onMouseLeave as ((e: React.MouseEvent) => void) | undefined
        handler?.(e)
      },
      onFocus: (e: React.FocusEvent) => {
        showTooltip()
        const handler = childProps.onFocus as ((e: React.FocusEvent) => void) | undefined
        handler?.(e)
      },
      onBlur: (e: React.FocusEvent) => {
        hideTooltip()
        const handler = childProps.onBlur as ((e: React.FocusEvent) => void) | undefined
        handler?.(e)
      },
      'aria-describedby': visible ? 'tooltip' : undefined,
    })

    return (
      <>
        {trigger}
        {visible ? createPortal(
            <div
              ref={(node) => {
                tooltipRef.current = node
                if (typeof ref === 'function') ref(node)
                else if (ref) ref.current = node
              }}
              role="tooltip"
              id="tooltip"
              className={`ui-tooltip ui-tooltip--${placement} ${className}`}
              style={{
                top: position.top,
                left: position.left,
                maxWidth,
              }}
              {...props}
            >
              <div className="ui-tooltip__content">{content}</div>
              <div className="ui-tooltip__arrow" />
            </div>,
            document.body
          ) : null}
      </>
    )
  }
)

Tooltip.displayName = 'Tooltip'
