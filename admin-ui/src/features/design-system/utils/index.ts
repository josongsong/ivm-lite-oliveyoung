/**
 * Design System Utils - Public API
 *
 * Phase 1 유틸리티들
 */

// Phase 1-G: Contrast Calculator
export {
  calculateContrast,
  calculateContrastRatio,
  getRelativeLuminance,
  hexToRgb,
  parseColor,
  parseRgbString,
  hslToRgb,
  rgbToHex,
  formatContrastRatio,
  getWcagLevel,
  getContrastGradeColor,
  getContrastBadgeText,
  suggestTextColor,
  meetsAccessibilityStandard,
} from './contrastCalculator'

// Phase 1-H: Component Analyzer
export {
  // Analysis functions
  countComponentUsage,
  findComponentImport,
  extractSnippet,
  analyzeFileUsage,
  analyzeComponents,
  // Co-occurrence
  calculateCoOccurrences,
  getTopPairs,
  // Statistics
  aggregateStats,
  findUnusedComponents,
  // Formatting
  formatUsageCount,
  formatFileCount,
  calculateUsageRate,
  formatComponentStats,
  toUsageInfoList,
  // Constants
  SHARED_UI_COMPONENTS,
  // Types
  type ComponentAnalysisResult,
  type FileComponentUsage,
  type AnalyzeOptions,
  type SharedUIComponent,
} from './componentAnalyzer'

// Phase 1-A: Props Extractor
export {
  extractPropsFromComponent,
  getRegisteredComponentNames,
  hasPropsMetadata,
  getRequiredProps,
  getOptionalProps,
  getPropsByType,
} from './propsExtractor'

// Phase 1-B: Code Generator
export {
  generateCodeSnippet,
  generateImportPath,
  getCodeTemplates,
  generateFromTemplate,
  tokenizeJSX,
  type CodeToken,
} from './codeGenerator'

// Phase 1-D: A11y Tester
export {
  testA11y,
  testA11yDetailed,
  testA11yWithRules,
  testColorContrast,
  testKeyboardNavigation,
  testAriaLabels,
  getA11yGrade,
  countIssuesBySeverity,
  getMostCriticalIssue,
} from './a11yTester'

// Phase 1-E: Usage Tracker
export {
  // Pre-computed data management
  setPrecomputedUsageData,
  getComponentUsage,
  getAllComponentStats,
  // Code analysis
  findComponentUsagesInCode,
  isComponentImported,
  extractSharedUIComponents,
  extractUsageSnippet,
  extractFeatureName,
  analyzeCoOccurrences,
  // Statistics
  generateUsageStats,
  getTopUsedComponents,
  findUnusedComponents as findUnusedComponentsFromUsage,
  // Link generation
  generateGitHubLink,
  generateVSCodeLink,
  // Mock data
  mockUsageData,
  // Types
  type UsageSearchOptions,
  type SnippetOptions,
} from './usageTracker'

// Phase 1-F: Design Smell Detector
export {
  detectDesignSmells,
  detectDesignSmellsInFile,
  getAllRules,
  detectWithRule,
  countSmellsBySeverity,
  countSmellsByType,
  getMostSevereSmell,
  calculateCodeQualityScore,
  type DetectionOptions,
} from './designSmellDetector'
