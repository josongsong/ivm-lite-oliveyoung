import Editor, { type Monaco, type OnMount } from '@monaco-editor/react'
import { useCallback, useEffect, useRef } from 'react'
import type { editor } from 'monaco-editor'
import type { ValidationError } from '../../types/playground'
import './YamlEditor.css'

interface YamlEditorProps {
  value: string
  onChange: (value: string) => void
  errors?: ValidationError[]
  height?: string
}

export function YamlEditor({ value, onChange, errors = [], height = '100%' }: YamlEditorProps) {
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null)
  const monacoRef = useRef<Monaco | null>(null)

  const handleEditorMount: OnMount = useCallback((editor, monaco) => {
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

    // Cmd+Enter 단축키 (시뮬레이션 실행)
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
      const event = new CustomEvent('playground:run')
      window.dispatchEvent(event)
    })

    // Cmd+S 단축키 (저장 - 기본 동작 방지)
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
      const event = new CustomEvent('playground:save')
      window.dispatchEvent(event)
    })
  }, [])

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
    <div className="yaml-editor-wrapper">
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
