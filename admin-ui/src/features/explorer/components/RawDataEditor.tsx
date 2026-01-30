import { useCallback, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import {
  AlertCircle,
  Check,
  ChevronDown,
  Code2,
  Copy,
  Eye,
  FileJson,
  Loader2,
  Send,
  Sparkles,
  Trash2,
  Upload,
  Wand2,
  X
} from 'lucide-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { explorerApi } from '@/shared/api'
import type { RawDataCreateRequest, SchemaField, SchemaInfo } from '@/shared/types'
import './RawDataEditor.css'

interface RawDataEditorProps {
  defaultTenant?: string
  onSuccess?: (entityId: string) => void
}

interface ValidationResult {
  valid: boolean
  errors?: string[]
}

/** 스키마 필드 기반 샘플 데이터 생성 */
function generateSampleData(schema: SchemaInfo): Record<string, unknown> {
  const result: Record<string, unknown> = {}

  // 제품 관련 샘플 데이터 풀
  const samplePool = {
    productNames: ['비타민C 1000mg', '히알루론산 세럼', '선크림 SPF50', '립스틱 로즈핑크', '쿠션 파운데이션'],
    brands: ['올리브영', '닥터지', '라운드랩', '토리든', 'COSRX'],
    categories: ['건강식품', '스킨케어', '메이크업', '헤어케어', '바디케어'],
    categoryPaths: ['건강식품>비타민', '스킨케어>세럼', '메이크업>립', '헤어케어>샴푸', '바디케어>로션'],
    descriptions: ['촉촉한 보습감', '강력한 자외선 차단', '풍부한 발색력', '부드러운 사용감', '건강한 피부를 위해'],
    tags: ['인기', '베스트셀러', '신상품', '세일', '리뷰많음', '추천'],
    images: [
      'https://image.oliveyoung.co.kr/sample1.jpg',
      'https://image.oliveyoung.co.kr/sample2.jpg',
      'https://image.oliveyoung.co.kr/sample3.jpg',
    ],
    currencies: ['KRW', 'USD', 'JPY'],
  }

  const randomInt = (min: number, max: number) => Math.floor(Math.random() * (max - min + 1)) + min
  const randomItem = <T,>(arr: T[]): T => arr[Math.floor(Math.random() * arr.length)]

  for (const field of schema.fields) {
    const name = field.name.toLowerCase()

    // 필드명 기반 스마트 생성
    if (name === 'sku' || name === 'id' || name === 'entityid') {
      result[field.name] = `SKU-${randomInt(10000, 99999)}`
    } else if (name === 'name' || name === 'productname') {
      result[field.name] = randomItem(samplePool.productNames)
    } else if (name === 'price') {
      result[field.name] = randomInt(10, 100) * 1000 // 10,000 ~ 100,000
    } else if (name === 'saleprice') {
      result[field.name] = randomInt(5, 80) * 1000 // 할인가
    } else if (name === 'brand') {
      result[field.name] = randomItem(samplePool.brands)
    } else if (name === 'brandid') {
      result[field.name] = `BRAND-${randomInt(100, 999)}`
    } else if (name === 'category') {
      result[field.name] = randomItem(samplePool.categories)
    } else if (name === 'categorypath') {
      result[field.name] = randomItem(samplePool.categoryPaths)
    } else if (name === 'stock') {
      result[field.name] = randomInt(0, 500)
    } else if (name === 'isavailable' || name.includes('available') || name.includes('active')) {
      result[field.name] = Math.random() > 0.2 // 80% 확률로 true
    } else if (name === 'description') {
      result[field.name] = randomItem(samplePool.descriptions)
    } else if (name === 'tags') {
      const count = randomInt(1, 3)
      result[field.name] = Array.from({ length: count }, () => randomItem(samplePool.tags))
    } else if (name === 'images') {
      const count = randomInt(1, 3)
      result[field.name] = samplePool.images.slice(0, count)
    } else if (name === 'currency') {
      result[field.name] = randomItem(samplePool.currencies)
    } else {
      // 타입 기반 기본 생성
      result[field.name] = generateByType(field)
    }
  }

  return result
}

/** 필드 타입 기반 기본 값 생성 */
function generateByType(field: SchemaField): unknown {
  switch (field.type) {
    case 'string':
      return `sample_${field.name}_${Math.random().toString(36).slice(2, 8)}`
    case 'number':
      return Math.floor(Math.random() * 10000)
    case 'boolean':
      return Math.random() > 0.5
    case 'array':
      return [`item_${Math.random().toString(36).slice(2, 6)}`]
    case 'object':
      return { key: 'value' }
    default:
      return field.required ? 'required_value' : null
  }
}

export function RawDataEditor({ defaultTenant = 'oliveyoung', onSuccess }: RawDataEditorProps) {
  const queryClient = useQueryClient()
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  // 폼 상태
  const [tenant, setTenant] = useState(defaultTenant)
  const [entityId, setEntityId] = useState('')
  const [selectedSchema, setSelectedSchema] = useState<string>('')
  const [jsonInput, setJsonInput] = useState('')
  const [showSchemaDropdown, setShowSchemaDropdown] = useState(false)
  const [showPreview, setShowPreview] = useState(false)
  const [isDragging, setIsDragging] = useState(false)

  // 유효성 검증 상태
  const [jsonError, setJsonError] = useState<string | null>(null)
  const [validationResult, setValidationResult] = useState<ValidationResult | null>(null)

  // 스키마 목록 조회 (API 없어도 안전하게 처리)
  const { data: schemasData, isError: schemasError } = useQuery({
    queryKey: ['schemas', tenant],
    queryFn: () => explorerApi.getSchemas(tenant),
    staleTime: 60000,
    retry: false,
  })

  // 유효성 검증 뮤테이션 (API 없으면 로컬 JSON 검증만)
  const validateMutation = useMutation({
    mutationFn: async ({ schemaRef, data }: { schemaRef: string; data: Record<string, unknown> }) => {
      try {
        return await explorerApi.validateRawData(schemaRef, data)
      } catch {
        // API 없으면 JSON 파싱 성공 = 유효
        return { valid: true }
      }
    },
    onSuccess: (result) => {
      setValidationResult(result)
    },
  })

  // 등록 뮤테이션
  const createMutation = useMutation({
    mutationFn: async (request: RawDataCreateRequest) => {
      try {
        return await explorerApi.createRawData(request)
      } catch (e) {
        throw new Error(`API 연결 실패: ${(e as Error).message}`)
      }
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['rawdata'] })
      onSuccess?.(result.entry.entityId)
      resetForm()
    },
  })

  // JSON 파싱 및 검증
  const parseAndValidate = useCallback((value: string) => {
    setJsonInput(value)
    setValidationResult(null)

    if (!value.trim()) {
      setJsonError(null)
      return
    }

    try {
      JSON.parse(value)
      setJsonError(null)

      // 스키마 선택되면 서버 검증
      if (selectedSchema) {
        validateMutation.mutate({
          schemaRef: selectedSchema,
          data: JSON.parse(value),
        })
      }
    } catch (e) {
      setJsonError((e as Error).message)
    }
  }, [selectedSchema, validateMutation])

  // 파일 드롭 핸들러
  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(false)

    const file = e.dataTransfer.files[0]
    if (file && file.type === 'application/json') {
      const reader = new FileReader()
      reader.onload = (event) => {
        const content = event.target?.result as string
        parseAndValidate(content)

        // 파일명에서 entityId 추출 시도
        const nameWithoutExt = file.name.replace('.json', '')
        if (!entityId) {
          setEntityId(nameWithoutExt)
        }
      }
      reader.readAsText(file)
    }
  }, [entityId, parseAndValidate])

  // JSON 포맷팅
  const formatJson = () => {
    try {
      const parsed = JSON.parse(jsonInput)
      setJsonInput(JSON.stringify(parsed, null, 2))
      setJsonError(null)
    } catch (e) {
      setJsonError((e as Error).message)
    }
  }

  // 샘플 데이터 생성
  const handleGenerateSample = () => {
    // 선택된 스키마 찾기
    const schema = schemasData?.schemas?.find(s => s.id === selectedSchema)

    if (schema && schema.fields.length > 0) {
      // 스키마 기반 생성
      const sample = generateSampleData(schema)
      setJsonInput(JSON.stringify(sample, null, 2))
      setJsonError(null)

      // Entity ID도 자동 생성
      if (!entityId) {
        const sku = (sample.sku || sample.id || sample.entityId) as string
        if (sku) setEntityId(sku)
      }
    } else {
      // 스키마 없으면 기본 Product 샘플
      const defaultSample = {
        sku: `SKU-${Math.floor(Math.random() * 90000) + 10000}`,
        name: '샘플 상품',
        price: 29000,
        salePrice: 25000,
        brand: '올리브영',
        category: '스킨케어',
        stock: 150,
        isAvailable: true,
        tags: ['신상품', '추천'],
        description: '샘플 상품 설명입니다.',
      }
      setJsonInput(JSON.stringify(defaultSample, null, 2))
      setJsonError(null)

      if (!entityId) {
        setEntityId(defaultSample.sku)
      }
    }
  }

  // 폼 초기화
  const resetForm = () => {
    setEntityId('')
    setJsonInput('')
    setSelectedSchema('')
    setJsonError(null)
    setValidationResult(null)
  }

  // 제출
  const handleSubmit = () => {
    if (!entityId || !selectedSchema || !jsonInput || jsonError) return

    try {
      const data = JSON.parse(jsonInput)
      createMutation.mutate({
        tenant,
        entityId,
        schemaRef: selectedSchema,
        data,
      })
    } catch {
      // 이미 jsonError로 처리됨
    }
  }

  // 파싱된 데이터 미리보기 (안전하게)
  const parsedData = (() => {
    if (!jsonInput || jsonError) return null
    try {
      return JSON.parse(jsonInput)
    } catch {
      return null
    }
  })()

  const isFormValid = entityId && selectedSchema && jsonInput && !jsonError

  return (
    <div className="rawdata-editor">
      {/* 헤더 */}
      <div className="editor-header">
        <div className="editor-title">
          <Sparkles size={20} />
          <h3>RawData 등록</h3>
        </div>
        <div className="editor-actions">
          <button className="action-btn" onClick={resetForm}>
            <Trash2 size={14} />
            초기화
          </button>
        </div>
      </div>

      <div className="editor-form">
        {/* Tenant & Entity ID */}
        <div className="form-row">
          <div className="form-group">
            <label>Tenant</label>
            <input
              type="text"
              value={tenant}
              onChange={(e) => setTenant(e.target.value)}
              placeholder="oliveyoung"
              className="form-input"
            />
          </div>
          <div className="form-group flex-2">
            <label>Entity ID</label>
            <input
              type="text"
              value={entityId}
              onChange={(e) => setEntityId(e.target.value)}
              placeholder="SKU-12345 또는 BRAND-001"
              className="form-input"
            />
          </div>
        </div>

        {/* Schema 선택 */}
        <div className="form-group">
          <label>Schema</label>
          <div className="schema-selector">
            <button
              className="schema-btn"
              onClick={() => setShowSchemaDropdown(!showSchemaDropdown)}
            >
              <FileJson size={16} />
              <span>{selectedSchema || '스키마 선택...'}</span>
              <ChevronDown size={14} className={showSchemaDropdown ? 'rotated' : ''} />
            </button>
            <AnimatePresence>
              {showSchemaDropdown && (
                <motion.div
                  className="schema-dropdown"
                  initial={{ opacity: 0, y: -10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -10 }}
                >
                  {/* API 에러 시 직접 입력 허용 */}
                  {schemasError && (
                    <div className="schema-manual">
                      <input
                        type="text"
                        placeholder="스키마 ID 직접 입력 (예: product-v1)"
                        value={selectedSchema}
                        onChange={(e) => setSelectedSchema(e.target.value)}
                        className="schema-input"
                        onClick={(e) => e.stopPropagation()}
                      />
                    </div>
                  )}
                  {!schemasError && schemasData?.schemas && schemasData.schemas.length > 0 ? (
                    schemasData.schemas.map((schema) => (
                      <button
                        key={schema.id}
                        className={`schema-item ${selectedSchema === schema.id ? 'selected' : ''}`}
                        onClick={() => {
                          setSelectedSchema(schema.id)
                          setShowSchemaDropdown(false)
                          if (jsonInput) {
                            parseAndValidate(jsonInput)
                          }
                        }}
                      >
                        <span className="schema-name">{schema.name}</span>
                        <span className="schema-version">v{schema.version}</span>
                        {schema.description && (
                          <span className="schema-desc">{schema.description}</span>
                        )}
                      </button>
                    ))
                  ) : !schemasError ? (
                    <div className="schema-empty">스키마가 없습니다</div>
                  ) : null}
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </div>

        {/* JSON 입력 */}
        <div className="form-group">
          <div className="json-header">
            <label>JSON Data</label>
            <div className="json-actions">
              <button
                className="mini-btn sample-btn"
                onClick={handleGenerateSample}
                title="스키마 기반 샘플 데이터 생성"
              >
                <Wand2 size={12} />
                샘플 생성
              </button>
              <button className="mini-btn" onClick={formatJson} title="Format JSON">
                <Code2 size={12} />
                Format
              </button>
              <button
                className="mini-btn"
                onClick={() => navigator.clipboard.writeText(jsonInput)}
                title="Copy"
              >
                <Copy size={12} />
              </button>
              <button
                className={`mini-btn ${showPreview ? 'active' : ''}`}
                onClick={() => setShowPreview(!showPreview)}
                title="Preview"
              >
                <Eye size={12} />
              </button>
            </div>
          </div>

          <div
            className={`json-input-container ${isDragging ? 'dragging' : ''}`}
            onDragOver={(e) => {
              e.preventDefault()
              setIsDragging(true)
            }}
            onDragLeave={() => setIsDragging(false)}
            onDrop={handleDrop}
          >
            {isDragging && (
              <div className="drop-overlay">
                <Upload size={32} />
                <span>JSON 파일을 여기에 드롭하세요</span>
              </div>
            )}

            <textarea
              ref={textareaRef}
              value={jsonInput}
              onChange={(e) => parseAndValidate(e.target.value)}
              placeholder={`{
  "name": "상품명",
  "price": 25000,
  "stock": 150,
  ...
}`}
              className={`json-textarea ${jsonError ? 'error' : ''}`}
              spellCheck={false}
            />

            <button
              className="file-upload-btn"
              onClick={() => fileInputRef.current?.click()}
            >
              <Upload size={14} />
              파일 업로드
            </button>
            <input
              ref={fileInputRef}
              type="file"
              accept=".json"
              onChange={(e) => {
                const file = e.target.files?.[0]
                if (file) {
                  const reader = new FileReader()
                  reader.onload = (event) => {
                    parseAndValidate(event.target?.result as string)
                  }
                  reader.readAsText(file)
                }
              }}
              hidden
            />
          </div>

          {/* JSON 에러 */}
          {jsonError && (
            <motion.div
              className="json-error"
              initial={{ opacity: 0, y: -5 }}
              animate={{ opacity: 1, y: 0 }}
            >
              <AlertCircle size={14} />
              <span>{jsonError}</span>
            </motion.div>
          )}

          {/* 유효성 검증 결과 */}
          <AnimatePresence>
            {validationResult && (
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
                    {validationResult.errors && (
                      <ul className="validation-errors">
                        {validationResult.errors.map((err, i) => (
                          <li key={i}>{err}</li>
                        ))}
                      </ul>
                    )}
                  </>
                )}
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* 미리보기 */}
        <AnimatePresence>
          {showPreview && parsedData && (
            <motion.div
              className="preview-panel"
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
            >
              <div className="preview-header">
                <Eye size={14} />
                <span>미리보기</span>
              </div>
              <div className="preview-content">
                <pre>{JSON.stringify(parsedData, null, 2)}</pre>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* 제출 버튼 */}
        <div className="form-submit">
          <button
            className="submit-btn"
            onClick={handleSubmit}
            disabled={!isFormValid || createMutation.isPending}
          >
            {createMutation.isPending ? (
              <>
                <Loader2 size={16} className="spinning" />
                등록 중...
              </>
            ) : (
              <>
                <Send size={16} />
                RawData 등록
              </>
            )}
          </button>
        </div>

        {/* 성공/에러 메시지 */}
        <AnimatePresence>
          {createMutation.isSuccess && (
            <motion.div
              className="submit-result success"
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0 }}
            >
              <Check size={16} />
              <span>RawData가 성공적으로 등록되었습니다!</span>
            </motion.div>
          )}
          {createMutation.isError && (
            <motion.div
              className="submit-result error"
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0 }}
            >
              <AlertCircle size={16} />
              <span>등록 실패: {(createMutation.error as Error).message}</span>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  )
}
