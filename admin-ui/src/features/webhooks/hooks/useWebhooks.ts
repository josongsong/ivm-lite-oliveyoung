/**
 * Webhooks Hooks
 *
 * 웹훅 관련 React hooks
 */

import { useCallback, useEffect, useState } from 'react'
import type {
  CreateWebhookRequest,
  EventInfo,
  TestResult,
  UpdateWebhookRequest,
  Webhook,
  WebhookDelivery,
  WebhookStats,
} from '../types/webhooks'
import * as api from '../api/webhooksApi'

// ===== useWebhooks =====

export function useWebhooks(activeOnly = false) {
  const [webhooks, setWebhooks] = useState<Webhook[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const response = await api.fetchWebhooks(activeOnly)
      setWebhooks(response.webhooks)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch webhooks')
    } finally {
      setLoading(false)
    }
  }, [activeOnly])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  return { webhooks, loading, error, refetch: fetchData }
}

// ===== useWebhookDetail =====

export function useWebhookDetail(id: string | null) {
  const [webhook, setWebhook] = useState<Webhook | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const fetchData = useCallback(async () => {
    if (!id) {
      setWebhook(null)
      return
    }

    setLoading(true)
    setError(null)
    try {
      const data = await api.fetchWebhook(id)
      setWebhook(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch webhook')
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  return { webhook, loading, error, refetch: fetchData }
}

// ===== useWebhookStats =====

export function useWebhookStats() {
  const [stats, setStats] = useState<WebhookStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await api.fetchWebhookStats()
      setStats(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch stats')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  return { stats, loading, error, refetch: fetchData }
}

// ===== useSupportedEvents =====

export function useSupportedEvents() {
  const [events, setEvents] = useState<EventInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const fetchData = async () => {
      try {
        const response = await api.fetchSupportedEvents()
        setEvents(response.events)
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to fetch events')
      } finally {
        setLoading(false)
      }
    }
    fetchData()
  }, [])

  return { events, loading, error }
}

// ===== useDeliveries =====

export function useDeliveries(webhookId: string | null, limit = 50) {
  const [deliveries, setDeliveries] = useState<WebhookDelivery[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const fetchData = useCallback(async () => {
    if (!webhookId) {
      setDeliveries([])
      return
    }

    setLoading(true)
    setError(null)
    try {
      const response = await api.fetchDeliveries(webhookId, limit)
      setDeliveries(response.deliveries)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch deliveries')
    } finally {
      setLoading(false)
    }
  }, [webhookId, limit])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  return { deliveries, loading, error, refetch: fetchData }
}

// ===== useRecentDeliveries =====

export function useRecentDeliveries(limit = 50) {
  const [deliveries, setDeliveries] = useState<WebhookDelivery[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const response = await api.fetchRecentDeliveries(limit)
      setDeliveries(response.deliveries)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch deliveries')
    } finally {
      setLoading(false)
    }
  }, [limit])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  return { deliveries, loading, error, refetch: fetchData }
}

// ===== useWebhookMutations =====

export function useWebhookMutations() {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const create = useCallback(async (request: CreateWebhookRequest): Promise<Webhook | null> => {
    setLoading(true)
    setError(null)
    try {
      return await api.createWebhook(request)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create webhook')
      return null
    } finally {
      setLoading(false)
    }
  }, [])

  const update = useCallback(async (id: string, request: UpdateWebhookRequest): Promise<Webhook | null> => {
    setLoading(true)
    setError(null)
    try {
      return await api.updateWebhook(id, request)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update webhook')
      return null
    } finally {
      setLoading(false)
    }
  }, [])

  const remove = useCallback(async (id: string): Promise<boolean> => {
    setLoading(true)
    setError(null)
    try {
      await api.deleteWebhook(id)
      return true
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete webhook')
      return false
    } finally {
      setLoading(false)
    }
  }, [])

  const test = useCallback(async (id: string): Promise<TestResult | null> => {
    setLoading(true)
    setError(null)
    try {
      return await api.testWebhook(id)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to test webhook')
      return null
    } finally {
      setLoading(false)
    }
  }, [])

  const resetCircuitBreaker = useCallback(async (id: string): Promise<boolean> => {
    setLoading(true)
    setError(null)
    try {
      await api.resetCircuit(id)
      return true
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to reset circuit breaker')
      return false
    } finally {
      setLoading(false)
    }
  }, [])

  return {
    loading,
    error,
    create,
    update,
    remove,
    test,
    resetCircuitBreaker,
  }
}
