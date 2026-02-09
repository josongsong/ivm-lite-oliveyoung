import { Check, Clock, Copy, Database, FileCode2, GitCompare, Hash } from 'lucide-react'
import { Button } from '@/shared/ui'

interface RawDataEntry {
  entityId: string
  schemaRef: string
  updatedAt: string
  hash: string
  version: number
  data: unknown
}

interface RawDataHeaderProps {
  entry: RawDataEntry
  copied: boolean
  showDiff: boolean
  onCopy: () => void
  onToggleDiff: () => void
}

export function RawDataHeader({ entry, copied, showDiff, onCopy, onToggleDiff }: RawDataHeaderProps) {
  return (
    <div className="rawdata-header">
      <div className="rawdata-meta">
        <div className="meta-item">
          <Database size={14} />
          <span className="meta-label">Entity</span>
          <span className="meta-value">{entry.entityId}</span>
        </div>
        <div className="meta-item">
          <FileCode2 size={14} />
          <span className="meta-label">Schema</span>
          <span className="meta-value">{entry.schemaRef}</span>
        </div>
        <div className="meta-item">
          <Clock size={14} />
          <span className="meta-label">Updated</span>
          <span className="meta-value">
            {new Date(entry.updatedAt).toLocaleString()}
          </span>
        </div>
        <div className="meta-item">
          <Hash size={14} />
          <span className="meta-label">Hash</span>
          <span className="meta-value mono">{entry.hash.slice(0, 12)}...</span>
        </div>
      </div>

      <div className="rawdata-actions">
        <Button
          variant={copied ? 'primary' : 'secondary'}
          size="sm"
          onClick={onCopy}
        >
          {copied ? <Check size={14} /> : <Copy size={14} />}
          {copied ? 'Copied!' : 'Copy JSON'}
        </Button>
        <Button
          variant={showDiff ? 'primary' : 'secondary'}
          size="sm"
          onClick={onToggleDiff}
        >
          <GitCompare size={14} />
          Diff
        </Button>
      </div>
    </div>
  )
}
