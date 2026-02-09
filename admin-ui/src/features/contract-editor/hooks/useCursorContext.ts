/**
 * useCursorContext Hook (Phase 1: DX Platform)
 *
 * Monaco Editor 커서 위치에 따른 의미론적 컨텍스트 조회.
 * - 빠른 debounce (50ms)로 즉각적인 피드백
 * - AbortController로 stale 요청 취소
 * - 위치 변경 없으면 스킵
 */
import { useCallback, useEffect, useRef, useState } from 'react'
import { contractEditorApi } from '../api'
import type { CursorContextResponse } from '../api/types'
import { PERFORMANCE } from '../constants/editorOptions'

interface UseCursorContextOptions {
  yaml: string
  enabled?: boolean
  debounceMs?: number
}

interface UseCursorContextResult {
  context: CursorContextResponse | null
  isLoading: boolean
  error: Error | null
  updatePosition: (line: number, column: number) => void
}

export function useCursorContext({
  yaml,
  enabled = true,
  debounceMs = PERFORMANCE.DEBOUNCE.CURSOR_CONTEXT,
}: UseCursorContextOptions): UseCursorContextResult {
  const [context, setContext] = useState<CursorContextResponse | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<Error | null>(null)

  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const abortControllerRef = useRef<AbortController | null>(null)
  const lastPositionRef = useRef<{ line: number; column: number; yaml: string } | null>(null)

  const updatePosition = useCallback(
    (line: number, column: number) => {
      if (!enabled) {
        setContext(null)
        return
      }

      const trimmedYaml = yaml.trim()
      if (!trimmedYaml) {
        setContext(null)
        return
      }

      // 동일한 위치 + YAML이면 스킵 (불필요한 API 호출 방지)
      const lastPos = lastPositionRef.current
      if (
        lastPos &&
        lastPos.line === line &&
        lastPos.column === column &&
        lastPos.yaml === trimmedYaml
      ) {
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

      timeoutRef.current = setTimeout(async () => {
        lastPositionRef.current = { line, column, yaml: trimmedYaml }
        abortControllerRef.current = new AbortController()
        const signal = abortControllerRef.current.signal

        setIsLoading(true)
        setError(null)

        try {
          const result = await contractEditorApi.getCursorContext({
            yaml: trimmedYaml,
            line,
            column,
          })

          if (signal.aborted) return

          setContext(result)
        } catch (err) {
          if (err instanceof Error && err.name === 'AbortError') return

          setError(err instanceof Error ? err : new Error('Failed to get cursor context'))
        } finally {
          if (!signal.aborted) {
            setIsLoading(false)
          }
        }
      }, debounceMs)
    },
    [yaml, enabled, debounceMs]
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

  return { context, isLoading, error, updatePosition }
}
