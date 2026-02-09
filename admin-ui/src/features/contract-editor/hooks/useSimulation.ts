/**
 * useSimulation Hook (Phase 5: Simulation)
 *
 * Contract YAML과 샘플 데이터로 파이프라인 시뮬레이션을 수행하는 훅.
 * - 단계별 결과 시각화
 * - 샘플 데이터 자동 생성
 */
import { useCallback, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { contractEditorApi } from '../api'
import type {
  SampleDataResponse,
  SimulationResultResponse,
  StageStatus,
} from '../api/types'

interface UseSimulationOptions {
  contractKind?: string
  contractId?: string
}

interface UseSimulationResult {
  // 시뮬레이션 결과
  result: SimulationResultResponse | null
  isSimulating: boolean
  simulate: (yaml: string, sampleData: string) => void

  // 샘플 데이터
  sample: SampleDataResponse | null
  isGeneratingSample: boolean
  generateSample: () => void

  // 상태
  error: Error | null
  reset: () => void
}

export function useSimulation(
  options: UseSimulationOptions = {}
): UseSimulationResult {
  const { contractKind, contractId } = options
  const [result, setResult] = useState<SimulationResultResponse | null>(null)
  const [sample, setSample] = useState<SampleDataResponse | null>(null)
  const [error, setError] = useState<Error | null>(null)

  // 시뮬레이션 mutation
  const simulateMutation = useMutation({
    mutationFn: async ({
      yaml,
      sampleData,
    }: {
      yaml: string
      sampleData: string
    }) => {
      return contractEditorApi.simulate({ yaml, sampleData })
    },
    onSuccess: (data) => {
      setResult(data)
      setError(null)
    },
    onError: (err: Error) => {
      setError(err)
    },
  })

  // 샘플 생성 mutation
  const generateSampleMutation = useMutation({
    mutationFn: async () => {
      if (!contractKind || !contractId) {
        throw new Error('Contract kind and id are required')
      }
      return contractEditorApi.generateSample(contractKind, contractId)
    },
    onSuccess: (data) => {
      setSample(data)
      setError(null)
    },
    onError: (err: Error) => {
      setError(err)
    },
  })

  const simulate = useCallback(
    (yaml: string, sampleData: string) => {
      simulateMutation.mutate({ yaml, sampleData })
    },
    [simulateMutation]
  )

  const generateSample = useCallback(() => {
    generateSampleMutation.mutate()
  }, [generateSampleMutation])

  const reset = useCallback(() => {
    setResult(null)
    setSample(null)
    setError(null)
    simulateMutation.reset()
    generateSampleMutation.reset()
  }, [simulateMutation, generateSampleMutation])

  return {
    // 시뮬레이션 결과
    result,
    isSimulating: simulateMutation.isPending,
    simulate,

    // 샘플 데이터
    sample,
    isGeneratingSample: generateSampleMutation.isPending,
    generateSample,

    // 상태
    error,
    reset,
  }
}

// Stage 상태 헬퍼
export function getStageStatusColor(status: StageStatus): string {
  switch (status) {
    case 'SUCCESS':
      return 'var(--success)'
    case 'PARTIAL':
      return 'var(--warning)'
    case 'FAILED':
      return 'var(--error)'
    default:
      return 'var(--text-secondary)'
  }
}

export function getStageStatusIcon(status: StageStatus): string {
  switch (status) {
    case 'SUCCESS':
      return '✓'
    case 'PARTIAL':
      return '⚠'
    case 'FAILED':
      return '✕'
    default:
      return '○'
  }
}
