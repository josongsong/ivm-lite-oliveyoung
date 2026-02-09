import { motion } from 'framer-motion'
import { Layers } from 'lucide-react'

interface SliceTypesSectionProps {
  slicesByType: Record<string, number>
}

export function SliceTypesSection({ slicesByType }: SliceTypesSectionProps) {
  if (Object.keys(slicesByType).length === 0) return null

  return (
    <motion.div
      className="slice-types-section"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.4 }}
    >
      <div className="panel-header">
        <Layers size={18} />
        <h3>Slice Types</h3>
      </div>
      <div className="slice-chips">
        {Object.entries(slicesByType).map(([type, count]) => (
          <div key={type} className="slice-chip">
            <span className="chip-type">{type}</span>
            <span className="chip-count">{count.toLocaleString()}</span>
          </div>
        ))}
      </div>
    </motion.div>
  )
}
