import { motion } from 'framer-motion'
import { ChevronRight, Clock, Database, Layers } from 'lucide-react'
import type { DataTableType } from './types'

interface DataTableRowProps {
  entry: {
    entityId: string
    version: number
    schemaRef: string
    updatedAt?: string
  }
  type: DataTableType
  index: number
  onSelect: (entityId: string, type: DataTableType) => void
}

export function DataTableRow({ entry, type, index, onSelect }: DataTableRowProps) {
  const Icon = type === 'rawdata' ? Database : Layers

  return (
    <motion.tr
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.02 }}
      onClick={() => onSelect(entry.entityId, type)}
    >
      <td className="entity-cell">
        <Icon size={14} />
        <span>{entry.entityId}</span>
      </td>
      <td className="version-cell">
        <span className="version-badge">v{entry.version}</span>
      </td>
      <td className="schema-cell">{entry.schemaRef.split('/').pop()}</td>
      <td className="time-cell">
        <Clock size={12} />
        {entry.updatedAt ? new Date(entry.updatedAt).toLocaleDateString() : '-'}
      </td>
      <td className="action-cell">
        <ChevronRight size={14} />
      </td>
    </motion.tr>
  )
}
