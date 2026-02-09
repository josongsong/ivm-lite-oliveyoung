/**
 * useWhyEngine Hook (Phase 3: Why Engine)
 *
 * "왜 안 됐지?" 질문에 답변하는 Why Engine을 위한 훅.
 * - Cause Chain 분석
 * - Contract 설명 조회
 * - 긴 staleTime으로 설명 캐싱
 */
import { useCallback, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { contractEditorApi } from '../api'
import { PERFORMANCE } from '../constants/editorOptions'
import type {
  ContractExplanationResponse,
  WhyExplanationResponse,
} from '../api/types'

interface UseWhyEngineOptions {
  contractKind?: string
  contractId?: string
  enabled?: boolean  // 탭 활성화 시에만 fetch
}

interface UseWhyEngineResult {
  // 실패 원인 분석
  explanation: WhyExplanationResponse | null
  isAnalyzing: boolean
  analyzeFailure: (symptom: string) => void

  // Contract 설명
  contractExplanation: ContractExplanationResponse | undefined
  isLoadingExplanation: boolean

  // 상태
  error: Error | null
  reset: () => void
}

export function useWhyEngine(
  options: UseWhyEngineOptions = {}
): UseWhyEngineResult {
  const { contractKind, contractId, enabled = true } = options
  const [explanation, setExplanation] = useState<WhyExplanationResponse | null>(
    null
  )
  const [error, setError] = useState<Error | null>(null)

  // 실패 원인 분석 mutation
  const analyzeFailureMutation = useMutation({
    mutationFn: async (symptom: string) => {
      if (!contractId) {
        throw new Error('Contract ID is required')
      }
      return contractEditorApi.why({
        contractId,
        symptom,
      })
    },
    onSuccess: (data) => {
      setExplanation(data)
      setError(null)
    },
    onError: (err: Error) => {
      setError(err)
    },
  })

  // Contract 설명 조회 query (거의 변하지 않으므로 긴 캐싱)
  const contractExplanationQuery = useQuery({
    queryKey: ['contract-explanation', contractKind, contractId],
    queryFn: () => contractEditorApi.explain(contractKind!, contractId!),
    enabled: enabled && !!contractKind && !!contractId,
    staleTime: PERFORMANCE.STALE_TIME.EXPLANATION,
    gcTime: PERFORMANCE.STALE_TIME.EXPLANATION * 2,
  })

  const analyzeFailure = useCallback(
    (symptom: string) => {
      analyzeFailureMutation.mutate(symptom)
    },
    [analyzeFailureMutation]
  )

  const reset = useCallback(() => {
    setExplanation(null)
    setError(null)
    analyzeFailureMutation.reset()
  }, [analyzeFailureMutation])

  return {
    // 실패 원인 분석
    explanation,
    isAnalyzing: analyzeFailureMutation.isPending,
    analyzeFailure,

    // Contract 설명
    contractExplanation: contractExplanationQuery.data,
    isLoadingExplanation: contractExplanationQuery.isLoading,

    // 상태
    error: error || (contractExplanationQuery.error as Error | null),
    reset,
  }
}
