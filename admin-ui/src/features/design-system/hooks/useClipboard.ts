/**
 * useClipboard Hook
 * Phase 1-C: 클립보드 복사 기능을 제공하는 React 훅
 */

import { useCallback, useEffect, useRef, useState } from 'react'

// ============================================================================
// Types
// ============================================================================

export interface UseClipboardOptions {
  /** 복사 성공 상태 유지 시간 (ms) */
  successDuration?: number
  /** 복사 성공 시 콜백 */
  onSuccess?: (text: string) => void
  /** 복사 실패 시 콜백 */
  onError?: (error: Error) => void
}

export interface UseClipboardReturn {
  /** 텍스트를 클립보드에 복사 */
  copy: (text: string) => Promise<boolean>
  /** 복사 성공 여부 (일시적) */
  copied: boolean
  /** 마지막 복사한 텍스트 */
  lastCopied: string | null
  /** 에러 상태 */
  error: Error | null
  /** 상태 초기화 */
  reset: () => void
}

// ============================================================================
// Hook Implementation
// ============================================================================

/**
 * 클립보드 복사 훅
 *
 * @param options - 훅 옵션
 * @returns 클립보드 제어 객체
 *
 * @example
 * ```tsx
 * function CopyButton({ text }: { text: string }) {
 *   const { copy, copied } = useClipboard()
 *
 *   return (
 *     <Button onClick={() => copy(text)}>
 *       {copied ? 'Copied!' : 'Copy'}
 *     </Button>
 *   )
 * }
 * ```
 */
export function useClipboard(options: UseClipboardOptions = {}): UseClipboardReturn {
  const { successDuration = 2000, onSuccess, onError } = options

  const [copied, setCopied] = useState(false)
  const [lastCopied, setLastCopied] = useState<string | null>(null)
  const [error, setError] = useState<Error | null>(null)

  const timeoutRef = useRef<number | null>(null)

  // Cleanup timeout on unmount
  useEffect(() => {
    return () => {
      if (timeoutRef.current !== null) {
        window.clearTimeout(timeoutRef.current)
      }
    }
  }, [])

  const copy = useCallback(
    async (text: string): Promise<boolean> => {
      // Clear previous timeout
      if (timeoutRef.current !== null) {
        window.clearTimeout(timeoutRef.current)
      }

      try {
        // Modern Clipboard API
        if (navigator.clipboard && window.isSecureContext) {
          await navigator.clipboard.writeText(text)
        } else {
          // Fallback for older browsers
          fallbackCopyToClipboard(text)
        }

        setCopied(true)
        setLastCopied(text)
        setError(null)
        onSuccess?.(text)

        // Reset copied state after duration
        timeoutRef.current = window.setTimeout(() => {
          setCopied(false)
        }, successDuration)

        return true
      } catch (err) {
        const error = err instanceof Error ? err : new Error('Failed to copy to clipboard')
        setError(error)
        setCopied(false)
        onError?.(error)
        return false
      }
    },
    [successDuration, onSuccess, onError]
  )

  const reset = useCallback(() => {
    if (timeoutRef.current !== null) {
      window.clearTimeout(timeoutRef.current)
    }
    setCopied(false)
    setLastCopied(null)
    setError(null)
  }, [])

  return {
    copy,
    copied,
    lastCopied,
    error,
    reset,
  }
}

// ============================================================================
// Fallback Implementation
// ============================================================================

/**
 * execCommand를 사용한 폴백 구현
 * Secure context가 아닌 환경에서 사용
 */
function fallbackCopyToClipboard(text: string): void {
  const textArea = document.createElement('textarea')
  textArea.value = text

  // 화면에 보이지 않도록 스타일 설정
  textArea.style.position = 'fixed'
  textArea.style.left = '-9999px'
  textArea.style.top = '-9999px'
  textArea.style.opacity = '0'
  textArea.style.pointerEvents = 'none'
  textArea.setAttribute('readonly', '')
  textArea.setAttribute('aria-hidden', 'true')

  document.body.appendChild(textArea)

  try {
    textArea.select()
    textArea.setSelectionRange(0, text.length)

    const successful = document.execCommand('copy')
    if (!successful) {
      throw new Error('execCommand copy failed')
    }
  } finally {
    document.body.removeChild(textArea)
  }
}

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * 클립보드 API 지원 여부 확인
 */
export function isClipboardSupported(): boolean {
  return !!(navigator.clipboard && window.isSecureContext)
}

/**
 * 클립보드에서 텍스트 읽기 (권한 필요)
 */
export async function readFromClipboard(): Promise<string | null> {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      return await navigator.clipboard.readText()
    }
    return null
  } catch {
    return null
  }
}
