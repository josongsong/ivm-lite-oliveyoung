import Editor, { type Monaco, type OnMount } from '@monaco-editor/react'
import { useCallback, useEffect, useRef } from 'react'
import type { editor } from 'monaco-editor'
import './YamlEditor.css'

export interface YamlValidationError {
  line: number
  column: number
  message: string
  severity: 'error' | 'warning' | 'info'
}

export interface YamlEditorProps {
  value: string
  onChange: (value: string) => void
  errors?: YamlValidationError[]
  height?: string
  onRun?: () => void
  onSave?: () => void
  className?: string
}

export function YamlEditor({
  value,
  onChange,
  errors = [],
  height = '100%',
  onRun,
  onSave,
  className = '',
}: YamlEditorProps) {
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null)
  const monacoRef = useRef<Monaco | null>(null)

  const handleEditorMount: OnMount = useCallback(
    (editor, monaco) => {
      editorRef.current = editor
      monacoRef.current = monaco

      // 에디터 옵션 설정
      editor.updateOptions({
        minimap: { enabled: false },
        fontSize: 13,
        lineHeight: 20,
        tabSize: 2,
        wordWrap: 'on',
        scrollBeyondLastLine: false,
        automaticLayout: true,
        padding: { top: 8 },
        renderLineHighlight: 'gutter',
        cursorBlinking: 'smooth',
        smoothScrolling: true,
      })

      // Cmd+Enter 단축키 (실행)
      if (onRun) {
        editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
          onRun()
        })
      }

      // Cmd+S 단축키 (저장)
      if (onSave) {
        editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, (e) => {
          e.preventDefault()
          onSave()
        })
      }
    },
    [onRun, onSave]
  )

  // 에러 마커 표시
  useEffect(() => {
    if (!monacoRef.current || !editorRef.current) return

    const monaco = monacoRef.current
    const model = editorRef.current.getModel()
    if (!model) return

    const markers: editor.IMarkerData[] = errors.map((err) => ({
      severity: err.severity === 'error'
        ? monaco.MarkerSeverity.Error
        : err.severity === 'warning'
        ? monaco.MarkerSeverity.Warning
        : monaco.MarkerSeverity.Info,
      startLineNumber: err.line,
      startColumn: err.column,
      endLineNumber: err.line,
      endColumn: err.column + 10,
      message: err.message,
    }))

    monaco.editor.setModelMarkers(model, 'yaml-validation', markers)
  }, [errors])

  const handleChange = useCallback((value: string | undefined) => {
    onChange(value ?? '')
  }, [onChange])

  return (
    <div className={`yaml-editor-wrapper ${className}`}>
      <Editor
        height={height}
        language="yaml"
        theme="vs-dark"
        value={value}
        onChange={handleChange}
        onMount={handleEditorMount}
        options={{
          minimap: { enabled: false },
          fontSize: 13,
          lineHeight: 20,
          tabSize: 2,
          wordWrap: 'on',
          scrollBeyondLastLine: false,
          automaticLayout: true,
          padding: { top: 8 },
        }}
      />
    </div>
  )
}
