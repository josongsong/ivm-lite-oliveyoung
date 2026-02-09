import { Button } from '@/shared/ui'

interface EditorHeaderProps {
  kind?: string
  id?: string
  onSimulate?: () => void
  onSave?: () => void
}

export function EditorHeader({ kind, id, onSimulate, onSave }: EditorHeaderProps) {
  return (
    <header className="contract-editor__header">
      <div className="contract-editor__breadcrumb">
        <span className="contract-editor__breadcrumb-kind">{kind ?? 'NEW'}</span>
        <span className="contract-editor__breadcrumb-separator">/</span>
        <span className="contract-editor__breadcrumb-id">{id ?? 'untitled'}</span>
      </div>
      <div className="contract-editor__actions">
        <Button variant="secondary" onClick={onSimulate}>
          Simulate
        </Button>
        <Button variant="primary" onClick={onSave}>
          Save
        </Button>
      </div>
    </header>
  )
}
