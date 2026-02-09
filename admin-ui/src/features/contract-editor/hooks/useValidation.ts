/**
 * useValidation Hook (Phase 1: DX Platform)
 *
 * YAML 변경 시 실시간 다단계 검증.
 * - debounce 적용으로 API 호출 최적화
 * - AbortController로 stale 요청 취소
 * - 연속 타이핑 시 불필요한 요청 방지
 */
import { useCallback, useEffect, useRef, useState } from 'react'
import { contractEditorApi } from '../api'
import type { ContractValidationResponse } from '../api/types'
import { PERFORMANCE } from '../constants/editorOptions'

interface UseValidationOptions {
  enabled?: boolean
  debounceMs?: number
}

interface UseValidationResult {
  validation: ContractValidationResponse | null
  isLoading: boolean
  error: Error | null
  validate: (yaml: string) => void
}

export function useValidation({
  enabled = true,
  debounceMs = PERFORMANCE.DEBOUNCE.VALIDATION,
}: UseValidationOptions = {}): UseValidationResult {
  const [validation, setValidation] = useState<ContractValidationResponse | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<Error | null>(null)

  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const abortControllerRef = useRef<AbortController | null>(null)
  const lastYamlRef = useRef<string>('')

  const validate = useCallback(
    (yaml: string) => {
      if (!enabled) {
        setValidation(null)
        return
      }

      // 빈 YAML은 검증하지 않음
      const trimmed = yaml.trim()
      if (!trimmed) {
        setValidation({ valid: true, errors: [], warnings: [] })
        setIsLoading(false)
        return
      }

      // 동일한 YAML이면 스킵 (불필요한 API 호출 방지)
      if (lastYamlRef.current === trimmed) {
        return
      }

      // 이전 타이머 취소
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current)
        timeoutRef.current = null
      }

      // 이전 요청 취소
      if (abortControllerRef.current) {
        abortControllerRef.current.abort()
        abortControllerRef.current = null
      }

      // debounce 시작 시 즉시 loading 표시 (UX 개선)
      setIsLoading(true)

      timeoutRef.current = setTimeout(async () => {
        lastYamlRef.current = trimmed
        abortControllerRef.current = new AbortController()
        const signal = abortControllerRef.current.signal

        try {
          const result = await contractEditorApi.validate({ yaml: trimmed })

          // 요청이 취소되었으면 결과 무시
          if (signal.aborted) return

          setValidation(result)
          setError(null)
        } catch (err) {
          // AbortError는 정상적인 취소이므로 무시
          if (err instanceof Error && err.name === 'AbortError') return

          setError(err instanceof Error ? err : new Error('Validation failed'))
        } finally {
          if (!signal.aborted) {
            setIsLoading(false)
          }
        }
      }, debounceMs)
    },
    [enabled, debounceMs]
  )

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current)
      }
      if (abortControllerRef.current) {
        abortControllerRef.current.abort()
      }
    }
  }, [])

  return { validation, isLoading, error, validate }
}
