// Data Explorer Types

/** 검색 쿼리 파라미터 */
export interface ExplorerQueryParams {
  tenant: string
  entityId?: string
  entityPrefix?: string
  version?: number | 'latest'
  sliceType?: string
  viewDefId?: string
  limit?: number
}

/** 자동완성 제안 */
export interface SearchSuggestion {
  type: 'tenant' | 'entity' | 'version' | 'sliceType' | 'viewDef'
  value: string
  label: string
  description?: string
}

/** 자동완성 응답 */
export interface AutocompleteResponse {
  suggestions: SearchSuggestion[]
}

/** RawData 아이템 */
export interface RawDataEntry {
  tenant: string
  entityId: string
  version: number
  schemaRef: string
  data: Record<string, unknown>
  createdAt: string
  updatedAt: string
  hash: string
}

/** RawData 조회 응답 */
export interface RawDataResponse {
  entry: RawDataEntry | null
  versions: number[]
  latestVersion: number
}

/** RawData 목록 아이템 (간략 정보) */
export interface RawDataListEntry {
  entityId: string
  version: number
  schemaRef: string
  updatedAt?: string
}

/** RawData 목록 응답 */
export interface RawDataListResponse {
  entries: RawDataListEntry[]
  total: number
  hasMore: boolean
  nextCursor?: string
}

/** Slice 아이템 */
export interface SliceEntry {
  tenant: string
  entityId: string
  sliceType: string
  version: number
  rulesetRef: string
  data: Record<string, unknown>
  sourceRawDataVersion: number
  createdAt: string
  hash: string
}

/** Slice 목록 응답 */
export interface SliceListResponse {
  slices: SliceEntry[]
  total: number
}

/** View 조합 결과 */
export interface ViewResult {
  viewDefId: string
  viewDefRef: string
  entityId: string
  version: number
  assembledData: Record<string, unknown>
  sourceSlices: Array<{
    sliceType: string
    version: number
  }>
  assembledAt: string
}

/** View 조회 응답 */
export interface ViewResponse {
  view: ViewResult | null
  availableViewDefs: string[]
}

/** Lineage 노드 타입 */
export type LineageNodeType = 'rawdata' | 'ruleset' | 'slice' | 'viewdef' | 'view' | 'sink'

/** Lineage 노드 */
export interface LineageNode {
  id: string
  type: LineageNodeType
  label: string
  version?: number
  status: 'success' | 'pending' | 'error'
  metadata?: Record<string, unknown>
}

/** Lineage 엣지 */
export interface LineageEdge {
  source: string
  target: string
  label?: string
}

/** Lineage 그래프 응답 */
export interface LineageResponse {
  nodes: LineageNode[]
  edges: LineageEdge[]
  entityId: string
  tenant: string
}

/** 버전 비교 diff */
export interface VersionDiff {
  path: string
  type: 'added' | 'removed' | 'changed'
  oldValue?: unknown
  newValue?: unknown
}

/** 버전 비교 응답 */
export interface DiffResponse {
  fromVersion: number
  toVersion: number
  diffs: VersionDiff[]
  fromData: Record<string, unknown>
  toData: Record<string, unknown>
}

/** Explorer 탭 타입 */
export type ExplorerTab = 'rawdata' | 'slices' | 'view' | 'lineage' | 'create'

/** 검색 히스토리 아이템 */
export interface SearchHistoryItem {
  query: string
  tenant: string
  entityId: string
  timestamp: number
}

/** 스키마 정보 */
export interface SchemaInfo {
  id: string
  name: string
  version: string
  description?: string
  fields: SchemaField[]
}

/** 스키마 필드 */
export interface SchemaField {
  name: string
  type: 'string' | 'number' | 'boolean' | 'object' | 'array'
  required: boolean
  description?: string
}

/** 스키마 목록 응답 */
export interface SchemaListResponse {
  schemas: SchemaInfo[]
}

/** ViewDefinition 아이템 */
export interface ViewDefinitionEntry {
  id: string
  version: string
  status: string
  requiredSlices: string[]
  fileName: string
}

/** ViewDefinition 목록 응답 */
export interface ViewDefinitionListResponse {
  entries: ViewDefinitionEntry[]
  total: number
}

/** RawData 등록 요청 */
export interface RawDataCreateRequest {
  tenant: string
  entityId: string
  schemaRef: string
  data: Record<string, unknown>
}

/** RawData 등록 응답 */
export interface RawDataCreateResponse {
  success: boolean
  entry: RawDataEntry
  message?: string
}

/** 슬라이스 타입 정보 */
export interface SliceTypeInfo {
  type: string
  count: number
}

/** 슬라이스 타입 목록 응답 */
export interface SliceTypesResponse {
  tenantId: string
  types: SliceTypeInfo[]
  total: number
}

/** 슬라이스 타입별 목록 아이템 */
export interface SliceListItem {
  entityId: string
  sliceType: string
  version: number
  data: Record<string, unknown> | null
  dataRaw: string
  hash: string
  ruleSetId: string
  ruleSetVersion: string
  isDeleted: boolean
  updatedAt: string | null
}

/** 슬라이스 타입별 목록 응답 */
export interface SliceListByTypeResponse {
  tenantId: string
  sliceType: string
  entries: SliceListItem[]
  total: number
  hasMore: boolean
  nextCursor: string | null
}
