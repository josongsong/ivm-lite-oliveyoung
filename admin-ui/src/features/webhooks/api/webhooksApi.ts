/**
 * Webhooks API
 *
 * 웹훅 관련 API 호출 함수
 */

import { apiClient } from '@/shared/api'
import type {
  CreateWebhookRequest,
  DeliveryListResponse,
  EventListResponse,
  TestResult,
  UpdateWebhookRequest,
  Webhook,
  WebhookListResponse,
  WebhookStats,
} from '../types/webhooks'

const BASE_PATH = '/webhooks'

/**
 * 웹훅 목록 조회
 */
export async function fetchWebhooks(activeOnly = false): Promise<WebhookListResponse> {
  const params = activeOnly ? '?active=true' : ''
  return apiClient.get<WebhookListResponse>(`${BASE_PATH}${params}`)
}

/**
 * 웹훅 상세 조회
 */
export async function fetchWebhook(id: string): Promise<Webhook> {
  return apiClient.get<Webhook>(`${BASE_PATH}/${id}`)
}

/**
 * 웹훅 생성
 */
export async function createWebhook(request: CreateWebhookRequest): Promise<Webhook> {
  return apiClient.post<Webhook>(BASE_PATH, request)
}

/**
 * 웹훅 수정
 */
export async function updateWebhook(id: string, request: UpdateWebhookRequest): Promise<Webhook> {
  return apiClient.put<Webhook>(`${BASE_PATH}/${id}`, request)
}

/**
 * 웹훅 삭제
 */
export async function deleteWebhook(id: string): Promise<{ success: boolean; message: string }> {
  return apiClient.delete(`${BASE_PATH}/${id}`)
}

/**
 * 웹훅 테스트 전송
 */
export async function testWebhook(id: string): Promise<TestResult> {
  return apiClient.post<TestResult>(`${BASE_PATH}/${id}/test`, {})
}

/**
 * 웹훅 통계 조회
 */
export async function fetchWebhookStats(): Promise<WebhookStats> {
  return apiClient.get<WebhookStats>(`${BASE_PATH}/stats`)
}

/**
 * 지원 이벤트 목록 조회
 */
export async function fetchSupportedEvents(): Promise<EventListResponse> {
  return apiClient.get<EventListResponse>(`${BASE_PATH}/events`)
}

/**
 * 웹훅별 전송 기록 조회
 */
export async function fetchDeliveries(webhookId: string, limit = 50): Promise<DeliveryListResponse> {
  return apiClient.get<DeliveryListResponse>(`${BASE_PATH}/${webhookId}/deliveries?limit=${limit}`)
}

/**
 * 최근 전송 기록 조회
 */
export async function fetchRecentDeliveries(limit = 50): Promise<DeliveryListResponse> {
  return apiClient.get<DeliveryListResponse>(`${BASE_PATH}/deliveries/recent?limit=${limit}`)
}

/**
 * Circuit Breaker 상태 조회
 */
export async function fetchCircuitState(webhookId: string): Promise<{ state: string }> {
  return apiClient.get(`${BASE_PATH}/${webhookId}/circuit`)
}

/**
 * Circuit Breaker 리셋
 */
export async function resetCircuit(webhookId: string): Promise<{ success: boolean; state: string }> {
  return apiClient.post(`${BASE_PATH}/${webhookId}/circuit/reset`, {})
}
