import { useMemo, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Braces, Brackets, Check, ChevronDown, ChevronRight, Copy, Hash, ToggleLeft, Type } from 'lucide-react'
import './JsonViewer.css'

interface JsonViewerProps {
  data: unknown
  initialExpanded?: boolean
  maxDepth?: number
  searchTerm?: string
}

type JsonValueType = 'string' | 'number' | 'boolean' | 'null' | 'object' | 'array'

function getValueType(value: unknown): JsonValueType {
  if (value === null) return 'null'
  if (Array.isArray(value)) return 'array'
  return typeof value as JsonValueType
}

const typeIcons: Record<JsonValueType, React.ElementType> = {
  string: Type,
  number: Hash,
  boolean: ToggleLeft,
  null: Type,
  object: Braces,
  array: Brackets,
}

export function JsonViewer({ data, initialExpanded = true, maxDepth = 10, searchTerm }: JsonViewerProps) {
  const [copiedPath, setCopiedPath] = useState<string | null>(null)

  const handleCopy = async (value: unknown, path: string) => {
    const text = typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value)
    await navigator.clipboard.writeText(text)
    setCopiedPath(path)
    setTimeout(() => setCopiedPath(null), 2000)
  }

  return (
    <div className="json-viewer">
      <JsonNode
        keyName={null}
        value={data}
        depth={0}
        path="$"
        expanded={initialExpanded}
        maxDepth={maxDepth}
        searchTerm={searchTerm}
        onCopy={handleCopy}
        copiedPath={copiedPath}
      />
    </div>
  )
}

interface JsonNodeProps {
  keyName: string | null
  value: unknown
  depth: number
  path: string
  expanded: boolean
  maxDepth: number
  searchTerm?: string
  onCopy: (value: unknown, path: string) => void
  copiedPath: string | null
}

function JsonNode({
  keyName,
  value,
  depth,
  path,
  expanded: initialExpanded,
  maxDepth,
  searchTerm,
  onCopy,
  copiedPath,
}: JsonNodeProps) {
  const [isExpanded, setIsExpanded] = useState(depth < 2 ? initialExpanded : false)
  const valueType = getValueType(value)
  const isExpandable = valueType === 'object' || valueType === 'array'
  const TypeIcon = typeIcons[valueType]

  // 검색어 하이라이트
  const isHighlighted = useMemo(() => {
    if (!searchTerm) return false
    const searchLower = searchTerm.toLowerCase()
    if (keyName?.toLowerCase().includes(searchLower)) return true
    if (typeof value === 'string' && value.toLowerCase().includes(searchLower)) return true
    if (typeof value === 'number' && String(value).includes(searchTerm)) return true
    return false
  }, [keyName, value, searchTerm])

  // 자식 노드들
  const childNodes = useMemo(() => {
    if (!isExpandable || !isExpanded) return null
    if (depth >= maxDepth) return <span className="json-max-depth">...</span>

    const obj = value as Record<string, unknown>
    const entries = Array.isArray(value)
      ? value.map((v, i) => [String(i), v] as const)
      : Object.entries(obj)

    return entries.map(([k, v]) => (
      <JsonNode
        key={`${path}.${k}`}
        keyName={k}
        value={v}
        depth={depth + 1}
        path={`${path}.${k}`}
        expanded={initialExpanded}
        maxDepth={maxDepth}
        searchTerm={searchTerm}
        onCopy={onCopy}
        copiedPath={copiedPath}
      />
    ))
  }, [value, isExpanded, depth, maxDepth, path, searchTerm, onCopy, copiedPath, initialExpanded, isExpandable])

  // 값 렌더링
  const renderValue = () => {
    if (isExpandable) {
      const count = Array.isArray(value) ? value.length : Object.keys(value as object).length
      const label = valueType === 'array' ? `[${count}]` : `{${count}}`
      return <span className="json-preview">{label}</span>
    }

    if (value === null) return <span className="json-null">null</span>
    if (typeof value === 'boolean') return <span className="json-boolean">{String(value)}</span>
    if (typeof value === 'number') return <span className="json-number">{value}</span>
    if (typeof value === 'string') {
      const displayValue = value.length > 100 ? value.slice(0, 100) + '...' : value
      return <span className="json-string">"{displayValue}"</span>
    }
    return null
  }

  return (
    <div className={`json-node ${isHighlighted ? 'highlighted' : ''}`} style={{ paddingLeft: depth * 16 }}>
      <div className="json-node-header">
        {isExpandable && (
          <button
            className="json-toggle"
            onClick={() => setIsExpanded(!isExpanded)}
            aria-label={isExpanded ? 'Collapse' : 'Expand'}
          >
            {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
          </button>
        )}
        {!isExpandable && <span className="json-toggle-placeholder" />}

        <span className="json-type-icon">
          <TypeIcon size={12} />
        </span>

        {keyName !== null && (
          <>
            <span className="json-key">{keyName}</span>
            <span className="json-colon">:</span>
          </>
        )}

        {renderValue()}

        <button
          className={`json-copy ${copiedPath === path ? 'copied' : ''}`}
          onClick={() => onCopy(value, path)}
          title="Copy value"
        >
          {copiedPath === path ? <Check size={12} /> : <Copy size={12} />}
        </button>
      </div>

      <AnimatePresence>
        {isExpanded && childNodes && (
          <motion.div
            className="json-children"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.15 }}
          >
            {childNodes}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
