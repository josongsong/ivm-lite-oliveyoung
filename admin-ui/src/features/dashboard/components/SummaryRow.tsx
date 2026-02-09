import { motion } from 'framer-motion'
import { AlertTriangle, Database, FileCode2, Layers, Zap } from 'lucide-react'

interface SummaryRowProps {
  worker: { running: boolean }
  rawDataCount: number
  sliceCount: number
  contractCount: number
}

export function SummaryRow({ worker, rawDataCount, sliceCount, contractCount }: SummaryRowProps) {
  return (
    <motion.div
      className="summary-row"
      initial={{ opacity: 0, y: -10 }}
      animate={{ opacity: 1, y: 0 }}
    >
      <div className={`worker-status ${worker.running ? 'running' : 'stopped'}`}>
        {worker.running ? (
          <motion.div
            animate={{ scale: [1, 1.2, 1] }}
            transition={{ repeat: Infinity, duration: 2 }}
          >
            <Zap size={18} />
          </motion.div>
        ) : (
          <AlertTriangle size={18} />
        )}
        <span>Worker {worker.running ? 'Active' : 'Stopped'}</span>
      </div>
      <div className="summary-stats">
        <span><Database size={14} /> {rawDataCount.toLocaleString()} RawData</span>
        <span><Layers size={14} /> {sliceCount.toLocaleString()} Slices</span>
        <span><FileCode2 size={14} /> {contractCount} Contracts</span>
      </div>
    </motion.div>
  )
}
