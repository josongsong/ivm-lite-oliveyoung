/**
 * Dashboard API 래퍼 (DIP 준수)
 * - fetchApi 추상화 계층
 * - 타입 안정성 보장
 * - API 변경 시 이 파일만 수정
 */
import { fetchApi } from '@/shared/api'
import type { DashboardResponse, PipelineOverviewResponse } from '@/shared/types'

export const dashboardApi = {
  /** 대시보드 전체 데이터 조회 */
  getDashboard: () =>
    fetchApi<DashboardResponse>('/dashboard'),

  /** 파이프라인 개요 조회 */
  getPipelineOverview: () =>
    fetchApi<PipelineOverviewResponse>('/pipeline/overview'),
}
