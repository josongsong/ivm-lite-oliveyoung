/**
 * Theme Selector - 기깔난 테마 선택 컴포넌트
 */

import { motion } from 'framer-motion'
import { Check, Moon, Palette, Sun } from 'lucide-react'
import { useState } from 'react'
import { THEME_INFO, useTheme } from '../hooks/useTheme'
import type { Theme } from '../hooks/useTheme'
import { Button } from './Button'
import './ThemeSelector.css'

// ============================================================================
// Simple Toggle (다크/라이트)
// ============================================================================

export function ThemeToggle() {
  const { isDark, toggleTheme } = useTheme()

  return (
    <Button
      variant="ghost"
      size="sm"
      onClick={toggleTheme}
      title={isDark ? 'Switch to Light Mode' : 'Switch to Dark Mode'}
    >
      {isDark ? <Sun size={18} /> : <Moon size={18} />}
    </Button>
  )
}

// ============================================================================
// Theme Cycle Button (순환)
// ============================================================================

export function ThemeCycleButton() {
  const { theme, cycleTheme } = useTheme()
  const info = THEME_INFO[theme]

  return (
    <Button
      variant="ghost"
      size="sm"
      onClick={cycleTheme}
      title={`Current: ${info.name}`}
      style={{ gap: '0.5rem' }}
    >
      <Palette size={16} />
      <span style={{ fontSize: '0.75rem' }}>{info.name}</span>
    </Button>
  )
}

// ============================================================================
// Theme Select (드롭다운)
// ============================================================================

export function ThemeSelect() {
  const { theme, setTheme } = useTheme()
  const [isOpen, setIsOpen] = useState(false)

  const themes: Theme[] = ['dark', 'light', 'light-stripe', 'light-github']

  return (
    <div className="theme-select-wrapper">
      <button
        className="theme-select-trigger"
        onClick={() => setIsOpen(!isOpen)}
      >
        <Palette size={16} />
        <span>{THEME_INFO[theme].name}</span>
      </button>

      {isOpen && (
        <motion.div
          className="theme-select-dropdown"
          initial={{ opacity: 0, y: -8 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -8 }}
        >
          {themes.map((t) => (
            <button
              key={t}
              className={`theme-select-option ${theme === t ? 'active' : ''}`}
              onClick={() => {
                setTheme(t)
                setIsOpen(false)
              }}
            >
              <span className="theme-option-name">{THEME_INFO[t].name}</span>
              {theme === t && <Check size={14} />}
            </button>
          ))}
        </motion.div>
      )}
    </div>
  )
}

// ============================================================================
// Theme Switcher Panel (기깔난 버전 - Design System용)
// ============================================================================

const THEME_COLORS: Record<Theme, { bg: string; accent: string; text: string }> = {
  dark: { bg: '#0a0a0f', accent: '#00d4ff', text: '#e8e8f0' },
  light: { bg: '#ffffff', accent: '#8b5cf6', text: '#0a0a0a' },
  'light-stripe': { bg: '#f6f9fc', accent: '#635bff', text: '#0f172a' },
  'light-github': { bg: '#ffffff', accent: '#0969da', text: '#1f2328' },
}

export function ThemeSwitcherPanel() {
  const { theme, setTheme } = useTheme()

  const themes: Theme[] = ['dark', 'light', 'light-stripe', 'light-github']

  return (
    <div className="theme-switcher-panel">
      <div className="theme-switcher-header">
        <span className="theme-switcher-title">Theme</span>
      </div>

      <div className="theme-switcher-grid">
        {themes.map((t) => {
          const colors = THEME_COLORS[t]
          const isActive = theme === t

          return (
            <motion.button
              key={t}
              className={`theme-switcher-item ${isActive ? 'active' : ''}`}
              onClick={() => setTheme(t)}
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
            >
              {/* Mini Preview */}
              <div
                className="theme-preview"
                style={{ background: colors.bg }}
              >
                <div
                  className="theme-preview-sidebar"
                  style={{ background: t === 'dark' ? '#12121a' : '#f0f0f0' }}
                />
                <div className="theme-preview-content">
                  <div
                    className="theme-preview-header"
                    style={{ background: colors.accent }}
                  />
                  <div
                    className="theme-preview-text"
                    style={{ background: colors.text, opacity: 0.3 }}
                  />
                  <div
                    className="theme-preview-text short"
                    style={{ background: colors.text, opacity: 0.2 }}
                  />
                </div>
                {isActive && (
                  <motion.div
                    className="theme-preview-check"
                    initial={{ scale: 0 }}
                    animate={{ scale: 1 }}
                    style={{ background: colors.accent }}
                  >
                    <Check size={10} color={t === 'dark' ? '#0a0a0f' : '#fff'} />
                  </motion.div>
                )}
              </div>

              {/* Label */}
              <span className="theme-switcher-label">
                {THEME_INFO[t].name.split(' ')[0]}
              </span>
            </motion.button>
          )
        })}
      </div>
    </div>
  )
}
