/**
 * Modal Component
 *
 * SOTA-level accessible modal dialog with:
 * - Portal rendering (z-index 이슈 방지)
 * - Focus trap (Tab 키로 모달 내부만 순환)
 * - Body scroll lock (배경 스크롤 방지)
 * - Keyboard navigation (Escape로 닫기)
 * - Focus management (열릴 때 첫 요소, 닫을 때 원래 요소로 복원)
 * - forwardRef support for composition
 *
 * @example
 * ```tsx
 * const [isOpen, setIsOpen] = useState(false)
 *
 * <Modal
 *   isOpen={isOpen}
 *   onClose={() => setIsOpen(false)}
 *   title="Confirm Action"
 *   size="md"
 *   footer={
 *     <>
 *       <Button variant="ghost" onClick={() => setIsOpen(false)}>Cancel</Button>
 *       <Button variant="primary" onClick={handleConfirm}>Confirm</Button>
 *     </>
 *   }
 * >
 *   Are you sure you want to proceed?
 * </Modal>
 * ```
 *
 * @example
 * ```tsx
 * // Form in modal
 * <Modal
 *   isOpen={isOpen}
 *   onClose={() => setIsOpen(false)}
 *   title="Edit User"
 *   footer={
 *     <Button type="submit" form="user-form" variant="primary">Save</Button>
 *   }
 * >
 *   <form id="user-form" onSubmit={handleSubmit}>
 *     <Input name="name" />
 *   </form>
 * </Modal>
 * ```
 */
import { AnimatePresence, motion } from 'framer-motion'
import { X } from 'lucide-react'
import { forwardRef, useEffect, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import './Modal.css'

export interface ModalProps {
  /** Whether modal is open */
  isOpen: boolean
  /** Callback when modal should close */
  onClose: () => void
  /** Modal title */
  title: string
  /** Modal content */
  children: ReactNode
  /** Modal size */
  size?: 'sm' | 'md' | 'lg' | 'xl' | 'full'
  /** Footer content (e.g., action buttons) */
  footer?: ReactNode
  /** Additional CSS class */
  className?: string
  /** Element to focus when modal opens (defaults to first focusable element) */
  initialFocusRef?: React.RefObject<HTMLElement>
  /** Prevent closing on overlay click */
  preventCloseOnOverlayClick?: boolean
}

/**
 * Get all focusable elements within a container
 */
function getFocusableElements(container: HTMLElement): HTMLElement[] {
  const selector = [
    'a[href]',
    'button:not([disabled])',
    'textarea:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    '[tabindex]:not([tabindex="-1"])',
  ].join(', ')

  return Array.from(container.querySelectorAll<HTMLElement>(selector)).filter(
    (el) => el.offsetParent !== null // Visible elements only
  )
}

export const Modal = forwardRef<HTMLDivElement, ModalProps>(
  (
    {
      isOpen,
      onClose,
      title,
      children,
      size = 'md',
      footer,
      className = '',
      initialFocusRef,
      preventCloseOnOverlayClick = false,
    },
    ref
  ) => {
    const contentRef = useRef<HTMLDivElement>(null)
    const previousActiveElementRef = useRef<HTMLElement | null>(null)

    // Body scroll lock
    useEffect(() => {
      if (isOpen) {
        // Save current scroll position
        const scrollY = window.scrollY
        document.body.style.position = 'fixed'
        document.body.style.top = `-${scrollY}px`
        document.body.style.width = '100%'

        return () => {
          // Restore scroll position
          document.body.style.position = ''
          document.body.style.top = ''
          document.body.style.width = ''
          window.scrollTo(0, scrollY)
        }
      }
    }, [isOpen])

    // Focus trap and initial focus
    useEffect(() => {
      if (!isOpen || !contentRef.current) return

      // Save the element that had focus before modal opened
      previousActiveElementRef.current = document.activeElement as HTMLElement

      // Initial focus
      if (initialFocusRef?.current) {
        initialFocusRef.current.focus()
      } else {
        // Focus first focusable element
        const focusableElements = getFocusableElements(contentRef.current)
        const firstElement = focusableElements[0]
        if (firstElement) {
          firstElement.focus()
        }
      }

      // Focus trap handler
      const handleTabKey = (e: KeyboardEvent) => {
        if (e.key !== 'Tab' || !contentRef.current) return

        const focusableElements = getFocusableElements(contentRef.current)
        if (focusableElements.length === 0) return

        const firstElement = focusableElements[0]
        const lastElement = focusableElements[focusableElements.length - 1]

        if (e.shiftKey) {
          // Shift + Tab (backwards)
          if (document.activeElement === firstElement) {
            e.preventDefault()
            lastElement.focus()
          }
        } else {
          // Tab (forwards)
          if (document.activeElement === lastElement) {
            e.preventDefault()
            firstElement.focus()
          }
        }
      }

      // Escape key handler
      const handleEscape = (e: KeyboardEvent) => {
        if (e.key === 'Escape') {
          e.preventDefault()
          onClose()
        }
      }

      document.addEventListener('keydown', handleTabKey)
      document.addEventListener('keydown', handleEscape)

      return () => {
        document.removeEventListener('keydown', handleTabKey)
        document.removeEventListener('keydown', handleEscape)

        // Restore focus to previous element
        if (previousActiveElementRef.current) {
          previousActiveElementRef.current.focus()
        }
      }
    }, [isOpen, onClose, initialFocusRef])

    const handleOverlayClick = (e: React.MouseEvent) => {
      if (!preventCloseOnOverlayClick && e.target === e.currentTarget) {
        onClose()
      }
    }

    if (!isOpen) return null

    const modalContent = (
      <AnimatePresence>
        <motion.div
          className="modal-overlay"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={handleOverlayClick}
          role="dialog"
          aria-modal="true"
          aria-labelledby="modal-title"
        >
          <motion.div
            ref={(node) => {
              contentRef.current = node
              if (typeof ref === 'function') ref(node)
              else if (ref) ref.current = node
            }}
            className={`modal-content modal-content--${size} ${className}`}
            initial={{ scale: 0.95, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            exit={{ scale: 0.95, opacity: 0 }}
            onClick={(e) => e.stopPropagation()}
            role="document"
          >
            <div className="modal-header">
              <h2 id="modal-title" className="modal-title">{title}</h2>
              <button
                className="modal-close"
                onClick={onClose}
                type="button"
                aria-label="Close modal"
              >
                <X size={20} />
              </button>
            </div>
            <div className="modal-body">
              {children}
            </div>
            {footer ? <div className="modal-footer">{footer}</div> : null}
          </motion.div>
        </motion.div>
      </AnimatePresence>
    )

    // Portal to body for proper z-index stacking
    return createPortal(modalContent, document.body)
  }
)

Modal.displayName = 'Modal'
