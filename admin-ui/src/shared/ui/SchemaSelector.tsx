/**
 * SchemaSelector Component
 *
 * 스키마 선택 드롭다운 컴포넌트
 */

import { useState, useRef, useEffect } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { FileCode2, ChevronDown } from 'lucide-react'
import './SchemaSelector.css'

export interface SchemaOption {
  value: string
  label: string
}

export interface SchemaSelectorProps {
  options: SchemaOption[]
  value?: string
  onChange: (value: string) => void
  placeholder?: string
  className?: string
  disabled?: boolean
  allowManualInput?: boolean
  onManualInput?: (value: string) => void
}

export function SchemaSelector({
  options,
  value,
  onChange,
  placeholder = '스키마 선택...',
  className = '',
  disabled = false,
  allowManualInput = false,
  onManualInput,
}: SchemaSelectorProps) {
  const [isOpen, setIsOpen] = useState(false)
  const [manualInput, setManualInput] = useState('')
  const containerRef = useRef<HTMLDivElement>(null)

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

  const handleSelect = (optionValue: string) => {
    onChange(optionValue)
    setIsOpen(false)
  }

  const handleManualSubmit = () => {
    if (manualInput.trim() && onManualInput) {
      onManualInput(manualInput.trim())
      setManualInput('')
      setIsOpen(false)
    }
  }

  return (
    <div className={`ui-schema-selector ${className}`} ref={containerRef}>
      <button
        type="button"
        className="ui-schema-selector__button"
        onClick={() => !disabled && setIsOpen(!isOpen)}
        disabled={disabled}
      >
        <FileCode2 size={16} />
        <span>{selectedOption?.label || value || placeholder}</span>
        <ChevronDown size={14} className={isOpen ? 'ui-schema-selector__chevron--rotated' : ''} />
      </button>

      <AnimatePresence>
        {isOpen && (
          <motion.div
            className="ui-schema-selector__dropdown"
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
          >
            {options.length === 0 ? (
              <div className="ui-schema-selector__empty">스키마가 없습니다</div>
            ) : (
              <>
                {options.map((option) => (
                  <div
                    key={option.value}
                    className={`ui-schema-selector__item ${value === option.value ? 'ui-schema-selector__item--selected' : ''}`}
                    onClick={() => handleSelect(option.value)}
                  >
                    {option.label}
                  </div>
                ))}
                {allowManualInput && onManualInput && (
                  <div className="ui-schema-selector__manual">
                    <input
                      type="text"
                      value={manualInput}
                      onChange={(e) => setManualInput(e.target.value)}
                      placeholder="스키마 ID 직접 입력..."
                      className="ui-schema-selector__manual-input"
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          handleManualSubmit()
                        }
                      }}
                    />
                    <button
                      type="button"
                      className="ui-schema-selector__manual-button"
                      onClick={handleManualSubmit}
                      disabled={!manualInput.trim()}
                    >
                      적용
                    </button>
                  </div>
                )}
              </>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
