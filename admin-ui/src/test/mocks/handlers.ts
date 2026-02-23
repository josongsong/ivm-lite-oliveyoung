import { http, HttpResponse } from 'msw'

// Mock data
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
  // Health endpoint
  http.get('/api/health', () => {
    return HttpResponse.json(mockHealthResponse)
  }),
]
