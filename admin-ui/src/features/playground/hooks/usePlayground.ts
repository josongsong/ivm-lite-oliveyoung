import { useCallback, useEffect, useRef, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { playgroundApi } from '../api/playgroundApi'
import type {
  DiffResult,
  PresetItem,
  SimulationResult,
  ValidationResult,
} from '../types/playground'

const DEFAULT_YAML = `kind: RULESET
id: my_ruleset
version: "1.0.0"
entityType: PRODUCT
slices:
  - type: CORE
    buildRules:
      passThrough: ["id", "name", "price"]
  - type: SUMMARY
    buildRules:
      mapFields:
        name: displayName
        price: displayPrice
`

const DEFAULT_SAMPLE = `{
  "id": "SKU-001",
  "name": "상품명",
  "price": 10000,
  "brandId": "BRAND-001",
  "isActive": true
}`

export function usePlayground() {
  const [yaml, setYaml] = useState(DEFAULT_YAML)
  const [sampleData, setSampleData] = useState(DEFAULT_SAMPLE)
  const [validationResult, setValidationResult] = useState<ValidationResult | null>(null)
  const [simulationResult, setSimulationResult] = useState<SimulationResult | null>(null)
  const [diffResult, setDiffResult] = useState<DiffResult | null>(null)
  const [contractId, setContractId] = useState<string | null>(null)

  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // 프리셋 목록 조회
  const { data: presetsData } = useQuery({
    queryKey: ['playground-presets'],
    queryFn: playgroundApi.getPresets,
  })

  // YAML 검증 mutation
  const validateMutation = useMutation({
    mutationFn: (yamlContent: string) => playgroundApi.validate(yamlContent),
    onSuccess: (result) => {
      setValidationResult(result)
    },
  })

  // 시뮬레이션 mutation
  const simulateMutation = useMutation({
    mutationFn: ({ yaml, sampleData }: { yaml: string; sampleData: string }) =>
      playgroundApi.simulate(yaml, sampleData),
    onSuccess: (result) => {
      setSimulationResult(result)
    },
  })

  // Diff mutation
  const diffMutation = useMutation({
    mutationFn: ({ contractId, yaml }: { contractId: string; yaml: string }) =>
      playgroundApi.diff(contractId, yaml),
    onSuccess: (result) => {
      setDiffResult(result)
    },
  })

  // 실시간 검증 (300ms debounce)
  const validateMutate = validateMutation.mutate
  useEffect(() => {
    if (debounceRef.current) {
      clearTimeout(debounceRef.current)
    }

    debounceRef.current = setTimeout(() => {
      if (yaml.trim()) {
        validateMutate(yaml)
      }
    }, 300)

    return () => {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current)
      }
    }
  }, [yaml, validateMutate])

  // 시뮬레이션 실행
  const runSimulation = useCallback(() => {
    if (yaml.trim() && sampleData.trim()) {
      simulateMutation.mutate({ yaml, sampleData })
    }
  }, [yaml, sampleData, simulateMutation])

  // Diff 실행
  const runDiff = useCallback(() => {
    if (contractId && yaml.trim()) {
      diffMutation.mutate({ contractId, yaml })
    }
  }, [contractId, yaml, diffMutation])

  // 프리셋 적용
  const applyPreset = useCallback((preset: PresetItem) => {
    setYaml(preset.sampleYaml)
    setSampleData(preset.sampleData)
    setSimulationResult(null)
    setDiffResult(null)
  }, [])

  // 초기화
  const reset = useCallback(() => {
    setYaml(DEFAULT_YAML)
    setSampleData(DEFAULT_SAMPLE)
    setValidationResult(null)
    setSimulationResult(null)
    setDiffResult(null)
    setContractId(null)
  }, [])

  return {
    // State
    yaml,
    sampleData,
    validationResult,
    simulationResult,
    diffResult,
    contractId,
    presets: presetsData?.presets ?? [],

    // Loading states
    isValidating: validateMutation.isPending,
    isSimulating: simulateMutation.isPending,
    isDiffing: diffMutation.isPending,

    // Actions
    setYaml,
    setSampleData,
    setContractId,
    runSimulation,
    runDiff,
    applyPreset,
    reset,
  }
}
