/**
 * Design System Data Layer - Public API
 */

// Types
export type {
  // Categories & Stability
  ComponentCategory,
  ComponentStability,

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

  // Decisions
  RelatedDecision,

  // Core
  ComponentMeta,
  ExtractedProp,
  UsageInfo,

  // Design Smell
  DesignSmellType,
  DesignSmellSeverity,
  DesignSmell,

  // Project Settings
  FrameworkType,
  StyleType,
  ImportType,
  ProjectSettings,

  // Statistics
  ComponentStats,
  ContrastResult,

  // Tokens
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

  // Icons
  IconInfo,

  // Health
  HealthMetrics,

  // Changelog
  ChangeType,
  ChangelogEntry,
} from './types'

// Mock Data
export {
  buttonMockData,
  inputMockData,
  selectMockData,
  mockComponents,
  mockColorTokens,
  mockTypographyTokens,
  mockSpacingTokens,
  mockShadowTokens,
  mockMotionTokens,
} from './mockData'

// Component Registry
export {
  componentRegistry,
  // Query by name
  getComponentByName,
  searchComponentsByName,
  // Query by category
  getComponentsByCategory,
  getAllCategories,
  getComponentCountByCategory,
  // Query by stability
  getComponentsByStability,
  getComponentCountByStability,
  // Relationships
  getRelatedComponents,
  // Statistics
  getTotalComponentCount,
  getStableComponentCount,
  getAverageA11yScore,
  getRegistryStats,
  // Utilities
  hasComponent,
  getComponentNamesByCategory,
  getAllComponentNames,
  // Category metadata
  categoryMetadata,
} from './componentRegistry'

// Props Registry
export { COMPONENT_PROPS_REGISTRY } from './propsRegistry'
