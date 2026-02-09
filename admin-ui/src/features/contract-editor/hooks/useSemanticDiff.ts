/**
 * useSemanticDiff Hook (Phase 2: Semantic Diff)
 *
 * Contract 변경사항에 대한 시맨틱 Diff를 계산합니다.
 * - Breaking change 감지
 * - 영향받는 Slice/View 분석
 * - Re-ingest 필요 여부 판단
 * - AbortController로 stale 요청 취소
 */
import { useCallback, useEffect, useRef, useState } from 'react'
import { contractEditorApi } from '../api'
import type { SemanticDiffResult } from '../api/types'
import { PERFORMANCE } from '../constants/editorOptions'

interface UseSemanticDiffOptions {
  debounceMs?: number
}

interface UseSemanticDiffReturn {
  diff: SemanticDiffResult | null
  isLoading: boolean
  error: Error | null
  computeDiff: (before: string, after: string) => void
}

export function useSemanticDiff(
  options: UseSemanticDiffOptions = {}
): UseSemanticDiffReturn {
  const { debounceMs = PERFORMANCE.DEBOUNCE.SEMANTIC_DIFF } = options

  const [diff, setDiff] = useState<SemanticDiffResult | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<Error | null>(null)

  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const abortControllerRef = useRef<AbortController | null>(null)
  const lastRequestRef = useRef<{ before: string; after: string } | null>(null)

  const computeDiff = useCallback(
    (before: string, after: string) => {
      const trimmedBefore = before.trim()
      const trimmedAfter = after.trim()

      // 동일한 요청이면 스킵
      if (
        lastRequestRef.current?.before === trimmedBefore &&
        lastRequestRef.current?.after === trimmedAfter
      ) {
        return
      }

      // 이전 타이머 취소
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current)
        debounceTimerRef.current = null
      }

      // 이전 요청 취소
      if (abortControllerRef.current) {
        abortControllerRef.current.abort()
        abortControllerRef.current = null
      }

      // 변경 없으면 결과 초기화
      if (trimmedBefore === trimmedAfter) {
        setDiff(null)
        setError(null)
        setIsLoading(false)
        lastRequestRef.current = null
        return
      }

      setIsLoading(true)

      debounceTimerRef.current = setTimeout(async () => {
        lastRequestRef.current = { before: trimmedBefore, after: trimmedAfter }
        abortControllerRef.current = new AbortController()
        const signal = abortControllerRef.current.signal

        try {
          const result = await contractEditorApi.diff({
            before: trimmedBefore,
            after: trimmedAfter,
          })

          if (signal.aborted) return

          setDiff(result)
          setError(null)
        } catch (err) {
          if (err instanceof Error && err.name === 'AbortError') return

          setError(err instanceof Error ? err : new Error('Diff 계산 실패'))
          setDiff(null)
        } finally {
          if (!signal.aborted) {
            setIsLoading(false)
          }
        }
      }, debounceMs)
    },
    [debounceMs]
  )

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current)
      }
      if (abortControllerRef.current) {
        abortControllerRef.current.abort()
      }
    }
  }, [])

  return {
    diff,
    isLoading,
    error,
    computeDiff,
  }
}
