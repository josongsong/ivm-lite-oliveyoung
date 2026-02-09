import { useCallback, useRef } from 'react'
import type { Monaco, OnMount } from '@monaco-editor/react'
import type { editor } from 'monaco-editor'

interface UseEditorCallbacksOptions {
  onPositionChange: (line: number, column: number) => void
}

export function useEditorCallbacks({ onPositionChange }: UseEditorCallbacksOptions) {
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null)
  const monacoRef = useRef<Monaco | null>(null)

  const handleEditorMount: OnMount = useCallback(
    (editor, monaco) => {
      editorRef.current = editor
      monacoRef.current = monaco

      editor.onDidChangeCursorPosition((e) => {
        onPositionChange(e.position.lineNumber, e.position.column)
      })

      monaco.languages.setLanguageConfiguration('yaml', {
        comments: { lineComment: '#' },
        brackets: [
          ['{', '}'],
          ['[', ']'],
        ],
        autoClosingPairs: [
          { open: '{', close: '}' },
          { open: '[', close: ']' },
          { open: '"', close: '"' },
          { open: "'", close: "'" },
        ],
      })
    },
    [onPositionChange]
  )

  const goToLine = useCallback((line: number, column: number) => {
    if (!editorRef.current) return
    editorRef.current.setPosition({ lineNumber: line, column })
    editorRef.current.focus()
    editorRef.current.revealLineInCenter(line)
  }, [])

  const applyFix = useCallback((line: number, replacement: string) => {
    if (!editorRef.current) return
    const model = editorRef.current.getModel()
    if (!model) return

    const lineContent = model.getLineContent(line)
    const indentMatch = lineContent.match(/^(\s*)/)
    const indent = indentMatch ? indentMatch[1] : ''

    editorRef.current.executeEdits('quick-fix', [
      {
        range: {
          startLineNumber: line,
          startColumn: 1,
          endLineNumber: line,
          endColumn: lineContent.length + 1,
        },
        text: `${indent}${replacement}`,
      },
    ])
  }, [])

  return {
    editorRef,
    monacoRef,
    handleEditorMount,
    goToLine,
    applyFix,
  }
}
