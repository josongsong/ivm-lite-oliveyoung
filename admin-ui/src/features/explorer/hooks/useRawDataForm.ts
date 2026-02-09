import { useCallback, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { explorerApi } from '@/shared/api'
import type { RawDataCreateRequest } from '@/shared/types'
import { generateSampleData } from '../utils/sampleDataGenerator'

interface ValidationResult {
  valid: boolean
  errors?: string[]
}

interface UseRawDataFormOptions {
  defaultTenant?: string
  onSuccess?: (entityId: string) => void
}

export function useRawDataForm({ defaultTenant = 'oliveyoung', onSuccess }: UseRawDataFormOptions) {
  const queryClient = useQueryClient()

  // 폼 상태
  const [tenant, setTenant] = useState(defaultTenant)
  const [entityId, setEntityId] = useState('')
  const [selectedSchema, setSelectedSchema] = useState<string>('')
  const [jsonInput, setJsonInput] = useState('')
  const [showSchemaDropdown, setShowSchemaDropdown] = useState(false)
  const [showPreview, setShowPreview] = useState(false)

  // 유효성 검증 상태
  const [jsonError, setJsonError] = useState<string | null>(null)
  const [validationResult, setValidationResult] = useState<ValidationResult | null>(null)

  // 스키마 목록 조회
  const { data: schemasData, isError: schemasError } = useQuery({
    queryKey: ['schemas', tenant],
    queryFn: () => explorerApi.getSchemas(tenant),
    staleTime: 60000,
    retry: false,
  })

  // 유효성 검증 뮤테이션
  const validateMutation = useMutation({
    mutationFn: async ({ schemaRef, data }: { schemaRef: string; data: Record<string, unknown> }) => {
      try {
        return await explorerApi.validateRawData(schemaRef, data)
      } catch {
        // API 없으면 JSON 파싱 성공 = 유효
        return { valid: true }
      }
    },
    onSuccess: (result) => {
      setValidationResult(result)
    },
  })

  // 등록 뮤테이션
  const createMutation = useMutation({
    mutationFn: async (request: RawDataCreateRequest) => {
      try {
        return await explorerApi.createRawData(request)
      } catch (e) {
        throw new Error(`API 연결 실패: ${(e as Error).message}`)
      }
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['rawdata'] })
      onSuccess?.(result.entry.entityId)
      resetForm()
    },
  })

  // JSON 파싱 및 검증
  const parseAndValidate = useCallback((value: string) => {
    setJsonInput(value)
    setValidationResult(null)

    if (!value.trim()) {
      setJsonError(null)
      return
    }

    try {
      JSON.parse(value)
      setJsonError(null)

      // 스키마 선택되면 서버 검증
      if (selectedSchema) {
        validateMutation.mutate({
          schemaRef: selectedSchema,
          data: JSON.parse(value),
        })
      }
    } catch (e) {
      setJsonError((e as Error).message)
    }
  }, [selectedSchema, validateMutation])

  const formatJson = useCallback(() => {
    try { const parsed = JSON.parse(jsonInput); setJsonInput(JSON.stringify(parsed, null, 2)); setJsonError(null) }
    catch (e) { setJsonError((e as Error).message) }
  }, [jsonInput])

  // 샘플 데이터 생성
  const handleGenerateSample = useCallback(() => {
    const schema = schemasData?.schemas?.find(s => s.id === selectedSchema)

    if (!schema || !schema.fields || schema.fields.length === 0) {
      console.warn('스키마를 찾을 수 없거나 필드 정보가 없습니다:', selectedSchema)
      return
    }

    const sample = generateSampleData(schema)
    setJsonInput(JSON.stringify(sample, null, 2))
    setJsonError(null)

    // Entity ID도 자동 생성
    if (!entityId) {
      const sku = (sample.sku || sample.id || sample.entityId) as string
      if (sku) setEntityId(sku)
    }
  }, [schemasData, selectedSchema, entityId])

  // 폼 초기화
  const resetForm = useCallback(() => {
    setEntityId('')
    setJsonInput('')
    setSelectedSchema('')
    setJsonError(null)
    setValidationResult(null)
  }, [])

  // 제출
  const handleSubmit = useCallback(() => {
    if (!entityId || !selectedSchema || !jsonInput || jsonError) return

    try {
      const data = JSON.parse(jsonInput)
      createMutation.mutate({
        tenant,
        entityId,
        schemaRef: selectedSchema,
        data,
      })
    } catch {
      // 이미 jsonError로 처리됨
    }
  }, [entityId, selectedSchema, jsonInput, jsonError, tenant, createMutation])

  // 파싱된 데이터 미리보기 (안전하게)
  const parsedData = (() => {
    if (!jsonInput || jsonError) return null
    try {
      return JSON.parse(jsonInput)
    } catch {
      return null
    }
  })()

  const isFormValid = !!(entityId && selectedSchema && jsonInput && !jsonError)

  // 파일 업로드 시 entityId 설정
  const handleFileParsed = useCallback((content: string, fileName: string) => {
    parseAndValidate(content)

    // 파일명에서 entityId 추출 시도
    const nameWithoutExt = fileName.replace(/\.json$/i, '')
    if (!entityId) {
      setEntityId(nameWithoutExt)
    }
  }, [entityId, parseAndValidate])

  return {
    // 상태
    tenant,
    entityId,
    selectedSchema,
    jsonInput,
    showSchemaDropdown,
    showPreview,
    jsonError,
    validationResult,
    schemasData,
    schemasError,
    createMutation,
    validateMutation,
    parsedData,
    isFormValid,

    // 상태 변경 함수
    setTenant,
    setEntityId,
    setSelectedSchema,
    setJsonInput,
    setShowSchemaDropdown,
    setShowPreview,
    setJsonError,

    // 액션
    parseAndValidate,
    formatJson,
    handleGenerateSample,
    resetForm,
    handleSubmit,
    handleFileParsed,
  }
}
