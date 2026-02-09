/**
 * Contracts API 래퍼 (DIP 준수)
 * - fetchApi 추상화 계층
 * - 타입 안정성 보장
 * - API 변경 시 이 파일만 수정
 */
import { fetchApi } from '@/shared/api'
import type {
  ContractListResponse,
  GraphResponse,
  ImpactAnalysisResponse,
} from '@/shared/types'

/**
 * Contracts API (조회용)
 *
 * DX Platform API는 contract-editor feature의 contractEditorApi 사용
 */
export const contractsApi = {
  /** 모든 계약 목록 조회 */
  list: () => fetchApi<ContractListResponse>('/contracts'),

  /** 특정 계약 상세 조회 (캐시에서 추출) */
  getDetail: async (kind: string, id: string) => {
    const response = await fetchApi<ContractListResponse>('/contracts')
    return response.contracts.find(c => c.kind === kind && c.id === id)
  },

  // ==================== Graph API ====================

  /** 전체 Contract Dependency Graph 조회 */
  getGraph: () => fetchApi<GraphResponse>('/contracts/graph'),

  /** 특정 Contract의 Impact Graph (downstream) */
  getImpactGraph: (kind: string, id: string, depth = 2) =>
    fetchApi<GraphResponse>(`/contracts/${kind}/${encodeURIComponent(id)}/graph?depth=${depth}`),

  /** 특정 Contract의 Dependency Graph (upstream) */
  getDependencyGraph: (kind: string, id: string, depth = 2) =>
    fetchApi<GraphResponse>(`/contracts/${kind}/${encodeURIComponent(id)}/dependencies?depth=${depth}`),

  /** 변경 영향 분석 */
  analyzeImpact: (kind: string, id: string) =>
    fetchApi<ImpactAnalysisResponse>(`/contracts/${kind}/${encodeURIComponent(id)}/impact`),
}
