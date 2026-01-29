const API_BASE = '/api'

/** API 에러 클래스 - 서버 응답 정보 포함 */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly statusText: string,
    public readonly serverMessage?: string
  ) {
    const message = serverMessage
      ? `${status} ${statusText}: ${serverMessage}`
      : `${status} ${statusText}`
    super(message)
    this.name = 'ApiError'
  }
}

/** 에러 응답 파싱 헬퍼 */
async function parseErrorResponse(response: Response): Promise<string | undefined> {
  try {
    const contentType = response.headers.get('content-type')
    if (contentType?.includes('application/json')) {
      const body = await response.json()
      return body.message || body.error || JSON.stringify(body)
    }
    const text = await response.text()
    return text || undefined
  } catch {
    return undefined
  }
}

export async function fetchApi<T>(endpoint: string): Promise<T> {
  const response = await fetch(`${API_BASE}${endpoint}`)
  if (!response.ok) {
    const serverMessage = await parseErrorResponse(response)
    throw new ApiError(response.status, response.statusText, serverMessage)
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
    const serverMessage = await parseErrorResponse(response)
    throw new ApiError(response.status, response.statusText, serverMessage)
  }
  return response.json()
}
