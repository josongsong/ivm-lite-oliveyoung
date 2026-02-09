/**
 * Tabs Component
 *
 * SOTA-level tab navigation with:
 * - Context-based state management
 * - Controlled and uncontrolled modes
 * - Keyboard navigation (Arrow keys, Enter, Space)
 * - Proper ARIA attributes
 * - Multiple tab panels support
 * - Responsive design (auto-scroll on small screens)
 *
 * @example
 * ```tsx
 * // Controlled mode (권장)
 * const [activeTab, setActiveTab] = useState('tab1')
 * <Tabs value={activeTab} onValueChange={setActiveTab}>
 *   <TabsList scrollable showScrollIndicators>
 *     <TabsTrigger value="tab1">Tab 1</TabsTrigger>
 *     <TabsTrigger value="tab2">Tab 2</TabsTrigger>
 *   </TabsList>
 *   <TabsContent value="tab1">Content 1</TabsContent>
 *   <TabsContent value="tab2">Content 2</TabsContent>
 * </Tabs>
 *
 * // Uncontrolled mode
 * <Tabs defaultValue="tab1" onValueChange={(value) => console.log(value)}>
 *   ...
 * </Tabs>
 * ```
 *
 * @example
 * ```tsx
 * // With icons and badges
 * <Tabs value={activeTab} onValueChange={setActiveTab}>
 *   <TabsList scrollable>
 *     <TabsTrigger value="tab1" icon={<Database />} badge="3">
 *       Data
 *     </TabsTrigger>
 *   </TabsList>
 * </Tabs>
 * ```
 *
 * @responsive
 * - 작은 화면(640px 이하)에서 자동으로 가로 스크롤 활성화
 * - 탭 레이블이 길면 ellipsis 처리
 * - 스크롤 인디케이터로 스크롤 가능 여부 표시
 */
import { createContext, type ReactNode, useContext, useEffect, useRef, useState } from 'react'
import './Tabs.css'

interface TabsContextValue {
  value: string
  onValueChange: (value: string) => void
}

const TabsContext = createContext<TabsContextValue | null>(null)

function useTabsContext() {
  const context = useContext(TabsContext)
  if (!context) {
    throw new Error('Tabs components must be used within a Tabs provider. Wrap your tabs with <Tabs> component.')
  }
  return context
}

export interface TabsProps {
  /** Currently selected tab value (controlled mode) */
  value?: string
  /** Default selected tab value (uncontrolled mode) */
  defaultValue?: string
  /** Callback when tab changes */
  onValueChange: (value: string) => void
  /** Tab children (TabsList and TabsContent) */
  children: ReactNode
  /** Additional CSS class */
  className?: string
}

export function Tabs({ value: controlledValue, defaultValue, onValueChange, children, className = '' }: TabsProps) {
  const [internalValue, setInternalValue] = useState(defaultValue || '')

  // Controlled vs Uncontrolled
  const isControlled = controlledValue !== undefined
  const value = isControlled ? controlledValue : internalValue

  const handleValueChange = (newValue: string) => {
    if (!isControlled) {
      setInternalValue(newValue)
    }
    onValueChange(newValue)
  }

  return (
    <TabsContext.Provider value={{ value, onValueChange: handleValueChange }}>
      <div className={`tabs ${className}`}>{children}</div>
    </TabsContext.Provider>
  )
}

export interface TabsListProps {
  /** Tab trigger buttons */
  children: ReactNode
  /** Additional CSS class */
  className?: string
  /** TabsList 스타일 변형 */
  variant?: 'default' | 'compact' | 'pills' | 'segmented'
  /**
   * 반응형 동작
   * - iconsOnlyMd: 1024px 이하에서 라벨 숨김(아이콘만)
   * - iconsOnlySm: 640px 이하에서 라벨 숨김(아이콘만)
   */
  responsive?: 'iconsOnlyMd' | 'iconsOnlySm'
  /** Enable horizontal scrolling for many tabs */
  scrollable?: boolean
  /** Show scroll indicators */
  showScrollIndicators?: boolean
}

