/**
 * Contract Editor API Types (Phase 1: DX Platform)
 */

// ==================== Cursor Context ====================

export interface CursorContextRequest {
  yaml: string
  line: number
  column: number
}

export interface CursorContextResponse {
  line: number
  column: number
  astPath: string[]
  semanticNode: SemanticNode | null
}

export interface SemanticNode {
  type: SemanticNodeType
  name: string
  dataType: string | null
  required: boolean | null
  description: string | null
  usedBy: UsageInfo
  impact: ImpactInfo
}

export type SemanticNodeType =
  | 'FIELD'
  | 'SLICE'
  | 'SLICE_REF'
  | 'VIEW'
  | 'RULE'
  | 'IMPACT_MAP'
  | 'PROPERTY'

export interface UsageInfo {
  ruleSets: DependencyRef[]
  views: DependencyRef[]
  sinks: DependencyRef[]
}

export interface ImpactInfo {
  slicesAffected: string[]
  regenRequired: boolean
}

export interface DependencyRef {
  id: string
  kind: string
  relation: string
}

// ==================== Validation ====================

export interface ContractValidateRequest {
  yaml: string
}

export interface ContractValidationResponse {
  valid: boolean
  errors: ValidationError[]
  warnings: string[]
}

export interface ValidationError {
  level: ValidationLevel
  line: number
  column: number
  message: string
  fix: QuickFix | null
}

export type ValidationLevel =
  | 'L0_SYNTAX'
  | 'L1_SHAPE'
  | 'L2_SEMANTIC'
  | 'L3_CROSS_REF'
  | 'L4_RUNTIME'

export interface QuickFix {
  description: string
  replacement: string
}

// ==================== Semantic Diff ====================

export interface ContractDiffRequest {
  before: string
  after: string
}

export interface SemanticDiffResponse {
  changes: SemanticChange[]
  breaking: boolean
  regenRequired: boolean
  affectedSlices: string[]
  affectedViews: string[]
}

export interface SemanticChange {
  type: ChangeType
  target: string
  before: string | null
  after: string | null
  breaking: boolean
}

export type ChangeType =
  | 'PROPERTY_ADDED'
  | 'PROPERTY_REMOVED'
  | 'PROPERTY_CHANGED'
  | 'FIELD_ADDED'
  | 'FIELD_REMOVED'
  | 'FIELD_TYPE_CHANGED'
  | 'FIELD_REQUIRED_CHANGED'
  | 'SLICE_ADDED'
  | 'SLICE_REMOVED'
  | 'RULE_CHANGED'
  | 'IMPACT_MAP_CHANGED'
  | 'SLICE_REF_ADDED'
  | 'SLICE_REF_REMOVED'
  | 'SLICE_REF_PROMOTED'
  | 'SLICE_REF_DEMOTED'

// ==================== Semantic Diff Result (for hooks) ====================

export interface SemanticDiffResult {
  changes: SemanticChange[]
  breaking: boolean
  regenRequired: boolean
  affectedSlices: string[]
  affectedViews: string[]
}

// ==================== Why Engine ====================

export interface WhyRequest {
  contractId: string
  symptom: string
}

export interface WhyExplanationResponse {
  symptom: string
  causeChain: CauseResponse[]
  lastEvaluated: string | null
}

export interface CauseResponse {
  order: number
  description: string
  expected: string | null
  actual: string | null
  relatedContract: ContractRefResponse | null
  fixSuggestion: string | null
}

export interface ContractRefResponse {
  id: string
  kind: string
}

export interface ContractExplanationResponse {
  id: string
  kind: string
  summary: string
  purpose: string
  inputs: string[]
  outputs: string[]
  dependencies: ContractRefResponse[]
  dependents: ContractRefResponse[]
}

// ==================== Impact Graph ====================

export interface ContractGraphResponse {
  nodes: GraphNodeResponse[]
  edges: GraphEdgeResponse[]
}

export interface GraphNodeResponse {
  id: string
  kind: string
  label: string
  entityType: string | null
  layer: number
  status: string
  metadata: Record<string, string>
}

export interface GraphEdgeResponse {
  id: string
  from: string
  to: string
  kind: string
  label: string | null
}

export interface ContractImpactResponse {
  contractId: string
  affectedContracts: ContractRefResponse[]
  affectedSlices: string[]
  breaking: boolean
}

// ==================== Simulation (Phase 5) ====================

export interface SimulateRequest {
  yaml: string
  sampleData: string
}

export interface SimulationResultResponse {
  stages: SimulationStageResponse[]
  finalOutput: string | null
  errors: SimulationErrorResponse[]
}

export interface SimulationStageResponse {
  name: string
  status: StageStatus
  output: string | null
  durationMs: number
}

export type StageStatus = 'SUCCESS' | 'PARTIAL' | 'FAILED'

export interface SimulationErrorResponse {
  stage: string
  message: string
  line: number | null
}

export interface SampleDataResponse {
  contractId: string
  contractKind: string
  data: string
  fields: string[]
}

// ==================== Export (Phase 6) ====================

export interface ExportPatchRequest {
  before: string
  after: string
  filePath: string
}

export interface ExportPatchResponse {
  filePath: string
  patch: string
  additions: number
  deletions: number
}

export interface ExportPRRequest {
  before: string
  after: string
  contractId: string
  contractKind: string
}

export interface ExportPRResponse {
  title: string
  body: string
  labels: string[]
}
