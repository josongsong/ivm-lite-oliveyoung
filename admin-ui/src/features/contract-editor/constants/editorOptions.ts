/**
 * Performance Budget Constants (Performance Budget from Proposal)
 *
 * | Metric              | Target    |
 * |---------------------|-----------|
 * | Keystroke Response  | < 16ms    |
 * | Validation (L0-L3)  | < 100ms   |
 * | Cursor Context      | < 50ms    |
 * | Semantic Diff       | < 300ms   |
 * | Graph Render        | < 500ms   |
 */
export const PERFORMANCE = {
  // Debounce 설정 (ms)
  DEBOUNCE: {
    CURSOR_CONTEXT: 50,     // 커서 이동은 빠른 피드백 필요
    VALIDATION: 200,         // 타이핑 중 실시간 검증
    SEMANTIC_DIFF: 400,      // Diff는 조금 더 여유있게
    GRAPH_REFETCH: 30_000,   // 그래프는 자주 변경되지 않음
  },
  // TanStack Query staleTime (ms)
  STALE_TIME: {
    GRAPH: 60_000,           // 그래프: 1분
    EXPLANATION: 300_000,    // 설명: 5분 (거의 변하지 않음)
    DESCRIPTORS: 120_000,    // 디스크립터: 2분
  },
  // API 요청 최적화
  REQUEST: {
    MAX_CONCURRENT: 3,       // 동시 요청 제한
    ABORT_TIMEOUT: 5_000,    // 요청 타임아웃
  },
} as const

/**
 * Monaco Editor 기본 옵션
 */
export const MONACO_OPTIONS = {
  fontSize: 14,
  fontFamily: "'JetBrains Mono', monospace",
  minimap: { enabled: false },
  lineNumbers: 'on' as const,
  scrollBeyondLastLine: false,
  wordWrap: 'on' as const,
  tabSize: 2,
  automaticLayout: true,
  renderLineHighlight: 'line' as const,
  cursorBlinking: 'smooth' as const,
}

export const DEFAULT_YAML = `kind: RULESET
id: my_ruleset
version: "1.0.0"
entityType: PRODUCT
slices:
  - type: CORE
    buildRules:
      passThrough: ["id", "name", "price"]
`
