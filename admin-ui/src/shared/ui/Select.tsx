/**
 * Select Component
 *
 * SOTA-level select dropdown with:
 * - forwardRef support for composition
 * - Controlled and uncontrolled modes
 * - Keyboard navigation (Arrow keys, Enter, Escape)
 * - Multiple sizes
 * - Icon support
 * - Disabled state
 * - Proper accessibility (ARIA listbox)
 *
 * @example
 * ```tsx
 * // Controlled mode (권장)
 * const [value, setValue] = useState('')
 * <Select
 *   value={value}
 *   onChange={setValue}
 *   options={[
 *     { value: 'a', label: 'Option A' },
 *     { value: 'b', label: 'Option B' },
 *   ]}
 * />
 *
 * // Uncontrolled mode
 * <Select
 *   defaultValue="a"
 *   onChange={(value) => console.log(value)}
 *   options={[...]}
 * />
 * ```
 *
 * @example
 * ```tsx
 * // With icons
 * <Select
 *   value={value}
 *   onChange={setValue}
 *   options={[
 *     { value: 'admin', label: 'Admin', icon: <Shield /> },
 *     { value: 'user', label: 'User', icon: <User /> },
 *   ]}
 * />
 * ```
 */
import { forwardRef, type ReactNode, useEffect, useRef, useState } from 'react'
import { Check, ChevronDown } from 'lucide-react'
import './Select.css'

export interface SelectOption {
  /** Option value */
  value: string
  /** Option label */
  label: string
  /** Optional icon */
  icon?: ReactNode
  /** Disable option */
  disabled?: boolean
}

export interface SelectProps {
  /** Selected value (controlled mode) */
  value?: string
  /** Default selected value (uncontrolled mode) */
  defaultValue?: string
  /** Callback when selection changes */
  onChange: (value: string) => void
  /** Available options */
  options: SelectOption[]
  /** Placeholder text */
  placeholder?: string
  /** Disable select */
  disabled?: boolean
  /** Size variant */
  size?: 'sm' | 'md' | 'lg'
  /** Additional CSS class */
  className?: string
  /** Icon displayed before selected value */
  icon?: ReactNode
}

export const Select = forwardRef<HTMLDivElement, SelectProps>(
  (
    {
      value: controlledValue,
      defaultValue,
      onChange,
      options,
      placeholder = 'Select...',
      disabled = false,
      size = 'md',
      className = '',
      icon,
    },
    ref
  ) => {
    const [internalValue, setInternalValue] = useState(defaultValue || '')
    const [isOpen, setIsOpen] = useState(false)
    const containerRef = useRef<HTMLDivElement>(null)

    // Controlled vs Uncontrolled
    const isControlled = controlledValue !== undefined
    const value = isControlled ? controlledValue : internalValue

    const handleChange = (newValue: string) => {
      if (!isControlled) {
        setInternalValue(newValue)
      }
      onChange(newValue)
    }

    const selectedOption = options.find((opt) => opt.value === value)

    useEffect(() => {
      const handleClickOutside = (event: MouseEvent) => {
        if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
          setIsOpen(false)
        }
      }

      if (isOpen) {
        document.addEventListener('mousedown', handleClickOutside)
      }

      return () => {
        document.removeEventListener('mousedown', handleClickOutside)
      }
    }, [isOpen])

    const handleKeyDown = (e: React.KeyboardEvent) => {
      if (disabled) return

      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault()
        setIsOpen(!isOpen)
      } else if (e.key === 'Escape') {
        setIsOpen(false)
      } else if (e.key === 'ArrowDown' && isOpen) {
        e.preventDefault()
        const currentIndex = options.findIndex((opt) => opt.value === value)
        const nextIndex = Math.min(currentIndex + 1, options.length - 1)
        if (!options[nextIndex].disabled) {
          handleChange(options[nextIndex].value)
        }
      } else if (e.key === 'ArrowUp' && isOpen) {
        e.preventDefault()
        const currentIndex = options.findIndex((opt) => opt.value === value)
        const prevIndex = Math.max(currentIndex - 1, 0)
        if (!options[prevIndex].disabled) {
          handleChange(options[prevIndex].value)
        }
      }
    }

    return (
      <div
        ref={(node) => {
          (containerRef as React.MutableRefObject<HTMLDivElement | null>).current = node
          if (typeof ref === 'function') ref(node)
          else if (ref) ref.current = node
        }}
        className={`select select-${size} ${isOpen ? 'open' : ''} ${disabled ? 'disabled' : ''} ${className}`}
      >
        <button
          type="button"
          className="select-trigger"
          onClick={() => !disabled && setIsOpen(!isOpen)}
          onKeyDown={handleKeyDown}
          disabled={disabled}
          aria-haspopup="listbox"
          aria-expanded={isOpen}
        >
          {icon ? <span className="select-icon">{icon}</span> : null}
          <span className={`select-value ${!selectedOption ? 'placeholder' : ''}`}>
            {selectedOption?.icon}
            {selectedOption?.label ?? placeholder}
          </span>
          <ChevronDown size={14} className={`select-chevron ${isOpen ? 'rotated' : ''}`} />
        </button>

        {isOpen ? <div className="select-dropdown" role="listbox">
            {options.map((option) => (
              <button
                key={option.value}
                type="button"
                role="option"
                className={`select-option ${option.value === value ? 'selected' : ''} ${option.disabled ? 'disabled' : ''}`}
                onClick={() => {
                  if (!option.disabled) {
                    handleChange(option.value)
                    setIsOpen(false)
                  }
                }}
                aria-selected={option.value === value}
                disabled={option.disabled}
              >
                {option.icon ? <span className="option-icon">{option.icon}</span> : null}
                <span className="option-label">{option.label}</span>
                {option.value === value && <Check size={14} className="option-check" />}
              </button>
            ))}
          </div> : null}
      </div>
    )
  }
)

Select.displayName = 'Select'
