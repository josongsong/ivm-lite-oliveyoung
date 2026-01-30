// 환경변수로 API URL 설정 (분리 배포 지원)
// 개발: VITE_API_URL 미설정 시 proxy 사용 (/api)
// 프로덕션: VITE_API_URL=https://api.example.com/api
const API_BASE = import.meta.env.VITE_API_URL || '/api'

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
    // response.clone()을 사용하여 원본 response를 보존
    const clonedResponse = response.clone()
    const contentType = clonedResponse.headers.get('content-type')
    if (contentType?.includes('application/json')) {
      const body = await clonedResponse.json()
      return body.message || body.error || JSON.stringify(body)
    }
    const text = await clonedResponse.text()
    return text || undefined
  } catch {
    return undefined
  }
}

export interface FetchApiOptions {
  /** 허용할 HTTP 상태 코드 목록 (기본: 200-299만 허용) */
  allowedStatusCodes?: number[]
  /** 요청 타임아웃 (ms, 기본: 10000) */
  timeout?: number
}

const DEFAULT_TIMEOUT = 10000 // 10초

export async function fetchApi<T>(
  endpoint: string,
  options?: FetchApiOptions
): Promise<T> {
  const timeout = options?.timeout ?? DEFAULT_TIMEOUT
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), timeout)
  
  try {
    const response = await fetch(`${API_BASE}${endpoint}`, {
      signal: controller.signal,
    })
    
    clearTimeout(timeoutId)
    
    // 허용된 상태 코드가 있으면 해당 상태 코드도 정상으로 처리
    const isAllowed = options?.allowedStatusCodes?.includes(response.status) ?? false
    
    // 응답 본문을 한 번만 읽기 위해 미리 읽어둠
    const contentType = response.headers.get('content-type')
    const isJson = contentType?.includes('application/json')
    
    if (!response.ok && !isAllowed) {
      const serverMessage = await parseErrorResponse(response)
      throw new ApiError(response.status, response.statusText, serverMessage)
    }
    
    // 허용된 상태 코드이거나 정상 응답인 경우 JSON 파싱
    if (isJson) {
      return response.json()
    } else {
      // JSON이 아닌 경우 텍스트로 반환 (일반적으로 발생하지 않음)
      const text = await response.text()
      return text as unknown as T
    }
  } catch (error) {
    clearTimeout(timeoutId)
    if (error instanceof Error && error.name === 'AbortError') {
      throw new ApiError(408, 'Request Timeout', `Request timed out after ${timeout}ms`)
    }
    throw error
  }
}

async function putApi<T>(
  endpoint: string,
  data?: unknown,
  options?: { timeout?: number }
): Promise<T> {
  const timeout = options?.timeout ?? DEFAULT_TIMEOUT
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), timeout)

  try {
    const response = await fetch(`${API_BASE}${endpoint}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: data ? JSON.stringify(data) : undefined,
      signal: controller.signal,
    })
    clearTimeout(timeoutId)
    if (!response.ok) {
      const serverMessage = await parseErrorResponse(response)
      throw new ApiError(response.status, response.statusText, serverMessage)
    }
    return response.json()
  } catch (error) {
    clearTimeout(timeoutId)
    if (error instanceof Error && error.name === 'AbortError') {
      throw new ApiError(408, 'Request Timeout', `Request timed out after ${timeout}ms`)
    }
    throw error
  }
}

async function deleteApi<T>(
  endpoint: string,
  options?: { timeout?: number }
): Promise<T> {
  const timeout = options?.timeout ?? DEFAULT_TIMEOUT
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), timeout)

  try {
    const response = await fetch(`${API_BASE}${endpoint}`, {
      method: 'DELETE',
      signal: controller.signal,
    })
    clearTimeout(timeoutId)
    if (!response.ok) {
      const serverMessage = await parseErrorResponse(response)
      throw new ApiError(response.status, response.statusText, serverMessage)
    }
    const text = await response.text()
    return text ? JSON.parse(text) : {} as T
  } catch (error) {
    clearTimeout(timeoutId)
    if (error instanceof Error && error.name === 'AbortError') {
      throw new ApiError(408, 'Request Timeout', `Request timed out after ${timeout}ms`)
    }
    throw error
  }
}

/** API Client 래퍼 - 클래스 스타일 API 호출 */
export const apiClient = {
  get: <T>(endpoint: string) => fetchApi<T>(endpoint),
  post: <T>(endpoint: string, data?: unknown) => postApi<T>(endpoint, data),
  put: <T>(endpoint: string, data?: unknown) => putApi<T>(endpoint, data),
  delete: <T>(endpoint: string) => deleteApi<T>(endpoint),
}

export async function postApi<T>(
  endpoint: string,
  data?: unknown,
  options?: { timeout?: number }
): Promise<T> {
  const timeout = options?.timeout ?? DEFAULT_TIMEOUT
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), timeout)
  
  try {
    const response = await fetch(`${API_BASE}${endpoint}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: data ? JSON.stringify(data) : undefined,
      signal: controller.signal,
    })
    
    clearTimeout(timeoutId)
    
    if (!response.ok) {
      const serverMessage = await parseErrorResponse(response)
      throw new ApiError(response.status, response.statusText, serverMessage)
    }
    return response.json()
  } catch (error) {
    clearTimeout(timeoutId)
    if (error instanceof Error && error.name === 'AbortError') {
      throw new ApiError(408, 'Request Timeout', `Request timed out after ${timeout}ms`)
    }
    throw error
  }
}
