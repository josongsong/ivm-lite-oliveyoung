// Playground Types

export interface ValidationError {
  line: number
  column: number
  message: string
  severity: 'error' | 'warning' | 'info'
}

export interface ValidationResult {
  valid: boolean
  errors: ValidationError[]
  warnings: string[]
}

export interface SimulatedSlice {
  type: string
  data: string
  hash: string
  fields: string[]
}

export interface RawDataPreview {
  payload: string
  entityType: string
  sliceCount: number
}

export interface SimulationResult {
  success: boolean
  slices: SimulatedSlice[]
  errors: string[]
  rawDataPreview: RawDataPreview | null
}

export interface DiffItem {
  path: string
  oldValue: string | null
  newValue: string | null
}

export interface DiffResult {
  added: DiffItem[]
  removed: DiffItem[]
  modified: DiffItem[]
  summary: string
}

export interface TryResult {
  success: boolean
  entityKey: string
  version: number
  slices: SimulatedSlice[]
  errors: string[]
}

export interface PresetItem {
  id: string
  name: string
  description: string
  entityType: string
  sampleData: string
  sampleYaml: string
}

export interface PresetsResponse {
  presets: PresetItem[]
}

// Playground State
export interface PlaygroundState {
  yaml: string
  sampleData: string
  validationResult: ValidationResult | null
  simulationResult: SimulationResult | null
  diffResult: DiffResult | null
  isValidating: boolean
  isSimulating: boolean
  selectedPreset: string | null
  contractId: string | null
}
