import Editor from '@monaco-editor/react'
import './YamlViewer.css'

export interface YamlViewerProps {
  value: string
  height?: string
  className?: string
}

/**
 * Read-only YAML Viewer with Monaco Editor
 *
 * Phase 0-5: Contract DX Enhancement
 * - Syntax highlighting
 * - Line numbers
 * - Code folding
 * - Search (Cmd+F)
 */
export function YamlViewer({ value, height = '400px', className = '' }: YamlViewerProps) {
  return (
    <div className={`yaml-viewer ${className}`}>
      <Editor
        height={height}
        language="yaml"
        theme="vs-dark"
        value={value}
        options={{
          readOnly: true,
          minimap: { enabled: false },
          fontSize: 13,
          lineHeight: 20,
          tabSize: 2,
          wordWrap: 'on',
          scrollBeyondLastLine: false,
          automaticLayout: true,
          padding: { top: 12, bottom: 12 },
          renderLineHighlight: 'none',
          lineNumbers: 'on',
          folding: true,
          foldingHighlight: true,
          scrollbar: {
            vertical: 'auto',
            horizontal: 'auto',
            verticalScrollbarSize: 8,
            horizontalScrollbarSize: 8,
          },
          overviewRulerBorder: false,
          hideCursorInOverviewRuler: true,
          contextmenu: true,
          selectionHighlight: true,
          occurrencesHighlight: 'off',
          cursorStyle: 'line',
          cursorBlinking: 'smooth',
        }}
      />
    </div>
  )
}
