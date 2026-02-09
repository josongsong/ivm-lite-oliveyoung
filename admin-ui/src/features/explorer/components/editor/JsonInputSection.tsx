import type { RefObject } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { AlertCircle, Check, Code2, Copy, Eye, Upload, Wand2, X } from 'lucide-react'
import { Button, Label, TextArea, toast } from '@/shared/ui'
import { copyToClipboard } from '@/shared/utils'

interface ValidationResult {
  valid: boolean
  errors?: string[]
}

interface JsonInputSectionProps {
  jsonInput: string
  jsonError: string | null
  selectedSchema: string
  showPreview: boolean
  validationResult: ValidationResult | null
  isDragging: boolean
  textareaRef: RefObject<HTMLTextAreaElement | null>
  fileInputRef: RefObject<HTMLInputElement | null>
  onJsonChange: (value: string) => void
  onGenerateSample: () => void
  onFormatJson: () => void
  onTogglePreview: () => void
  onDragOver: (e: React.DragEvent) => void
  onDragLeave: (e: React.DragEvent) => void
  onDrop: (e: React.DragEvent) => void
  onOpenFilePicker: () => void
  onFileInputChange: (e: React.ChangeEvent<HTMLInputElement>) => void
}

export function JsonInputSection({
  jsonInput,
  jsonError,
  selectedSchema,
  showPreview,
  validationResult,
  isDragging,
  textareaRef,
  fileInputRef,
  onJsonChange,
  onGenerateSample,
  onFormatJson,
  onTogglePreview,
  onDragOver,
  onDragLeave,
  onDrop,
  onOpenFilePicker,
  onFileInputChange,
}: JsonInputSectionProps) {
  return (
    <div className="form-group">
      <div className="json-header">
        <Label>JSON Data</Label>
        <div className="json-actions">
          <Button
            variant="primary"
            size="sm"
            onClick={onGenerateSample}
            disabled={!selectedSchema}
            icon={<Wand2 size={12} />}
            title={selectedSchema ? "스키마 기반 샘플 데이터 생성" : "먼저 스키마를 선택하세요"}
          >
            샘플 생성
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={onFormatJson}
            icon={<Code2 size={12} />}
            title="Format JSON"
          >
            Format
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={async () => {
              const result = await copyToClipboard(jsonInput)
              if (result.success) toast.success('복사됨')
              else toast.error(result.error || '복사 실패')
            }}
            icon={<Copy size={12} />}
            title="Copy"
          />
          <Button
            variant={showPreview ? 'secondary' : 'ghost'}
            size="sm"
            onClick={onTogglePreview}
            icon={<Eye size={12} />}
            title="Preview"
          />
        </div>
      </div>

      <div
        role="region"
        aria-label="JSON input area"
        className={`json-input-container ${isDragging ? 'dragging' : ''}`}
        onDragOver={onDragOver}
        onDragLeave={onDragLeave}
        onDrop={onDrop}
      >
        {isDragging ? (
          <div className="drop-overlay">
            <Upload size={32} />
            <span>JSON 파일을 여기에 드롭하세요</span>
          </div>
        ) : null}

        <TextArea
          ref={textareaRef}
          value={jsonInput}
          onChange={(e) => onJsonChange(e.target.value)}
          placeholder={`{
  "name": "상품명",
  "price": 25000,
  "stock": 150,
  ...
}`}
          error={!!jsonError}
          mono
          spellCheck={false}
          style={{ minHeight: '200px' }}
        />

        <Button
          variant="ghost"
          size="sm"
          className="file-upload-btn"
          onClick={onOpenFilePicker}
          icon={<Upload size={14} />}
        >
          파일 업로드
        </Button>
        <input
          ref={fileInputRef}
          type="file"
          accept=".json,application/json"
          onChange={onFileInputChange}
          hidden
        />
      </div>

      {jsonError ? (
        <motion.div
          className="json-error"
          initial={{ opacity: 0, y: -5 }}
          animate={{ opacity: 1, y: 0 }}
        >
          <AlertCircle size={14} />
          <span>{jsonError}</span>
        </motion.div>
      ) : null}

      <AnimatePresence>
        {validationResult ? (
          <motion.div
            className={`validation-result ${validationResult.valid ? 'valid' : 'invalid'}`}
            initial={{ opacity: 0, y: -5 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -5 }}
          >
            {validationResult.valid ? (
              <>
                <Check size={14} />
                <span>스키마 유효성 검증 통과</span>
              </>
            ) : (
              <>
                <X size={14} />
                <span>스키마 유효성 검증 실패</span>
                {validationResult.errors ? (
                  <ul className="validation-errors">
                    {validationResult.errors.map((err, i) => (
                      <li key={`validation-error-${i}`}>{err}</li>
                    ))}
                  </ul>
                ) : null}
              </>
            )}
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  )
}
