/**
 * useGitOutput Hook (Phase 6: Git-Friendly Output)
 *
 * Contract 변경 내용을 Git 친화적 형식으로 내보내는 훅.
 * - Patch export
 * - PR description 생성
 * - Copy to clipboard
 */
import { useCallback, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { contractEditorApi } from '../api'
import type { ExportPatchResponse, ExportPRResponse } from '../api/types'

interface UseGitOutputOptions {
  contractKind?: string
  contractId?: string
}

interface UseGitOutputResult {
  // Patch
  patch: ExportPatchResponse | null
  isExportingPatch: boolean
  exportPatch: (before: string, after: string, filePath?: string) => void

  // PR
  prDescription: ExportPRResponse | null
  isExportingPR: boolean
  exportPR: (before: string, after: string) => void

  // Clipboard
  copyToClipboard: (text: string) => Promise<boolean>

  // State
  error: Error | null
  reset: () => void
}

export function useGitOutput(
  options: UseGitOutputOptions = {}
): UseGitOutputResult {
  const { contractKind, contractId } = options
  const [patch, setPatch] = useState<ExportPatchResponse | null>(null)
  const [prDescription, setPRDescription] = useState<ExportPRResponse | null>(null)
  const [error, setError] = useState<Error | null>(null)

  // Patch export mutation
  const patchMutation = useMutation({
    mutationFn: async ({
      before,
      after,
      filePath,
    }: {
      before: string
      after: string
      filePath: string
    }) => {
      return contractEditorApi.exportPatch({ before, after, filePath })
    },
    onSuccess: (data) => {
      setPatch(data)
      setError(null)
    },
    onError: (err: Error) => {
      setError(err)
    },
  })

  // PR description mutation
  const prMutation = useMutation({
    mutationFn: async ({
      before,
      after,
    }: {
      before: string
      after: string
    }) => {
      if (!contractKind || !contractId) {
        throw new Error('Contract kind and id are required')
      }
      return contractEditorApi.exportPR({
        before,
        after,
        contractId,
        contractKind,
      })
    },
    onSuccess: (data) => {
      setPRDescription(data)
      setError(null)
    },
    onError: (err: Error) => {
      setError(err)
    },
  })

  const exportPatch = useCallback(
    (before: string, after: string, filePath?: string) => {
      const defaultPath = contractId
        ? `contracts/${contractKind}/${contractId}.yaml`
        : 'contract.yaml'
      patchMutation.mutate({ before, after, filePath: filePath ?? defaultPath })
    },
    [patchMutation, contractKind, contractId]
  )

  const exportPR = useCallback(
    (before: string, after: string) => {
      prMutation.mutate({ before, after })
    },
    [prMutation]
  )

  const copyToClipboard = useCallback(async (text: string): Promise<boolean> => {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // Fallback for older browsers
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      try {
        document.execCommand('copy')
        return true
      } catch {
        return false
      } finally {
        document.body.removeChild(textarea)
      }
    }
  }, [])

  const reset = useCallback(() => {
    setPatch(null)
    setPRDescription(null)
    setError(null)
    patchMutation.reset()
    prMutation.reset()
  }, [patchMutation, prMutation])

  return {
    // Patch
    patch,
    isExportingPatch: patchMutation.isPending,
    exportPatch,

    // PR
    prDescription,
    isExportingPR: prMutation.isPending,
    exportPR,

    // Clipboard
    copyToClipboard,

    // State
    error,
    reset,
  }
}
