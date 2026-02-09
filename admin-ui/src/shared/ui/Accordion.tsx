/**
 * Accordion Component
 *
 * 접기/펼치기 아코디언 컴포넌트:
 * - Single 또는 Multiple 모드 지원
 * - Uncontrolled 모드 (defaultValue)
 * - 애니메이션 지원
 * - 키보드 네비게이션
 *
 * @example
 * ```tsx
 * // Single mode (하나만 열림)
 * <Accordion type="single" defaultValue="item1">
 *   <AccordionItem value="item1">
 *     <AccordionTrigger>Section 1</AccordionTrigger>
 *     <AccordionContent>Content 1</AccordionContent>
 *   </AccordionItem>
 * </Accordion>
 * ```
 *
 * @example
 * ```tsx
 * // Multiple mode (여러 개 동시에 열림)
 * <Accordion type="multiple" defaultValue={['item1', 'item2']}>
 *   <AccordionItem value="item1">
 *     <AccordionTrigger>Section 1</AccordionTrigger>
 *     <AccordionContent>Content 1</AccordionContent>
 *   </AccordionItem>
 * </Accordion>
 * ```
 */
import { createContext, type ReactNode, useContext, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { ChevronDown } from 'lucide-react'
import './Accordion.css'

interface AccordionContextValue {
  expandedItems: string[]
  toggleItem: (value: string) => void
  type: 'single' | 'multiple'
}

const AccordionContext = createContext<AccordionContextValue | null>(null)

function useAccordionContext() {
  const context = useContext(AccordionContext)
  if (!context) {
    throw new Error('Accordion components must be used within an Accordion provider. Wrap your accordion items with <Accordion> component.')
  }
  return context
}

export interface AccordionProps {
  /** Accordion items */
  children: ReactNode
  /** Single (하나만 열림) or Multiple (여러 개 동시에 열림) */
  type?: 'single' | 'multiple'
  /** Default expanded items (uncontrolled) */
  defaultValue?: string | string[]
  /** Additional CSS class */
  className?: string
}

export function Accordion({
  children,
  type = 'single',
  defaultValue,
  className = '',
}: AccordionProps) {
  const [expandedItems, setExpandedItems] = useState<string[]>(() => {
    if (!defaultValue) return []
    return Array.isArray(defaultValue) ? defaultValue : [defaultValue]
  })

  const toggleItem = (value: string) => {
    if (type === 'single') {
      setExpandedItems((prev) => (prev.includes(value) ? [] : [value]))
    } else {
      setExpandedItems((prev) =>
        prev.includes(value) ? prev.filter((v) => v !== value) : [...prev, value]
      )
    }
  }

  return (
    <AccordionContext.Provider value={{ expandedItems, toggleItem, type }}>
      <div className={`accordion ${className}`}>{children}</div>
    </AccordionContext.Provider>
  )
}

export interface AccordionItemProps {
  /** Unique item value */
  value: string
  /** Item content (AccordionTrigger and AccordionContent) */
  children: ReactNode
  /** Additional CSS class */
  className?: string
}

export function AccordionItem({ value, children, className = '' }: AccordionItemProps) {
  const { expandedItems } = useAccordionContext()
  const isExpanded = expandedItems.includes(value)

  return (
    <div className={`accordion-item ${isExpanded ? 'expanded' : ''} ${className}`} data-state={isExpanded ? 'open' : 'closed'}>
      {children}
    </div>
  )
}

export interface AccordionTriggerProps {
  /** Trigger label */
  children: ReactNode
  /** Item value (must match AccordionItem value) */
  value: string
  /** Icon displayed before label */
  icon?: ReactNode
  /** Badge displayed after label */
  badge?: ReactNode
  /** Additional CSS class */
  className?: string
}

export function AccordionTrigger({
  children,
  value,
  icon,
  badge,
  className = '',
}: AccordionTriggerProps) {
  const { expandedItems, toggleItem } = useAccordionContext()
  const isExpanded = expandedItems.includes(value)

  return (
    <button
      type="button"
      className={`accordion-trigger ${className}`}
      onClick={() => toggleItem(value)}
      aria-expanded={isExpanded}
    >
      <div className="accordion-trigger-content">
        {icon ? <span className="accordion-trigger-icon">{icon}</span> : null}
        <span className="accordion-trigger-text">{children}</span>
        {badge ? <span className="accordion-trigger-badge">{badge}</span> : null}
      </div>
      <ChevronDown size={16} className={`accordion-chevron ${isExpanded ? 'expanded' : ''}`} />
    </button>
  )
}

export interface AccordionContentProps {
  /** Content to display when expanded */
  children: ReactNode
  /** Item value (must match AccordionItem value) */
  value: string
  /** Additional CSS class */
  className?: string
}

export function AccordionContent({ children, value, className = '' }: AccordionContentProps) {
  const { expandedItems } = useAccordionContext()
  const isExpanded = expandedItems.includes(value)

  return (
    <AnimatePresence initial={false}>
      {isExpanded ? <motion.div
          initial={{ height: 0, opacity: 0 }}
          animate={{ height: 'auto', opacity: 1 }}
          exit={{ height: 0, opacity: 0 }}
          transition={{ duration: 0.2, ease: 'easeInOut' }}
          className={`accordion-content ${className}`}
        >
          <div className="accordion-content-inner">{children}</div>
        </motion.div> : null}
    </AnimatePresence>
  )
}
