import { useCallback, useRef, useState } from 'react'
import { toast } from '@/shared/ui'
import { MAX_FILE_SIZE } from '../utils/sampleDataGenerator'

interface UseJsonFileUploadOptions {
  onContentParsed: (content: string, fileName: string) => void
  onError?: (error: string) => void
}

export function useJsonFileUpload({ onContentParsed, onError }: UseJsonFileUploadOptions) {
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [isDragging, setIsDragging] = useState(false)

  /** 파일 검증 유틸리티 (확장자 + MIME + 크기) */
  const validateJsonFile = useCallback((file: File): string | null => {
    // 확장자 검증
    if (!file.name.toLowerCase().endsWith('.json')) {
      return 'JSON 파일(.json)만 지원됩니다'
    }

    // MIME 타입 검증 (일부 브라우저는 text/plain으로 감지)
    const validMimeTypes = ['application/json', 'text/plain', 'text/json']
    if (file.type && !validMimeTypes.includes(file.type)) {
      return `지원하지 않는 파일 형식입니다: ${file.type}`
    }

    // 파일 크기 검증
    if (file.size > MAX_FILE_SIZE) {
      return `파일 크기가 너무 큽니다 (최대 5MB, 현재: ${(file.size / 1024 / 1024).toFixed(2)}MB)`
    }

    if (file.size === 0) {
      return '빈 파일입니다'
    }

    return null
  }, [])

  /** 파일 내용 처리 */
  const processFile = useCallback((file: File) => {
    const validationError = validateJsonFile(file)
    if (validationError) {
      toast.error(validationError)
      onError?.(validationError)
      return
    }

    const reader = new FileReader()
    reader.onload = (event) => {
      const content = event.target?.result
      if (typeof content !== 'string') {
        const error = '파일을 읽을 수 없습니다'
        toast.error(error)
        onError?.(error)
        return
      }

      // JSON 파싱 검증
      try {
        const parsed = JSON.parse(content)
        if (typeof parsed !== 'object' || parsed === null) {
          const error = 'JSON 최상위는 객체 또는 배열이어야 합니다'
          toast.error(error)
          onError?.(error)
          return
        }
        onContentParsed(content, file.name)
      } catch (e) {
        const error = `JSON 파싱 실패: ${(e as Error).message}`
        toast.error('유효하지 않은 JSON 파일입니다')
        onError?.(error)
      }
    }
    reader.onerror = () => {
      const error = '파일 읽기 실패'
      toast.error('파일을 읽을 수 없습니다')
      onError?.(error)
    }
    reader.readAsText(file)
  }, [validateJsonFile, onContentParsed, onError])

  /** 파일 드롭 핸들러 */
  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(false)

    const file = e.dataTransfer.files[0]
    if (file) {
      processFile(file)
    }
  }, [processFile])

  /** 드래그 이벤트 핸들러 */
  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(true)
  }, [])

  const handleDragLeave = useCallback(() => {
    setIsDragging(false)
  }, [])

  /** 파일 입력 변경 핸들러 */
  const handleFileInputChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      processFile(file)
    }
    e.target.value = '' // 동일 파일 재선택 허용
  }, [processFile])

  /** 파일 선택 다이얼로그 열기 */
  const openFilePicker = useCallback(() => {
    fileInputRef.current?.click()
  }, [])

  return {
    fileInputRef,
    isDragging,
    handleDrop,
    handleDragOver,
    handleDragLeave,
    handleFileInputChange,
    openFilePicker,
  }
}
