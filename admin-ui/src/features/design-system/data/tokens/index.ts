/**
 * Design Tokens - SSOT (Single Source of Truth)
 *
 * 모든 디자인 토큰을 정의하고 여기서 export합니다.
 */

import shadowsData from './shadows.json'
import motionData from './motion.json'
import spacingData from './spacing.json'

// ============================================================================
// Colors - Phase 4-A
// ============================================================================

export {
  primaryColors,
  neutralColors,
  semanticColors,
  accentColors,
  backgroundColors,
  textColors,
  borderColors,
  allColorTokens,
  colorCategories,
} from './colors'

// ============================================================================
// Typography - Phase 4-B
// ============================================================================

export {
  displayTypography,
  headingTypography,
  bodyTypography,
  labelTypography,
  codeTypography,
  fontFamilies,
  fontWeights,
  allTypographyTokens,
  typographyCategories,
} from './typography'
export type { FontFamily, FontWeight } from './typography'

// ============================================================================
// Types
// ============================================================================

export interface SpacingToken {
  value: string
  description: string
}

export interface ShadowToken {
  value: string
  description: string
}

export interface MotionDurationToken {
  value: string
  description: string
}

export interface MotionEasingToken {
  value: string
  description: string
}

export interface MotionPresetToken {
  keyframes: string
  properties: {
    animation: string
  }
  description: string
}

// ============================================================================
// Shadows & Motion Data
// ============================================================================

export const shadows = shadowsData as {
  elevation: Record<string, ShadowToken>
  inner: Record<string, ShadowToken>
  colored: Record<string, ShadowToken>
}

export const motion = motionData as {
  duration: Record<string, MotionDurationToken>
  easing: Record<string, MotionEasingToken>
  presets: Record<string, MotionPresetToken>
}

export const spacing = spacingData as {
  base: Record<string, SpacingToken>
  semantic: Record<string, SpacingToken>
}
