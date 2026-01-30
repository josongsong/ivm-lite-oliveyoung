import { useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import {
  AlertTriangle,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  Copy,
  FileJson,
  Hash,
  Layers,
  Minus,
  Plus,
  RefreshCw,
  XCircle,
} from 'lucide-react'
import type {
  DiffItem,
  DiffResult,
  SimulatedSlice,
  SimulationResult,
  ValidationResult,
} from '../../types/playground'
import './Preview.css'

type ContractType = 'RULESET' | 'VIEW_DEFINITION' | 'SINK_RULE' | 'ENTITY_SCHEMA'

interface PreviewPanelProps {
  validationResult: ValidationResult | null
  simulationResult: SimulationResult | null
  diffResult: DiffResult | null
  isValidating: boolean
  isSimulating: boolean
  contractType?: ContractType
}

export function PreviewPanel({
  validationResult,
  simulationResult,
  diffResult,
  isSimulating,
  contractType = 'RULESET',
}: PreviewPanelProps) {
  const [activeTab, setActiveTab] = useState<'result' | 'validation' | 'diff'>('result')

  const hasDiff = diffResult !== null

  return (
    <div className="preview-panel-ide">
      {/* 탭 헤더 */}
      <div className="preview-tabs">
        <button
          className={`preview-tab ${activeTab === 'result' ? 'active' : ''}`}
          onClick={() => setActiveTab('result')}
        >
          <Layers size={14} />
          <span>결과</span>
          {isSimulating && <span className="tab-loader" />}
        </button>
        <button
          className={`preview-tab ${activeTab === 'validation' ? 'active' : ''}`}
          onClick={() => setActiveTab('validation')}
        >
          {validationResult?.valid ? (
            <CheckCircle2 size={14} className="icon-success" />
          ) : validationResult ? (
            <XCircle size={14} className="icon-error" />
          ) : (
            <CheckCircle2 size={14} />
          )}
          <span>검증</span>
          {validationResult && !validationResult.valid && (
            <span className="tab-badge error">{validationResult.errors.length}</span>
          )}
        </button>
        <button
          className={`preview-tab ${activeTab === 'diff' ? 'active' : ''}`}
          onClick={() => setActiveTab('diff')}
          disabled={!hasDiff}
        >
          <RefreshCw size={14} />
          <span>비교</span>
        </button>
      </div>

      {/* 콘텐츠 */}
      <div className="preview-body">
        <AnimatePresence mode="wait">
          {activeTab === 'result' && (
            <motion.div
              key="result"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="preview-content-wrapper"
            >
              {simulationResult ? (
                <SimulationSection result={simulationResult} />
              ) : (
                <EmptyState contractType={contractType} />
              )}
            </motion.div>
          )}

          {activeTab === 'validation' && (
            <motion.div
              key="validation"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="preview-content-wrapper"
            >
              <ValidationSection result={validationResult} />
            </motion.div>
          )}

          {activeTab === 'diff' && hasDiff && (
            <motion.div
              key="diff"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="preview-content-wrapper"
            >
              <DiffSection result={diffResult} />
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  )
}

function EmptyState({ contractType }: { contractType: ContractType }) {
  const descriptions: Record<ContractType, string> = {
    RULESET: 'RawData를 Slice로 변환하는 규칙을 정의합니다',
    VIEW_DEFINITION: '여러 Slice를 조합하여 View를 구성합니다',
    SINK_RULE: 'View 데이터를 외부 시스템으로 전송합니다',
    ENTITY_SCHEMA: '엔티티의 스키마를 정의합니다',
  }

  return (
    <div className="preview-empty-state">
      <div className="empty-icon">
        <FileJson size={40} strokeWidth={1.5} />
      </div>
      <h3>실행 결과가 여기에 표시됩니다</h3>
      <p>{descriptions[contractType]}</p>
      <div className="empty-hint">
        <kbd>Cmd</kbd> + <kbd>Enter</kbd> 로 실행
      </div>
    </div>
  )
}

function ValidationSection({ result }: { result: ValidationResult | null }) {
  if (!result) {
    return (
      <div className="validation-empty">
        <CheckCircle2 size={24} />
        <p>YAML 입력 시 자동으로 검증됩니다</p>
      </div>
    )
  }

  return (
    <div className="validation-content">
      <div className={`validation-status ${result.valid ? 'valid' : 'invalid'}`}>
        {result.valid ? (
          <>
            <CheckCircle2 size={20} />
            <span>검증 통과</span>
          </>
        ) : (
          <>
            <XCircle size={20} />
            <span>{result.errors.length}개의 오류 발견</span>
          </>
        )}
      </div>

      {result.errors.length > 0 && (
        <div className="validation-list">
          {result.errors.map((error, index) => (
            <div key={index} className={`validation-item ${error.severity}`}>
              <div className="validation-item-header">
                <span className="error-location">
                  L{error.line}:{error.column}
                </span>
                <span className={`error-badge ${error.severity}`}>
                  {error.severity}
                </span>
              </div>
              <p className="error-message">{error.message}</p>
            </div>
          ))}
        </div>
      )}

      {result.warnings.length > 0 && (
        <div className="warnings-list">
          <h4>경고</h4>
          {result.warnings.map((warning, index) => (
            <div key={index} className="warning-item">
              <AlertTriangle size={14} />
              <span>{warning}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function SimulationSection({
  result,
}: {
  result: SimulationResult
}) {
  if (result.errors.length > 0) {
    return (
      <div className="simulation-errors-view">
        <div className="errors-header">
          <XCircle size={20} />
          <span>시뮬레이션 실패</span>
        </div>
        <div className="errors-list">
          {result.errors.map((error, index) => (
            <div key={index} className="error-card">
              <span>{error}</span>
            </div>
          ))}
        </div>
      </div>
    )
  }

  return (
    <div className="simulation-result-view">
      <div className="result-summary">
        <div className="summary-stat">
          <span className="stat-value">{result.slices.length}</span>
          <span className="stat-label">슬라이스</span>
        </div>
        {result.rawDataPreview && (
          <>
            <div className="summary-stat">
              <span className="stat-value">{result.rawDataPreview.entityType}</span>
              <span className="stat-label">엔티티</span>
            </div>
          </>
        )}
      </div>

      <div className="slices-list">
        {result.slices.map((slice, index) => (
          <SliceCard key={index} slice={slice} />
        ))}
      </div>
    </div>
  )
}

function SliceCard({ slice }: { slice: SimulatedSlice }) {
  const [isExpanded, setIsExpanded] = useState(true)
  const [copied, setCopied] = useState(false)

  const handleCopy = () => {
    navigator.clipboard.writeText(slice.data)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  let parsedData: object | null = null
  try {
    parsedData = JSON.parse(slice.data)
  } catch {
    parsedData = null
  }

  return (
    <div className="slice-card-v2">
      <div className="slice-card-header" onClick={() => setIsExpanded(!isExpanded)}>
        <div className="slice-info">
          {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
          <span className="slice-type-badge">{slice.type}</span>
          <span className="slice-hash">
            <Hash size={10} />
            {slice.hash}
          </span>
        </div>
        <div className="slice-actions">
          <button className="slice-action-btn" onClick={(e) => { e.stopPropagation(); handleCopy() }}>
            <Copy size={12} />
            {copied ? '복사됨!' : '복사'}
          </button>
        </div>
      </div>

      <AnimatePresence>
        {isExpanded && (
          <motion.div
            className="slice-card-body"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.15 }}
          >
            <div className="slice-fields">
              {slice.fields.map((field, i) => (
                <span key={i} className="field-chip">{field}</span>
              ))}
            </div>
            <div className="slice-json">
              <pre>{JSON.stringify(parsedData, null, 2)}</pre>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

function DiffSection({ result }: { result: DiffResult }) {
  const hasChanges = result.added.length > 0 || result.removed.length > 0 || result.modified.length > 0

  return (
    <div className="diff-content">
      <div className="diff-summary-card">
        <span>{result.summary}</span>
      </div>

      {hasChanges ? (
        <div className="diff-list">
          {result.added.map((item, index) => (
            <DiffItemRow key={`added-${index}`} item={item} type="added" />
          ))}
          {result.removed.map((item, index) => (
            <DiffItemRow key={`removed-${index}`} item={item} type="removed" />
          ))}
          {result.modified.map((item, index) => (
            <DiffItemRow key={`modified-${index}`} item={item} type="modified" />
          ))}
        </div>
      ) : (
        <div className="diff-empty">
          <CheckCircle2 size={20} />
          <span>변경사항 없음</span>
        </div>
      )}
    </div>
  )
}

function DiffItemRow({ item, type }: { item: DiffItem; type: 'added' | 'removed' | 'modified' }) {
  const icons = {
    added: <Plus size={12} />,
    removed: <Minus size={12} />,
    modified: <RefreshCw size={12} />,
  }

  return (
    <div className={`diff-row ${type}`}>
      <span className="diff-icon">{icons[type]}</span>
      <span className="diff-path">{item.path}</span>
      {type === 'modified' && (
        <span className="diff-change">
          <span className="old">{item.oldValue}</span>
          <span className="arrow">→</span>
          <span className="new">{item.newValue}</span>
        </span>
      )}
      {type === 'added' && <span className="diff-value new">{item.newValue}</span>}
      {type === 'removed' && <span className="diff-value old">{item.oldValue}</span>}
    </div>
  )
}
