import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchApi, postApi } from '@/shared/api'
import { OUTBOX_CONFIG } from '@/shared/config'
import type {
  DlqResponse,
  FailedResponse,
  OutboxEntryDto,
  RecentOutboxResponse,
  StaleResponse,
} from '@/shared/types'
import { toast } from '@/shared/ui'

export function useOutboxQueries() {
  const queryClient = useQueryClient()
  const { DEFAULT_LIMIT, BATCH_RETRY_LIMIT, STALE_TIMEOUT_SECONDS } = OUTBOX_CONFIG

  // Recent 데이터
  const recentQuery = useQuery({
    queryKey: ['outbox-recent'],
    queryFn: () => fetchApi<RecentOutboxResponse>(`/outbox/recent?limit=${DEFAULT_LIMIT}`),
    retry: 1,
    staleTime: 30_000,
  })

  // Failed 데이터
  const failedQuery = useQuery({
    queryKey: ['outbox-failed'],
    queryFn: () => fetchApi<FailedResponse>(`/outbox/failed?limit=${DEFAULT_LIMIT}`),
    retry: 1,
    staleTime: 30_000,
  })

  // DLQ 데이터
  const dlqQuery = useQuery({
    queryKey: ['outbox-dlq'],
    queryFn: () => fetchApi<DlqResponse>(`/outbox/dlq?limit=${DEFAULT_LIMIT}`),
    retry: 1,
    staleTime: 30_000,
  })

  // Stale 데이터
  const staleQuery = useQuery({
    queryKey: ['outbox-stale'],
    queryFn: () => fetchApi<StaleResponse>(`/outbox/stale?timeout=${STALE_TIMEOUT_SECONDS}`),
    staleTime: 30_000,
  })

  // Replay mutation (DLQ)
  const replayMutation = useMutation({
    mutationFn: (id: string) => postApi(`/outbox/dlq/${id}/replay`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['outbox-dlq'] })
      queryClient.invalidateQueries({ queryKey: ['outbox-recent'] })
    },
  })

  // Retry mutation (개별)
  const retryMutation = useMutation({
    mutationFn: (id: string) => postApi(`/outbox/${id}/retry`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['outbox-failed'] })
      queryClient.invalidateQueries({ queryKey: ['outbox-recent'] })
    },
  })

  // Retry All mutation (일괄)
  const retryAllMutation = useMutation({
    mutationFn: () => postApi(`/outbox/failed/retry-all?limit=${BATCH_RETRY_LIMIT}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['outbox-failed'] })
      queryClient.invalidateQueries({ queryKey: ['outbox-recent'] })
    },
  })

  // Release Stale mutation
  const releaseStale = useMutation({
    mutationFn: () => postApi(`/outbox/stale/release?timeout=${STALE_TIMEOUT_SECONDS}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['outbox-stale'] })
      queryClient.invalidateQueries({ queryKey: ['outbox-recent'] })
    },
  })

  // Process mutation (개별 항목 처리)
  const processMutation = useMutation({
    mutationFn: (id: string) => postApi(`/outbox/${id}/retry`),
    onSuccess: (_, id) => {
      toast.success(`항목 ${id.slice(0, 8)}... 처리 완료`)
      queryClient.invalidateQueries({ queryKey: ['outbox-recent'] })
      queryClient.invalidateQueries({ queryKey: ['outbox-stats'] })
    },
    onError: (error) => {
      const message = error instanceof Error ? error.message : 'Failed to process entry'
      toast.error(message)
    },
  })

  const refreshAll = () => queryClient.invalidateQueries()

  const isLoading = recentQuery.isLoading || failedQuery.isLoading || dlqQuery.isLoading || staleQuery.isLoading

  return {
    // Queries
    recentQuery,
    failedQuery,
    dlqQuery,
    staleQuery,
    // Mutations
    replayMutation,
    retryMutation,
    retryAllMutation,
    releaseStale,
    processMutation,
    // Helpers
    refreshAll,
    isLoading,
    STALE_TIMEOUT_SECONDS,
  }
}

export function useOutboxDetail(selectedEntryId: string | null) {
  return useQuery({
    queryKey: ['outbox-detail', selectedEntryId],
    queryFn: () => fetchApi<OutboxEntryDto>(`/outbox/${selectedEntryId}`),
    enabled: !!selectedEntryId,
    staleTime: 0,
  })
}
