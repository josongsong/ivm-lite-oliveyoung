/**
 * EditorLoading Component
 *
 * Contract 로딩 상태 표시
 */
export function EditorLoading() {
  return (
    <div className="contract-editor contract-editor--loading">
      <div className="contract-editor__spinner" />
      <span>Contract 로딩 중...</span>
    </div>
  )
}
