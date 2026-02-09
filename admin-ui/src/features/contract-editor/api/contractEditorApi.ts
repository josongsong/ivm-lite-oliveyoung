/**
 * Contract Editor API (Phase 1: DX Platform)
 */
import { fetchApi, postApi } from '@/shared/api'
import type {
  ContractDiffRequest,
  ContractExplanationResponse,
  ContractGraphResponse,
  ContractImpactResponse,
  ContractValidateRequest,
  ContractValidationResponse,
  CursorContextRequest,
  CursorContextResponse,
  ExportPatchRequest,
  ExportPatchResponse,
  ExportPRRequest,
  ExportPRResponse,
  SampleDataResponse,
  SemanticDiffResponse,
  SimulateRequest,
  SimulationResultResponse,
  WhyExplanationResponse,
  WhyRequest,
} from './types'

export const contractEditorApi = {
  /**
   * 커서 컨텍스트 조회 (Meaning Panel용)
   */
  getCursorContext: (request: CursorContextRequest) =>
    postApi<CursorContextResponse>('/contracts/cursor-context', request),

  /**
   * 다단계 검증 (L0-L3)
   */
  validate: (request: ContractValidateRequest) =>
    postApi<ContractValidationResponse>('/contracts/validate', request),

  /**
   * 의미론적 Diff (Phase 2)
   */
  diff: (request: ContractDiffRequest) =>
    postApi<SemanticDiffResponse>('/contracts/diff', request),

  /**
   * Why Engine - 실패 원인 분석 (Phase 3)
   */
  why: (request: WhyRequest) =>
    postApi<WhyExplanationResponse>('/contracts/why', request),

  /**
   * Contract 설명 조회 (Phase 3)
   */
  explain: (kind: string, id: string) =>
    fetchApi<ContractExplanationResponse>(
      `/contracts/${kind}/${encodeURIComponent(id)}/explain`
    ),

  /**
   * 전체 Contract 그래프 조회 (Phase 4)
   */
  getGraph: () => fetchApi<ContractGraphResponse>('/contracts/graph'),

  /**
   * 특정 Contract의 영향 그래프 조회 (Phase 4)
   */
  getImpactGraph: (kind: string, id: string) =>
    fetchApi<ContractGraphResponse>(
      `/contracts/${kind}/${encodeURIComponent(id)}/graph`
    ),

  /**
   * 변경 영향 분석 (Phase 4)
   */
  getImpact: (kind: string, id: string) =>
    fetchApi<ContractImpactResponse>(
      `/contracts/${kind}/${encodeURIComponent(id)}/impact`
    ),

  /**
   * 파이프라인 시뮬레이션 (Phase 5)
   */
  simulate: (request: SimulateRequest) =>
    postApi<SimulationResultResponse>('/contracts/simulate', request),

  /**
   * 샘플 데이터 생성 (Phase 5)
   */
  generateSample: (kind: string, id: string) =>
    postApi<SampleDataResponse>(
      `/contracts/${kind}/${encodeURIComponent(id)}/sample`,
      {}
    ),

  /**
   * Unified Patch 내보내기 (Phase 6)
   */
  exportPatch: (request: ExportPatchRequest) =>
    postApi<ExportPatchResponse>('/contracts/export/patch', request),

  /**
   * PR 설명 템플릿 생성 (Phase 6)
   */
  exportPR: (request: ExportPRRequest) =>
    postApi<ExportPRResponse>('/contracts/export/pr', request),
}
