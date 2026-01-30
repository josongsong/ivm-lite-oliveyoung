import { fetchApi, postApi } from '@/shared/api'
import type {
  DiffResult,
  PresetsResponse,
  SimulationResult,
  TryResult,
  ValidationResult,
} from '../types/playground'

export const playgroundApi = {
  /**
   * YAML 검증
   */
  validate: (yaml: string) =>
    postApi<ValidationResult>('/playground/validate', { yaml }),

  /**
   * 샘플 데이터로 슬라이싱 시뮬레이션
   */
  simulate: (yaml: string, sampleData: string) =>
    postApi<SimulationResult>('/playground/simulate', { yaml, sampleData }),

  /**
   * 현재 계약과 비교
   */
  diff: (contractId: string, newYaml: string) =>
    postApi<DiffResult>('/playground/diff', { contractId, newYaml }),

  /**
   * 실제 데이터로 드라이런
   */
  tryOnRealData: (yaml: string, entityKey: string, tenantId = 'oliveyoung') =>
    postApi<TryResult>('/playground/try', { yaml, entityKey, tenantId }),

  /**
   * 프리셋 목록 조회
   */
  getPresets: () => fetchApi<PresetsResponse>('/playground/presets'),
}
