import Editor from '@monaco-editor/react'
import { useCallback, useMemo } from 'react'
import { ChevronDown, FileJson } from 'lucide-react'
import type { PresetItem } from '../../types/playground'
import './SampleInput.css'

interface SampleInputProps {
  value: string
  onChange: (value: string) => void
  presets: PresetItem[]
  onApplyPreset: (preset: PresetItem) => void
  height?: string
}

export function SampleInput({
  value,
  onChange,
  presets,
  onApplyPreset,
  height = '100%',
}: SampleInputProps) {
  const handleChange = useCallback((value: string | undefined) => {
    onChange(value ?? '')
  }, [onChange])

  const isValid = useMemo(() => {
    try {
      JSON.parse(value)
      return true
    } catch {
      return false
    }
  }, [value])

  return (
    <div className="sample-input-v2">
      <div className="sample-input-header">
        <div className="header-left">
          <FileJson size={14} />
          <span className="sample-title">Sample Input</span>
          <span className={`json-status ${isValid ? 'valid' : 'invalid'}`}>
            {isValid ? 'Valid' : 'Invalid'}
          </span>
        </div>

        {presets.length > 0 && (
          <div className="presets-dropdown">
            <select
              className="preset-select"
              onChange={(e) => {
                const preset = presets.find(p => p.id === e.target.value)
                if (preset) {
                  onApplyPreset(preset)
                }
              }}
              defaultValue=""
            >
              <option value="" disabled>
                Presets
              </option>
              {presets.map((preset) => (
                <option key={preset.id} value={preset.id}>
                  {preset.name}
                </option>
              ))}
            </select>
            <ChevronDown size={12} className="dropdown-icon" />
          </div>
        )}
      </div>

      <div className="sample-editor-wrapper">
        <Editor
          height={height}
          language="json"
          theme="vs-dark"
          value={value}
          onChange={handleChange}
          options={{
            minimap: { enabled: false },
            fontSize: 12,
            lineHeight: 18,
            tabSize: 2,
            wordWrap: 'on',
            scrollBeyondLastLine: false,
            formatOnPaste: true,
            formatOnType: true,
            lineNumbers: 'off',
            glyphMargin: false,
            folding: false,
            lineDecorationsWidth: 8,
            padding: { top: 8, bottom: 8 },
          }}
        />
      </div>
    </div>
  )
}
