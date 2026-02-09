/**
 * Theme Hook - 테마 전환 기능
 * 
 * 사용 가능한 테마:
 * - 'dark' (기본) - Cyberpunk Neon
 * - 'light' - Linear/Vercel Style
 * - 'light-stripe' - Stripe Style
 * - 'light-github' - GitHub Style
 */

import { useEffect, useState } from 'react'

export type Theme = 'dark' | 'light' | 'light-stripe' | 'light-github'

const THEME_KEY = 'ivm-theme'

export function useTheme() {
  const [theme, setThemeState] = useState<Theme>(() => {
    if (typeof window === 'undefined') return 'dark'
    return (localStorage.getItem(THEME_KEY) as Theme) || 'dark'
  })

  useEffect(() => {
    const root = document.documentElement
    
    // 이전 테마 속성 제거
    root.removeAttribute('data-theme')
    
    // 다크 테마가 아니면 data-theme 속성 추가
    if (theme !== 'dark') {
      root.setAttribute('data-theme', theme)
    }
    
    // localStorage에 저장
    localStorage.setItem(THEME_KEY, theme)
  }, [theme])

  const setTheme = (newTheme: Theme) => {
    setThemeState(newTheme)
  }

  const toggleTheme = () => {
    setThemeState(prev => prev === 'dark' ? 'light' : 'dark')
  }

  const cycleTheme = () => {
    const themes: Theme[] = ['dark', 'light', 'light-stripe', 'light-github']
    const currentIndex = themes.indexOf(theme)
    const nextIndex = (currentIndex + 1) % themes.length
    setThemeState(themes[nextIndex])
  }

  return {
    theme,
    setTheme,
    toggleTheme,
    cycleTheme,
    isDark: theme === 'dark',
    isLight: theme !== 'dark',
  }
}

// 테마 정보
export const THEME_INFO: Record<Theme, { name: string; description: string }> = {
  dark: {
    name: 'Cyberpunk Neon',
    description: '다크 배경 + 네온 액센트',
  },
  light: {
    name: 'Linear Style',
    description: '클린 화이트 + 퍼플 액센트',
  },
  'light-stripe': {
    name: 'Stripe Style',
    description: '우아한 블루/퍼플 그라디언트',
  },
  'light-github': {
    name: 'GitHub Style',
    description: '클린 미니멀리스트',
  },
}
