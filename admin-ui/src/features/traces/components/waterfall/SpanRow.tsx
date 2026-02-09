import { motion } from 'framer-motion'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { IconButton } from '@/shared/ui'
import { formatDuration } from '@/shared/ui/formatters'
import type { TreeNode } from './spanUtils'
import { getSpanColor, getSpanIcon } from './spanUtils'

interface SpanRowProps {
  node: TreeNode
  hasChildren: boolean
  isSelected: boolean
  isHovered: boolean
  isExpanded: boolean
  totalDuration: number
  onSelect: () => void
  onToggle: (e: React.MouseEvent) => void
  onMouseEnter: () => void
  onMouseLeave: () => void
}

export function SpanRow({
  node,
  hasChildren,
  isSelected,
  isHovered,
  isExpanded,
  totalDuration,
  onSelect,
  onToggle,
  onMouseEnter,
  onMouseLeave,
}: SpanRowProps) {
  const { span } = node
  const color = getSpanColor(span)

  const barWidth = Math.max(
    ((span.relativeEnd - span.relativeStart) / totalDuration) * 100,
    0.5
  )
  const barLeft = (span.relativeStart / totalDuration) * 100

  return (
    <motion.div
      className={`span-row ${isSelected ? 'selected' : ''} ${span.hasError ? 'error' : ''}`}
      initial={{ opacity: 0, height: 0 }}
      animate={{ opacity: 1, height: 'auto' }}
      exit={{ opacity: 0, height: 0 }}
      transition={{ duration: 0.15 }}
      onClick={onSelect}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
    >
      <div className="span-label" style={{ paddingLeft: `${node.depth * 16 + 8}px` }}>
        {hasChildren ? (
          <IconButton
            icon={isExpanded ? ChevronDown : ChevronRight}
            iconSize={14}
            variant="ghost"
            size="sm"
            className="expand-btn visible"
            onClick={onToggle}
            aria-label={isExpanded ? 'Collapse' : 'Expand'}
          />
        ) : (
          <span className="expand-btn" style={{ width: 24, display: 'inline-block' }} />
        )}

        <span className="span-icon" style={{ color: color.text }}>
          {getSpanIcon(span)}
        </span>

        <span className="span-name" title={span.name}>
          {span.name}
        </span>

        <span
          className="span-service-tag"
          style={{
            backgroundColor: color.bg,
            borderColor: color.border,
            color: color.text,
          }}
        >
          {span.type}
        </span>
      </div>

      <div className="span-timeline">
        <div
          className="span-bar"
          style={{
            left: `${barLeft}%`,
            width: `${barWidth}%`,
            backgroundColor: color.bg,
            borderColor: color.border,
            boxShadow: isSelected || isHovered ? `0 0 12px ${color.border}` : 'none',
          }}
        >
          <span className="span-duration">{formatDuration(span.duration)}</span>
        </div>
      </div>
    </motion.div>
  )
}
