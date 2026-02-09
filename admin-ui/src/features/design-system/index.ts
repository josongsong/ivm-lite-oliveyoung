/**
 * Design System Catalog Feature - Public API
 *
 * 이 모듈은 Design System 문서화 및 컴포넌트 카탈로그 기능을 제공합니다.
 *
 * @example
 * ```tsx
 * import { DesignSystemPage, ComponentMeta, mockComponents } from '@/features/design-system'
 * ```
 */

// ============================================================================
// Types (Phase 0 - 즉시 사용 가능)
// ============================================================================
export type {
  // Core types
  ComponentCategory,
  ComponentStability,
  ComponentMeta,
  ExtractedProp,

  // Controls
  ControlType,
  ControlDefinition,

  // Examples & Patterns
  ComponentExample,
  AntiPattern,

  // Accessibility
  A11ySeverity,
  A11yIssue,
  A11yScore,
  A11yResult,

  // Usage & Analysis
  UsageInfo,
  DesignSmellType,
  DesignSmellSeverity,
  DesignSmell,
  ComponentStats,
  ContrastResult,

  // Project Settings
  FrameworkType,
  StyleType,
  ImportType,
  ProjectSettings,

  // Design Tokens
  ColorToken,
  TypographyToken,
  SpacingToken,
  ShadowToken,
  MotionToken,

  // Search
  SearchResultItem,
  SearchFilters,

  // Playground
  PlaygroundState,

  // Misc
  RelatedDecision,
  IconInfo,
  HealthMetrics,
  ChangeType,
  ChangelogEntry,
} from './data/types'

// ============================================================================
// Mock Data (Phase 0 - 즉시 사용 가능)
// ============================================================================
export {
  // Component Mock Data
  buttonMockData,
  inputMockData,
  selectMockData,
  mockComponents,

  // Design Token Mock Data
  mockColorTokens,
  mockTypographyTokens,
  mockSpacingTokens,
  mockShadowTokens,
  mockMotionTokens,
} from './data/mockData'

// ============================================================================
// UI Pages (Phase 2-A)
// ============================================================================
export { DesignSystemPage } from './ui/DesignSystemPage'
export { ComponentDetail } from './ui/ComponentDetail'
export { ComponentCategory as ComponentCategoryPage } from './ui/ComponentCategory'
export { FeatureCategory as FeatureCategoryPage } from './ui/FeatureCategory'
export { FoundationsSection } from './ui/FoundationsSection'
export { PatternGuide } from './ui/PatternGuide'
export { ResourcePage } from './ui/ResourcePage'

// ============================================================================
// Layout Components (Phase 2-B)
// ============================================================================
export { Sidebar, ContentArea, Header, PageHeader } from './components/layout'

// ============================================================================
// Hooks (Phase 1+ 에서 구현 예정)
// ============================================================================
// export { usePlayground } from './hooks/usePlayground'
// export { useClipboard } from './hooks/useClipboard'
// export { useSearch } from './hooks/useSearch'
// export { useCodeGenerator } from './hooks/useCodeGenerator'

// ============================================================================
// Utils (Phase 1+ 에서 구현 예정)
// ============================================================================
// export { extractPropsFromComponent } from './utils/propsExtractor'
// export { generateCodeSnippet } from './utils/codeGenerator'
// export { calculateContrast } from './utils/contrastCalculator'
// export { testA11y } from './utils/a11yTester'
