import { AnimatePresence, motion } from 'framer-motion'
import { FileCode2, PanelLeftClose, PanelRightClose } from 'lucide-react'
import { IconButton, YamlEditor } from '@/shared/ui'
import type { PresetItem, ValidationError } from '../types/playground'
import { SampleInput } from './SampleInput/SampleInput'

interface EditorPanelProps {
  yaml: string
  onYamlChange: (value: string) => void
  validationErrors: ValidationError[] | undefined
  sampleData: string
  onSampleDataChange: (value: string) => void
  presets: PresetItem[]
  onApplyPreset: (preset: PresetItem) => void
  showSampleInput: boolean
  onToggleSampleInput: () => void
  sampleHeight: number
  isResizingV: boolean
  onVerticalMouseDown: () => void
}

export function EditorPanel({
  yaml,
  onYamlChange,
  validationErrors,
  sampleData,
  onSampleDataChange,
  presets,
  onApplyPreset,
  showSampleInput,
  onToggleSampleInput,
  sampleHeight,
  isResizingV,
  onVerticalMouseDown,
}: EditorPanelProps) {
  return (
    <>
      <div className="editor-section yaml-section" style={{ flex: 1, minHeight: 0 }}>
        <div className="section-header">
          <div className="section-title">
            <FileCode2 size={14} />
            <span>Contract YAML</span>
          </div>
          <div className="section-actions">
            <IconButton
              icon={showSampleInput ? PanelLeftClose : PanelRightClose}
              iconSize={14}
              variant="ghost"
              size="sm"
              onClick={onToggleSampleInput}
              tooltip={showSampleInput ? '샘플 입력 숨기기' : '샘플 입력 표시'}
              aria-label={showSampleInput ? 'Hide sample input' : 'Show sample input'}
            />
          </div>
        </div>
        <div className="editor-content">
          <YamlEditor
            value={yaml}
            onChange={onYamlChange}
            errors={validationErrors?.map((e) => ({
              line: e.line,
              column: e.column,
              message: e.message,
              severity: e.severity,
            }))}
            height="100%"
          />
        </div>
      </div>

      <AnimatePresence>
        {showSampleInput ? (
          <>
            <div role="separator" aria-orientation="horizontal" className={`resize-handle-v ${isResizingV ? 'active' : ''}`} onMouseDown={onVerticalMouseDown}>
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
                onChange={onSampleDataChange}
                presets={presets}
                onApplyPreset={onApplyPreset}
                height="100%"
              />
            </motion.div>
          </>
        ) : null}
      </AnimatePresence>
    </>
  )
}
