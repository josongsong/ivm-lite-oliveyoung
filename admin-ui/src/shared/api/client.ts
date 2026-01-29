const API_BASE = '/api'

export async function fetchApi<T>(endpoint: string): Promise<T> {
  const response = await fetch(`${API_BASE}${endpoint}`)
  if (!response.ok) {
    throw new Error(`API Error: ${response.status} ${response.statusText}`)
  }
  return response.json()
}

export async function postApi<T>(endpoint: string, data?: unknown): Promise<T> {
  const response = await fetch(`${API_BASE}${endpoint}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: data ? JSON.stringify(data) : undefined,
  })
  if (!response.ok) {
    throw new Error(`API Error: ${response.status} ${response.statusText}`)
  }
  return response.json()
}
