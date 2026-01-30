import { fetchApi } from './client'
import type {
  ServiceMapResult,
  TraceDetail,
  TraceFilterOptions,
  TraceListResult,
} from '../types'

/**
 * Trace API 클라이언트
 */
export const tracesApi = {
  /**
   * 트레이스 목록 조회
   */
  async getTraces(options: TraceFilterOptions = {}): Promise<TraceListResult> {
    const params = new URLSearchParams()
    
    if (options.startTime) params.set('startTime', options.startTime)
    if (options.endTime) params.set('endTime', options.endTime)
    if (options.serviceName) params.set('serviceName', options.serviceName)
    if (options.limit) params.set('limit', options.limit.toString())
    if (options.nextToken) params.set('nextToken', options.nextToken)

    const queryString = params.toString()
    const endpoint = `/traces${queryString ? `?${queryString}` : ''}`
    
    return fetchApi<TraceListResult>(endpoint)
  },

  /**
   * 트레이스 상세 조회
   */
  async getTraceDetail(traceId: string): Promise<TraceDetail> {
    return fetchApi<TraceDetail>(`/traces/${traceId}`)
  },

  /**
   * 서비스 맵 조회
   */
  async getServiceMap(options: {
    startTime?: string
    endTime?: string
    serviceName?: string
  } = {}): Promise<ServiceMapResult> {
    const params = new URLSearchParams()
    
    if (options.startTime) params.set('startTime', options.startTime)
    if (options.endTime) params.set('endTime', options.endTime)
    if (options.serviceName) params.set('serviceName', options.serviceName)

    const queryString = params.toString()
    const endpoint = `/traces/service-map${queryString ? `?${queryString}` : ''}`
    
    return fetchApi<ServiceMapResult>(endpoint)
  },
}
