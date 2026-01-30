import { useCallback, useEffect, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import {
  ChevronRight,
  Code2,
  Command,
  Database,
  FileCode2,
  Layers,
  PanelLeftClose,
  PanelRightClose,
  Play,
  RotateCcw,
  Share2,
  Zap,
} from 'lucide-react'
import { YamlEditor } from './components/YamlEditor/YamlEditor'
import { SampleInput } from './components/SampleInput/SampleInput'
import { PreviewPanel } from './components/Preview/PreviewPanel'
import { usePlayground } from './hooks/usePlayground'
import './PlaygroundPage.css'

type ContractType = 'RULESET' | 'VIEW_DEFINITION' | 'SINK_RULE' | 'ENTITY_SCHEMA'

const CONTRACT_TYPES: { type: ContractType; label: string; icon: React.ElementType; color: string }[] = [
  { type: 'RULESET', label: 'RuleSet', icon: Layers, color: '#6366f1' },
  { type: 'VIEW_DEFINITION', label: 'ViewDef', icon: Database, color: '#10b981' },
  { type: 'SINK_RULE', label: 'SinkRule', icon: Zap, color: '#f59e0b' },
  { type: 'ENTITY_SCHEMA', label: 'Schema', icon: FileCode2, color: '#ec4899' },
]

const TEMPLATES: Record<ContractType, { yaml: string; sample: string }> = {
  RULESET: {
    yaml: `kind: RULESET
id: my_ruleset
version: "1.0.0"
entityType: PRODUCT
slices:
  - type: CORE
    buildRules:
      passThrough: ["id", "name", "price"]
  - type: SUMMARY
    buildRules:
      mapFields:
        name: displayName
        price: displayPrice`,
    sample: `{
  "id": "SKU-001",
  "name": "상품명",
  "price": 10000,
  "brandId": "BRAND-001",
  "isActive": true
}`,
  },
  VIEW_DEFINITION: {
    yaml: `kind: VIEW_DEFINITION
id: product_view
version: "1.0.0"
viewName: ProductFullView
primaryEntity: PRODUCT
compose:
  - sliceRef: CORE
    from: PRODUCT
  - sliceRef: SUMMARY
    from: PRODUCT
    optional: true`,
    sample: `{
  "entityKey": "SKU-001",
  "entityType": "PRODUCT"
}`,
  },
  SINK_RULE: {
    yaml: `kind: SINK_RULE
id: product_sink
version: "1.0.0"
viewRef: ProductFullView
sink:
  type: WEBHOOK
  endpoint: https://api.example.com/sync
  headers:
    Authorization: "Bearer \${env.API_TOKEN}"
  retryPolicy:
    maxRetries: 3
    backoffMs: 1000`,
    sample: `{
  "viewName": "ProductFullView",
  "entityKey": "SKU-001"
}`,
  },
  ENTITY_SCHEMA: {
    yaml: `kind: ENTITY_SCHEMA
id: product_schema
version: "1.0.0"
entityType: PRODUCT
fields:
  - name: id
    type: string
    required: true
  - name: name
    type: string
    required: true
  - name: price
    type: number
    required: true
  - name: brandId
    type: string
    required: false`,
    sample: `{
  "id": "SKU-001",
  "name": "상품명",
  "price": 10000
}`,
  },
}

export function PlaygroundPage() {
  const {
    yaml,
    sampleData,
    validationResult,
    simulationResult,
    diffResult,
    presets,
    isValidating,
    isSimulating,
    setYaml,
    setSampleData,
    runSimulation,
    applyPreset,
    reset,
  } = usePlayground()

  const [activeType, setActiveType] = useState<ContractType>('RULESET')
  const [leftPanelWidth, setLeftPanelWidth] = useState(50)
  const [sampleHeight, setSampleHeight] = useState(250)
  const [isResizingH, setIsResizingH] = useState(false)
  const [isResizingV, setIsResizingV] = useState(false)
  const [showSampleInput, setShowSampleInput] = useState(true)
  const containerRef = useRef<HTMLDivElement>(null)
  const editorPanelRef = useRef<HTMLDivElement>(null)

  // Contract 타입 변경 시 템플릿 적용
  const handleTypeChange = useCallback((type: ContractType) => {
    setActiveType(type)
    setYaml(TEMPLATES[type].yaml)
    setSampleData(TEMPLATES[type].sample)
  }, [setYaml, setSampleData])

  // 키보드 단축키 (capture: true로 Monaco Editor보다 먼저 캡처)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
        e.preventDefault()
        e.stopPropagation()
        runSimulation()
      }
      if ((e.metaKey || e.ctrlKey) && e.key === 's') {
        e.preventDefault()
        e.stopPropagation()
        // Save 로직
      }
      if ((e.metaKey || e.ctrlKey) && e.key === 'b') {
        e.preventDefault()
        e.stopPropagation()
        setShowSampleInput(prev => !prev)
      }
    }

    window.addEventListener('keydown', handleKeyDown, true)
    return () => window.removeEventListener('keydown', handleKeyDown, true)
  }, [runSimulation])

  // 수평 리사이즈 핸들러 (좌/우 패널)
  const handleHorizontalMouseDown = useCallback(() => {
    setIsResizingH(true)
  }, [])

  // 수직 리사이즈 핸들러 (YAML/Sample)
  const handleVerticalMouseDown = useCallback(() => {
    setIsResizingV(true)
  }, [])

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      if (isResizingH && containerRef.current) {
        const rect = containerRef.current.getBoundingClientRect()
        const newWidth = ((e.clientX - rect.left) / rect.width) * 100
        setLeftPanelWidth(Math.min(Math.max(newWidth, 25), 75))
      }
      if (isResizingV && editorPanelRef.current) {
        const rect = editorPanelRef.current.getBoundingClientRect()
        const newHeight = rect.bottom - e.clientY
        setSampleHeight(Math.min(Math.max(newHeight, 100), 500))
      }
    }

    const handleMouseUp = () => {
      setIsResizingH(false)
      setIsResizingV(false)
    }

    if (isResizingH || isResizingV) {
      window.addEventListener('mousemove', handleMouseMove)
      window.addEventListener('mouseup', handleMouseUp)
    }

    return () => {
      window.removeEventListener('mousemove', handleMouseMove)
      window.removeEventListener('mouseup', handleMouseUp)
    }
  }, [isResizingH, isResizingV])

  const handleShare = useCallback(() => {
    const state = { yaml, sampleData, type: activeType }
    const encoded = btoa(encodeURIComponent(JSON.stringify(state)))
    const url = `${window.location.origin}${window.location.pathname}?state=${encoded}`
    navigator.clipboard.writeText(url)
  }, [yaml, sampleData, activeType])

  return (
    <div className="playground-ide">
      {/* IDE 헤더 */}
      <header className="playground-header">
        <div className="header-left">
          <div className="header-title">
            <Code2 size={18} />
            <span>Contract Playground</span>
          </div>
          <span className="header-hint">
            <Command size={12} />+Enter 실행
          </span>
        </div>

        <div className="header-tabs">
          {CONTRACT_TYPES.map(({ type, label, icon: Icon, color }) => (
            <button
              key={type}
              className={`header-tab ${activeType === type ? 'active' : ''}`}
              onClick={() => handleTypeChange(type)}
              style={{ '--tab-color': color } as React.CSSProperties}
            >
              <Icon size={14} />
              <span>{label}</span>
            </button>
          ))}
        </div>

        <div className="header-actions">
          <button
            className="action-btn primary"
            onClick={runSimulation}
            disabled={isSimulating}
          >
            <Play size={14} />
            <span>{isSimulating ? '실행중...' : '실행'}</span>
          </button>

          <button className="action-btn" onClick={handleShare} title="공유 링크 복사">
            <Share2 size={14} />
          </button>

          <button className="action-btn" onClick={reset} title="초기화">
            <RotateCcw size={14} />
          </button>
        </div>
      </header>

      {/* IDE 메인 콘텐츠 */}
      <div className="playground-main" ref={containerRef}>
        {/* 왼쪽: 에디터 패널 */}
        <div
          className="editor-panel"
          style={{ width: `${leftPanelWidth}%` }}
          ref={editorPanelRef}
        >
          <div className="editor-section yaml-section" style={{ flex: 1, minHeight: 0 }}>
            <div className="section-header">
              <div className="section-title">
                <FileCode2 size={14} />
                <span>Contract YAML</span>
              </div>
              <div className="section-actions">
                <button
                  className="section-action"
                  onClick={() => setShowSampleInput(!showSampleInput)}
                  title={showSampleInput ? '샘플 입력 숨기기' : '샘플 입력 표시'}
                >
                  {showSampleInput ? <PanelLeftClose size={14} /> : <PanelRightClose size={14} />}
                </button>
              </div>
            </div>
            <div className="editor-content">
              <YamlEditor
                value={yaml}
                onChange={setYaml}
                errors={validationResult?.errors}
                height="100%"
              />
            </div>
          </div>

          <AnimatePresence>
            {showSampleInput && (
              <>
                {/* 수직 리사이즈 핸들 */}
                <div
                  className={`resize-handle-v ${isResizingV ? 'active' : ''}`}
                  onMouseDown={handleVerticalMouseDown}
                >
                  <div className="resize-line-v" />
                </div>
                <motion.div
                  className="editor-section sample-section"
                  initial={{ height: 0, opacity: 0 }}
                  animate={{ height: sampleHeight, opacity: 1 }}
                  exit={{ height: 0, opacity: 0 }}
                  transition={{ duration: 0.2 }}
                  style={{ height: sampleHeight, flexShrink: 0 }}
                >
                  <SampleInput
                    value={sampleData}
                    onChange={setSampleData}
                    presets={presets}
                    onApplyPreset={applyPreset}
                    height="100%"
                  />
                </motion.div>
              </>
            )}
          </AnimatePresence>
        </div>

        {/* 수평 리사이즈 핸들 */}
        <div
          className={`resize-handle ${isResizingH ? 'active' : ''}`}
          onMouseDown={handleHorizontalMouseDown}
        >
          <div className="resize-line" />
        </div>

        {/* 오른쪽: Preview 패널 */}
        <div
          className="preview-panel-container"
          style={{ width: `${100 - leftPanelWidth}%` }}
        >
          <PreviewPanel
            validationResult={validationResult}
            simulationResult={simulationResult}
            diffResult={diffResult}
            isValidating={isValidating}
            isSimulating={isSimulating}
            contractType={activeType}
          />
        </div>
      </div>

      {/* 상태 바 */}
      <footer className="playground-statusbar">
        <div className="statusbar-left">
          <span className="status-item">
            <ChevronRight size={12} />
            {activeType}
          </span>
          {validationResult && (
            <span className={`status-item ${validationResult.valid ? 'success' : 'error'}`}>
              {validationResult.valid ? '검증 통과' : `${validationResult.errors.length}개 오류`}
            </span>
          )}
        </div>
        <div className="statusbar-right">
          <span className="status-item">
            Lines: {yaml.split('\n').length}
          </span>
          <span className="status-item">
            YAML
          </span>
        </div>
      </footer>
    </div>
  )
}
