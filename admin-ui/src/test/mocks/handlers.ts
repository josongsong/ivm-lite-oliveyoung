import { http, HttpResponse } from 'msw'

// Mock data
export const mockOutboxRecent = {
  items: [
    {
      id: '123e4567-e89b-12d3-a456-426614174000',
      aggregateType: 'PRODUCT',
      aggregateId: 'PRODUCT:SKU-001',
      eventType: 'PRODUCT_UPDATED',
      status: 'PROCESSED',
      createdAt: '2024-01-15T10:30:00Z',
      processedAt: '2024-01-15T10:30:05Z',
      retryCount: 0,
    },
    {
      id: '223e4567-e89b-12d3-a456-426614174001',
      aggregateType: 'PRODUCT',
      aggregateId: 'PRODUCT:SKU-002',
      eventType: 'PRODUCT_CREATED',
      status: 'PENDING',
      createdAt: '2024-01-15T10:35:00Z',
      processedAt: null,
      retryCount: 0,
    },
  ],
  count: 2,
}

export const mockOutboxFailed = {
  items: [
    {
      id: '323e4567-e89b-12d3-a456-426614174002',
      aggregateType: 'ORDER',
      aggregateId: 'ORDER:ORD-001',
      eventType: 'ORDER_FAILED',
      createdAt: '2024-01-15T09:00:00Z',
      retryCount: 3,
      failureReason: 'Connection timeout',
    },
  ],
  count: 1,
}

export const mockOutboxDlq = {
  items: [],
  count: 0,
}

export const mockOutboxStale = {
  items: [],
  count: 0,
  timeoutSeconds: 300,
}

export const mockHealthResponse = {
  status: 'HEALTHY',
  uptime: 86400,
  components: [
    { name: 'database', status: 'HEALTHY', latency: 5 },
    { name: 'dynamodb', status: 'HEALTHY', latency: 12 },
    { name: 'worker', status: 'HEALTHY', latency: null },
  ],
}

export const handlers = [
  // Outbox endpoints
  http.get('/api/outbox/recent', () => {
    return HttpResponse.json(mockOutboxRecent)
  }),

  http.get('/api/outbox/failed', () => {
    return HttpResponse.json(mockOutboxFailed)
  }),

  http.get('/api/outbox/dlq', () => {
    return HttpResponse.json(mockOutboxDlq)
  }),

  http.get('/api/outbox/stale', () => {
    return HttpResponse.json(mockOutboxStale)
  }),

  http.get('/api/outbox/:id', ({ params }) => {
    const entry = mockOutboxRecent.items.find(i => i.id === params.id)
    if (entry) {
      return HttpResponse.json({
        ...entry,
        idempotencyKey: `idem-${entry.id}`,
        payload: JSON.stringify({ sku: 'SKU-001', name: 'Test Product' }),
        claimedAt: null,
        claimedBy: null,
        priority: 0,
        entityVersion: 1,
      })
    }
    return HttpResponse.json({ error: 'Not found' }, { status: 404 })
  }),

  // Health endpoint
  http.get('/api/health', () => {
    return HttpResponse.json(mockHealthResponse)
  }),
]
