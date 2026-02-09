/**
 * FileUpload Component
 *
 * 파일 업로드 컴포넌트
 * - 드래그 앤 드롭 지원
 * - 파일 선택 버튼
 * - 파일명 표시
 */

import { useRef, useState, type DragEvent, type ChangeEvent } from 'react'
import { Upload } from 'lucide-react'
import './FileUpload.css'

export interface FileUploadProps {
  accept?: string
  onFileSelect: (file: File, content: string) => void
  className?: string
  label?: string
  disabled?: boolean
}

export function FileUpload({
  accept = '.json',
  onFileSelect,
  className = '',
  label = '파일 업로드',
  disabled = false,
}: FileUploadProps) {
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [isDragging, setIsDragging] = useState(false)
  const [fileName, setFileName] = useState<string | null>(null)

  const handleFile = (file: File) => {
    if (!file) return

    const reader = new FileReader()
    reader.onload = (event) => {
      const content = event.target?.result as string
      setFileName(file.name)
      onFileSelect(file, content)
    }
    reader.readAsText(file)
  }

  const handleFileInputChange = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      handleFile(file)
    }
  }

  const handleDragOver = (e: DragEvent) => {
    e.preventDefault()
    if (!disabled) {
      setIsDragging(true)
    }
  }

  const handleDragLeave = () => {
    setIsDragging(false)
  }

  const handleDrop = (e: DragEvent) => {
    e.preventDefault()
    setIsDragging(false)

    if (disabled) return

    const file = e.dataTransfer.files[0]
    if (file) {
      handleFile(file)
    }
  }

  const handleClick = () => {
    if (!disabled) {
      fileInputRef.current?.click()
    }
  }

  return (
    <div
      className={`ui-file-upload ${isDragging ? 'ui-file-upload--dragging' : ''} ${disabled ? 'ui-file-upload--disabled' : ''} ${className}`}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
      <input
        ref={fileInputRef}
        type="file"
        accept={accept}
        onChange={handleFileInputChange}
        className="ui-file-upload__input"
        disabled={disabled}
      />
      <button
        type="button"
        className="ui-file-upload__button"
        onClick={handleClick}
        disabled={disabled}
      >
        <Upload size={14} />
        <span>{label}</span>
      </button>
      {fileName && <span className="ui-file-upload__filename">{fileName}</span>}
    </div>
  )
}
