import { fetchApi, postApi } from './client'
import type {
  AutocompleteResponse,
  DiffResponse,
  ExplorerQueryParams,
  LineageResponse,
  RawDataCreateRequest,
  RawDataCreateResponse,
  RawDataListResponse,
  RawDataResponse,
  SchemaField,
  SchemaListResponse,
  SliceListByTypeResponse,
  SliceListResponse,
  SliceTypesResponse,
  ViewDefinitionListResponse,
  ViewResponse,
} from '@/shared/types'

/** 백엔드 필드 타입 → 프론트엔드 타입 매핑 */
function mapFieldType(backendType: string): SchemaField['type'] {
  const type = backendType.toLowerCase()
  if (type.includes('string')) return 'string'
  if (type.includes('int') || type.includes('long') || type.includes('number') || type.includes('float') || type.includes('double')) return 'number'
  if (type.includes('bool')) return 'boolean'
  if (type.includes('list') || type.includes('array')) return 'array'
  if (type.includes('map') || type.includes('object')) return 'object'
  return 'string' // 기본값
}

/** Data Explorer API 클라이언트 */
export const explorerApi = {
  /** 자동완성 제안 조회 */
  getAutocomplete: async (
    query: string,
    tenant?: string
  ): Promise<AutocompleteResponse> => {
    const params = new URLSearchParams({ query })
    if (tenant) params.set('tenant', tenant)
    return fetchApi(`/query/autocomplete?${params}`)
  },

  /** RawData 단건 조회 */
  getRawData: async (
    tenant: string,
    entityId: string,
    version?: number | 'latest'
  ): Promise<RawDataResponse> => {
    const params = new URLSearchParams({ tenant, entityId })
    if (version && version !== 'latest') params.set('version', String(version))

    // 백엔드 응답 형식: { tenantId, entityKey, version, schemaId, schemaVersion, payload, payloadRaw, payloadHash, versions: [] }
    interface BackendRawDataResponse {
      tenantId: string
      entityKey: string
      version: number
      schemaId: string
      schemaVersion: string
      payload: Record<string, unknown> | null
      payloadRaw: string
      payloadHash: string
      versions: Array<{ version: number; createdAt?: string; hash: string }>
    }

    const response = await fetchApi<BackendRawDataResponse>(`/query/raw?${params}`)

    // 프론트엔드 RawDataResponse 형식으로 변환
    const versionNumbers = response.versions.map(v => v.version)
    if (!versionNumbers.includes(response.version)) {
      versionNumbers.push(response.version)
    }
    versionNumbers.sort((a, b) => b - a) // 내림차순 정렬

    return {
      entry: {
        tenant: response.tenantId,
        entityId: response.entityKey,
        version: response.version,
        schemaRef: `${response.schemaId}@${response.schemaVersion}`,
        data: response.payload ?? {},
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        hash: response.payloadHash,
      },
      versions: versionNumbers,
      latestVersion: Math.max(...versionNumbers, response.version),
    }
  },

  /** RawData 목록 조회 (prefix 검색) */
  listRawData: async (
    tenant: string,
    entityPrefix?: string,
    limit = 50,
    cursor?: string
  ): Promise<RawDataListResponse> => {
    const params = new URLSearchParams({ tenant, limit: String(limit) })
    if (entityPrefix) params.set('entityPrefix', entityPrefix)
    if (cursor) params.set('cursor', cursor)
    return fetchApi(`/query/raw/list?${params}`)
  },

  /** Slice 목록 조회 */
  getSlices: async (
    tenant: string,
    entityId: string,
    sliceType?: string
  ): Promise<SliceListResponse> => {
    const params = new URLSearchParams({ tenant, entityId })
    if (sliceType) params.set('sliceType', sliceType)
    return fetchApi(`/query/slices?${params}`)
  },

  /** 사용 가능한 슬라이스 타입 목록 조회 */
  getSliceTypes: async (tenant: string): Promise<SliceTypesResponse> => {
    const params = new URLSearchParams({ tenant })
    return fetchApi(`/query/slices/types?${params}`)
  },

  /** 슬라이스 타입별 전체 목록 조회 */
  listSlicesByType: async (
    tenant: string,
    sliceType: string,
    limit = 50,
    cursor?: string
  ): Promise<SliceListByTypeResponse> => {
    const params = new URLSearchParams({ tenant, sliceType, limit: String(limit) })
    if (cursor) params.set('cursor', cursor)
    return fetchApi(`/query/slices/list?${params}`)
  },

  /** View 조회 */
  getView: async (
    tenant: string,
    entityId: string,
    viewDefId?: string
  ): Promise<ViewResponse> => {
    const params = new URLSearchParams({ tenant, entityId })
    if (viewDefId) params.set('viewDefId', viewDefId)
    return fetchApi(`/query/view?${params}`)
  },

  /** Lineage 그래프 조회 */
  getLineage: async (
    tenant: string,
    entityId: string
  ): Promise<LineageResponse> => {
    const params = new URLSearchParams({ tenant, entityId })
    return fetchApi(`/query/lineage?${params}`)
  },

  /** 버전 비교 diff */
  getDiff: async (
    tenant: string,
    entityId: string,
    fromVersion: number,
    toVersion: number
  ): Promise<DiffResponse> => {
    const params = new URLSearchParams({
      tenant,
      entityId,
      fromVersion: String(fromVersion),
      toVersion: String(toVersion),
    })
    return fetchApi(`/query/diff?${params}`)
  },

  /** 통합 검색 */
  search: async (params: ExplorerQueryParams): Promise<RawDataListResponse> => {
    const urlParams = new URLSearchParams()
    urlParams.set('tenant', params.tenant)
    // q 파라미터: entityId 또는 entityPrefix를 검색어로 사용
    const query = params.entityId || params.entityPrefix || ''
    if (query) urlParams.set('q', query)
    if (params.limit) urlParams.set('limit', String(params.limit))

    // 백엔드 응답: { query, items: [{entityKey, tenantId, type}], count }
    interface SearchApiResponse {
      query: string
      items: Array<{ entityKey: string; tenantId: string; type: string }>
      count: number
    }

    const response = await fetchApi<SearchApiResponse>(`/query/search?${urlParams}`)

    // RawDataListResponse 형식으로 변환 (RawDataListEntry 사용)
    return {
      entries: response.items.map(item => ({
        entityId: item.entityKey,
        version: 1, // search에서는 버전 정보 없음
        schemaRef: item.type,
        updatedAt: undefined,
      })),
      total: response.count,
      hasMore: false,
      nextCursor: undefined,
    }
  },

  /** 스키마 목록 조회 (백엔드 ContractListResponse → 프론트엔드 SchemaListResponse 변환) */
  getSchemas: async (_tenant?: string): Promise<SchemaListResponse> => {
    // 백엔드 응답 형식: { contracts: [...], total: N }
    interface ContractResponse {
      kind: string
      id: string
      version: string
      status: string
      fileName: string
      content: string
      parsed: {
        fields?: Array<{
          name: string
          type: string
          required?: boolean
          description?: string
        }>
        [key: string]: unknown
      }
    }
    interface BackendResponse {
      contracts: ContractResponse[]
      total: number
    }

    const response = await fetchApi<BackendResponse>('/contracts/schemas')

    // 프론트엔드 SchemaInfo 형식으로 변환
    const schemas = response.contracts.map(contract => ({
      id: contract.id,
      name: contract.id.replace(/\./g, ' ').replace(/v\d+$/, '').trim() || contract.id,
      version: contract.version,
      description: `${contract.status} - ${contract.fileName}`,
      fields: (contract.parsed.fields || []).map(field => ({
        name: field.name,
        type: mapFieldType(field.type),
        required: field.required ?? false,
        description: field.description,
      })),
    }))

    return { schemas }
  },

  /** ViewDefinition 목록 조회 */
  getViewDefinitions: async (): Promise<ViewDefinitionListResponse> => {
    interface ContractResponse {
      kind: string
      id: string
      version: string
      status: string
      fileName: string
      content: string
      parsed: {
        requiredSlices?: string[]
        outputSchema?: Record<string, unknown>
        [key: string]: unknown
      }
    }
    interface BackendResponse {
      contracts: ContractResponse[]
      total: number
    }

    const response = await fetchApi<BackendResponse>('/contracts/views')

    return {
      entries: response.contracts.map(contract => ({
        id: contract.id,
        version: contract.version,
        status: contract.status,
        requiredSlices: contract.parsed.requiredSlices || [],
        fileName: contract.fileName,
      })),
      total: response.total,
    }
  },

  /** RawData 등록 */
  createRawData: async (request: RawDataCreateRequest): Promise<RawDataCreateResponse> => {
    return postApi('/query/ingest', request)
  },

  /** RawData JSON 유효성 검증 (로컬 검증 - 백엔드 API 미구현) */
  validateRawData: async (
    _schemaRef: string,
    data: Record<string, unknown>
  ): Promise<{ valid: boolean; errors?: string[] }> => {
    // 로컬 JSON 구조 검증 (백엔드 validate 엔드포인트 미구현)
    try {
      if (typeof data !== 'object' || data === null) {
        return { valid: false, errors: ['데이터가 객체 형식이어야 합니다'] }
      }
      return { valid: true }
    } catch {
      return { valid: false, errors: ['유효하지 않은 데이터 형식'] }
    }
  },
}
