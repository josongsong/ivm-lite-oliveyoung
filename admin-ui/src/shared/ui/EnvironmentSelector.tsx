import { ChevronDown } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useAppStore } from '@/shared/store'
import type { EnvironmentType } from '@/shared/types'
import './EnvironmentSelector.css'

const environments: EnvironmentType[] = ['Dev', 'QA', 'Staging', 'Production']

const environmentColors: Record<EnvironmentType, string> = {
  Dev: 'var(--accent-cyan)',
  QA: 'var(--accent-purple)',
  Staging: 'var(--accent-yellow)',
  Production: 'var(--accent-red)',
}

export function EnvironmentSelector() {
  const { environment, setEnvironment } = useAppStore()
  const [isOpen, setIsOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false)
      }
    }

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside)
      return () => document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [isOpen])

  return (
    <div className="environment-selector" ref={dropdownRef}>
      <button
        className="environment-selector-button"
        onClick={() => setIsOpen(!isOpen)}
        style={{ '--env-color': environmentColors[environment] } as React.CSSProperties}
      >
        <div className="environment-selector-dot" />
        <span className="environment-selector-label">{environment}</span>
        <ChevronDown size={16} className={`environment-selector-chevron ${isOpen ? 'open' : ''}`} />
      </button>
      {isOpen && (
        <div className="environment-selector-dropdown">
          {environments.map((env) => (
            <button
              key={env}
              className={`environment-selector-option ${env === environment ? 'active' : ''}`}
              onClick={() => {
                setEnvironment(env)
                setIsOpen(false)
              }}
              style={{ '--env-color': environmentColors[env] } as React.CSSProperties}
            >
              <div className="environment-selector-dot" />
              <span>{env}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