export function TabsList({
  children,
  className = '',
  variant = 'default',
  responsive,
  scrollable = true,
  showScrollIndicators = false,
}: TabsListProps) {
  const tabListRef = useRef<HTMLDivElement>(null)
  const [tabValues, setTabValues] = useState<string[]>([])
  const [showLeftIndicator, setShowLeftIndicator] = useState(false)
  const [showRightIndicator, setShowRightIndicator] = useState(false)

  // Extract tab values from children
  useEffect(() => {
    if (!tabListRef.current) return
    const tabs = Array.from(tabListRef.current.querySelectorAll<HTMLButtonElement>('[role="tab"]'))
    const values = tabs
      .map((tab) => {
        const controls = tab.getAttribute('aria-controls')
        return controls ? controls.replace('tabpanel-', '') : ''
      })
      .filter(Boolean)
    setTabValues(values)
  }, [children])

  // 스크롤 인디케이터 업데이트
  useEffect(() => {
    if (!scrollable || !showScrollIndicators || !tabListRef.current) return

    const updateIndicators = () => {
      const element = tabListRef.current
      if (!element) return

      const { scrollLeft, scrollWidth, clientWidth } = element
      setShowLeftIndicator(scrollLeft > 0)
      setShowRightIndicator(scrollLeft < scrollWidth - clientWidth - 1)
    }

    updateIndicators()
    const element = tabListRef.current
    element.addEventListener('scroll', updateIndicators)
    window.addEventListener('resize', updateIndicators)

    return () => {
      element?.removeEventListener('scroll', updateIndicators)
      window.removeEventListener('resize', updateIndicators)
    }
  }, [scrollable, showScrollIndicators, children])

  const scrollableClass = scrollable ? 'scrollable' : ''
  const indicatorsClass = showScrollIndicators ? 'with-indicators' : ''
  const variantClass = variant === 'default' ? '' : variant
  const responsiveClass =
    responsive === 'iconsOnlyMd' ? 'mobile-icons-only-md' : responsive === 'iconsOnlySm' ? 'mobile-icons-only' : ''

  return (
    <div className={`tabs-list-wrapper ${indicatorsClass}`}>
      {showScrollIndicators && showLeftIndicator && (
        <div className="tabs-scroll-indicator tabs-scroll-indicator--left" aria-hidden="true" />
      )}
      <div
        ref={tabListRef}
        className={`tabs-list ${scrollableClass} ${variantClass} ${responsiveClass} ${className}`}
        role="tablist"
        data-tab-values={tabValues.join(',')}
      >
        {children}
      </div>
      {showScrollIndicators && showRightIndicator && (
        <div className="tabs-scroll-indicator tabs-scroll-indicator--right" aria-hidden="true" />
      )}
    </div>
  )
}

export interface TabsTriggerProps {
  /** Tab value (must match TabsContent value) */
  value: string
  /** Tab label */
  children: ReactNode
  /** Disable tab */
  disabled?: boolean
  /** Additional CSS class */
  className?: string
  /** Icon displayed before label */
  icon?: ReactNode
  /** Badge displayed after label */
  badge?: ReactNode
}

export function TabsTrigger({
  value,
  children,
  disabled = false,
  className = '',
  icon,
  badge,
}: TabsTriggerProps) {
  const { value: selectedValue, onValueChange } = useTabsContext()
  const isActive = selectedValue === value
  const triggerRef = useRef<HTMLButtonElement>(null)

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (disabled) return

    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      onValueChange(value)
    } else if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
      e.preventDefault()
      // Arrow key navigation between tabs
      const tabList = triggerRef.current?.closest('[role="tablist"]')
      if (!tabList) return

      const tabValues = tabList.getAttribute('data-tab-values')?.split(',') || []
      const currentIndex = tabValues.indexOf(value)
      if (currentIndex === -1) return

      if (e.key === 'ArrowLeft') {
        const prevIndex = currentIndex > 0 ? currentIndex - 1 : tabValues.length - 1
        const prevValue = tabValues[prevIndex]
        if (prevValue) {
          onValueChange(prevValue)
          // Focus the previous tab button
          const prevTab = tabList.querySelector(`[aria-controls="tabpanel-${prevValue}"]`) as HTMLButtonElement
          prevTab?.focus()
        }
      } else if (e.key === 'ArrowRight') {
        const nextIndex = currentIndex < tabValues.length - 1 ? currentIndex + 1 : 0
        const nextValue = tabValues[nextIndex]
        if (nextValue) {
          onValueChange(nextValue)
          // Focus the next tab button
          const nextTab = tabList.querySelector(`[aria-controls="tabpanel-${nextValue}"]`) as HTMLButtonElement
          nextTab?.focus()
        }
      }
    }
  }

  return (
    <button
      ref={triggerRef}
      type="button"
      role="tab"
      aria-selected={isActive}
      aria-controls={`tabpanel-${value}`}
      disabled={disabled}
      className={`tabs-trigger ${isActive ? 'active' : ''} ${disabled ? 'disabled' : ''} ${className}`}
      onClick={() => onValueChange(value)}
      onKeyDown={handleKeyDown}
    >
      {icon ? <span className="tabs-trigger-icon">{icon}</span> : null}
      <span className="tabs-trigger-label">{children}</span>
      {badge ? <span className="tabs-trigger-badge">{badge}</span> : null}
    </button>
  )
}

export interface TabsContentProps {
  /** Tab value (must match TabsTrigger value) */
  value: string
  /** Tab panel content */
  children: ReactNode
  /** Additional CSS class */
  className?: string
}

export function TabsContent({ value, children, className = '' }: TabsContentProps) {
  const { value: selectedValue } = useTabsContext()

  if (selectedValue !== value) return null

  return (
    <div
      role="tabpanel"
      id={`tabpanel-${value}`}
      className={`tabs-content ${className}`}
    >
      {children}
    </div>
  )
}
