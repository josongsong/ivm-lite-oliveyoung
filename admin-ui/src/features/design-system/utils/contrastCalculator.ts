/**
 * Contrast Calculator Utility
 * WCAG 대비 비율 계산 및 접근성 레벨 판정
 *
 * Phase 1-G: Contrast Calculator
 *
 * @see https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum.html
 * @see https://www.w3.org/TR/WCAG21/#dfn-contrast-ratio
 */

import type { ContrastResult } from '../data/types'

// ============================================================================
// Color Parsing
// ============================================================================

/**
 * RGB 색상 값 (0-255)
 */
interface RGB {
  r: number
  g: number
  b: number
}

/**
 * HEX 색상을 RGB로 변환
 * 지원 포맷: #RGB, #RRGGBB, RGB, RRGGBB
 */
export function hexToRgb(hex: string): RGB | null {
  // # 제거
  let cleanHex = hex.replace(/^#/, '')

  // 3자리 HEX를 6자리로 확장 (#RGB → #RRGGBB)
  if (cleanHex.length === 3) {
    cleanHex = cleanHex
      .split('')
      .map((char) => char + char)
      .join('')
  }

  // 유효성 검사
  if (!/^[a-fA-F0-9]{6}$/.test(cleanHex)) {
    return null
  }

  const result = /^([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(cleanHex)
  if (!result) {
    return null
  }

  return {
    r: parseInt(result[1], 16),
    g: parseInt(result[2], 16),
    b: parseInt(result[3], 16),
  }
}

/**
 * RGB 문자열을 파싱
 * 지원 포맷: rgb(255, 255, 255), rgba(255, 255, 255, 1)
 */
export function parseRgbString(rgb: string): RGB | null {
  const match = rgb.match(/rgba?\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/)
  if (!match) {
    return null
  }

  return {
    r: parseInt(match[1], 10),
    g: parseInt(match[2], 10),
    b: parseInt(match[3], 10),
  }
}

/**
 * HSL 문자열을 RGB로 변환
 * 지원 포맷: hsl(360, 100%, 50%), hsla(360, 100%, 50%, 1)
 */
export function hslToRgb(hsl: string): RGB | null {
  const match = hsl.match(/hsla?\s*\(\s*(\d+)\s*,\s*(\d+)%\s*,\s*(\d+)%/)
  if (!match) {
    return null
  }

  const h = parseInt(match[1], 10) / 360
  const s = parseInt(match[2], 10) / 100
  const l = parseInt(match[3], 10) / 100

  let r: number, g: number, b: number

  if (s === 0) {
    r = g = b = l
  } else {
    const hue2rgb = (p: number, q: number, t: number): number => {
      let adjustedT = t
      if (adjustedT < 0) adjustedT += 1
      if (adjustedT > 1) adjustedT -= 1
      if (adjustedT < 1 / 6) return p + (q - p) * 6 * adjustedT
      if (adjustedT < 1 / 2) return q
      if (adjustedT < 2 / 3) return p + (q - p) * (2 / 3 - adjustedT) * 6
      return p
    }

    const q = l < 0.5 ? l * (1 + s) : l + s - l * s
    const p = 2 * l - q
    r = hue2rgb(p, q, h + 1 / 3)
    g = hue2rgb(p, q, h)
    b = hue2rgb(p, q, h - 1 / 3)
  }

  return {
    r: Math.round(r * 255),
    g: Math.round(g * 255),
    b: Math.round(b * 255),
  }
}

/**
 * 색상 문자열을 RGB로 변환 (HEX, RGB, HSL 지원)
 */
export function parseColor(color: string): RGB | null {
  const trimmed = color.trim().toLowerCase()

  // Named colors 지원 (자주 사용되는 색상)
  const namedColors: Record<string, string> = {
    black: '#000000',
    white: '#ffffff',
    red: '#ff0000',
    green: '#008000',
    blue: '#0000ff',
    transparent: '#00000000',
  }

  if (namedColors[trimmed]) {
    return hexToRgb(namedColors[trimmed])
  }

  // HEX 포맷
  if (trimmed.startsWith('#') || /^[a-f0-9]{3,6}$/i.test(trimmed)) {
    return hexToRgb(trimmed)
  }

  // RGB 포맷
  if (trimmed.startsWith('rgb')) {
    return parseRgbString(trimmed)
  }

  // HSL 포맷
  if (trimmed.startsWith('hsl')) {
    return hslToRgb(trimmed)
  }

  return null
}

// ============================================================================
// WCAG Luminance Calculation
// ============================================================================

/**
 * sRGB 값을 선형화
 * @see https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
 */
function linearize(value: number): number {
  const normalized = value / 255
  return normalized <= 0.03928
    ? normalized / 12.92
    : Math.pow((normalized + 0.055) / 1.055, 2.4)
}

/**
 * RGB 색상의 상대 휘도(Relative Luminance) 계산
 * 공식: L = 0.2126 * R + 0.7152 * G + 0.0722 * B
 *
 * @see https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
 */
export function getRelativeLuminance(rgb: RGB): number {
  const r = linearize(rgb.r)
  const g = linearize(rgb.g)
  const b = linearize(rgb.b)

  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

// ============================================================================
// Contrast Ratio Calculation
// ============================================================================

/**
 * 두 색상 간의 대비 비율 계산
 * 공식: (L1 + 0.05) / (L2 + 0.05)
 *
 * @param foreground - 전경색 (텍스트 색상)
 * @param background - 배경색
 * @returns 대비 비율 (1:1 ~ 21:1)
 *
 * @see https://www.w3.org/TR/WCAG21/#dfn-contrast-ratio
 */
export function calculateContrastRatio(foreground: RGB, background: RGB): number {
  const l1 = getRelativeLuminance(foreground)
  const l2 = getRelativeLuminance(background)

  const lighter = Math.max(l1, l2)
  const darker = Math.min(l1, l2)

  return (lighter + 0.05) / (darker + 0.05)
}

/**
 * 두 색상 간의 대비 계산 및 WCAG 레벨 판정
 *
 * WCAG 2.1 기준:
 * - Level AA (일반 텍스트): 4.5:1 이상
 * - Level AAA (일반 텍스트): 7:1 이상
 * - Level AA (큰 텍스트, 18pt+ 또는 14pt+ bold): 3:1 이상
 * - Level AAA (큰 텍스트): 4.5:1 이상
 *
 * @param foreground - 전경색 (HEX, RGB, HSL 문자열)
 * @param background - 배경색 (HEX, RGB, HSL 문자열)
 * @returns ContrastResult 또는 null (파싱 실패 시)
 */
export function calculateContrast(
  foreground: string,
  background: string
): ContrastResult | null {
  const fgRgb = parseColor(foreground)
  const bgRgb = parseColor(background)

  if (!fgRgb || !bgRgb) {
    return null
  }

  const ratio = calculateContrastRatio(fgRgb, bgRgb)

  // 소수점 2자리로 반올림
  const roundedRatio = Math.round(ratio * 100) / 100

  return {
    ratio: roundedRatio,
    levelAA: ratio >= 4.5,
    levelAAA: ratio >= 7,
    levelAALarge: ratio >= 3,
    levelAAALarge: ratio >= 4.5,
  }
}

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * 대비 비율을 문자열로 포맷팅
 */
export function formatContrastRatio(ratio: number): string {
  return `${ratio.toFixed(2)}:1`
}

/**
 * WCAG 레벨을 문자열로 반환
 */
export function getWcagLevel(result: ContrastResult, isLargeText: boolean = false): string {
  if (isLargeText) {
    if (result.levelAAALarge) return 'AAA'
    if (result.levelAALarge) return 'AA'
    return 'Fail'
  }

  if (result.levelAAA) return 'AAA'
  if (result.levelAA) return 'AA'
  return 'Fail'
}

/**
 * 대비 비율에 따른 등급 색상 반환
 */
export function getContrastGradeColor(result: ContrastResult): string {
  if (result.levelAAA) return '#22c55e' // green-500
  if (result.levelAA) return '#3b82f6' // blue-500
  if (result.levelAALarge) return '#f59e0b' // yellow-500
  return '#ef4444' // red-500
}

/**
 * 대비 비율에 따른 배지 텍스트 반환
 */
export function getContrastBadgeText(result: ContrastResult): string {
  if (result.levelAAA) return 'AAA Pass'
  if (result.levelAA) return 'AA Pass'
  if (result.levelAALarge) return 'AA (Large Text)'
  return 'Fail'
}

/**
 * RGB를 HEX로 변환
 */
export function rgbToHex(rgb: RGB): string {
  const toHex = (n: number): string => {
    const hex = Math.max(0, Math.min(255, Math.round(n))).toString(16)
    return hex.length === 1 ? '0' + hex : hex
  }

  return `#${toHex(rgb.r)}${toHex(rgb.g)}${toHex(rgb.b)}`
}

/**
 * 주어진 배경색에서 AA 기준을 만족하는 텍스트 색상 추천
 * 흑백 중 더 적합한 색상 반환
 */
export function suggestTextColor(background: string): string {
  const bgRgb = parseColor(background)
  if (!bgRgb) {
    return '#000000'
  }

  const luminance = getRelativeLuminance(bgRgb)

  // 밝은 배경이면 검정, 어두운 배경이면 흰색
  return luminance > 0.179 ? '#000000' : '#ffffff'
}

/**
 * 주어진 전경색과 배경색 쌍이 AA 기준을 만족하는지 확인
 */
export function meetsAccessibilityStandard(
  foreground: string,
  background: string,
  level: 'AA' | 'AAA' = 'AA',
  isLargeText: boolean = false
): boolean {
  const result = calculateContrast(foreground, background)
  if (!result) return false

  if (isLargeText) {
    return level === 'AAA' ? result.levelAAALarge : result.levelAALarge
  }

  return level === 'AAA' ? result.levelAAA : result.levelAA
}
