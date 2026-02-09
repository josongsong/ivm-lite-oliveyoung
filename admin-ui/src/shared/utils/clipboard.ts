/**
 * 안전한 클립보드 유틸리티
 * - 에러 처리 포함
 * - 브라우저 호환성 고려
 */

export interface CopyResult {
  success: boolean
  error?: string
}

/**
 * 텍스트를 클립보드에 복사 (에러 핸들링 포함)
 * @param text 복사할 텍스트
 * @returns 성공 여부와 에러 메시지
 */
export async function copyToClipboard(text: string): Promise<CopyResult> {
  // Clipboard API 지원 여부 확인
  if (!navigator.clipboard) {
    // fallback: execCommand (deprecated but works in some contexts)
    try {
      const textArea = document.createElement('textarea')
      textArea.value = text
      textArea.style.position = 'fixed'
      textArea.style.left = '-9999px'
      document.body.appendChild(textArea)
      textArea.select()
      document.execCommand('copy')
      document.body.removeChild(textArea)
      return { success: true }
    } catch {
      return { success: false, error: '클립보드 API를 사용할 수 없습니다' }
    }
  }

  try {
    await navigator.clipboard.writeText(text)
    return { success: true }
  } catch (error) {
    const message = error instanceof Error ? error.message : '알 수 없는 오류'

    // 권한 관련 에러 처리
    if (message.includes('denied') || message.includes('permission')) {
      return { success: false, error: '클립보드 접근 권한이 없습니다' }
    }

    return { success: false, error: `복사 실패: ${message}` }
  }
}

/**
 * JSON 데이터를 포맷팅하여 클립보드에 복사
 * @param data JSON 객체
 * @param indent 들여쓰기 (기본: 2)
 */
export async function copyJsonToClipboard(data: unknown, indent = 2): Promise<CopyResult> {
  try {
    const text = JSON.stringify(data, null, indent)
    return copyToClipboard(text)
  } catch {
    return { success: false, error: 'JSON 변환 실패' }
  }
}
