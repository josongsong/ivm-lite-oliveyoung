import { motion } from 'framer-motion'
import { ArrowRight, Database, Eye, Rocket, Scissors } from 'lucide-react'
import type { PipelineStage } from '@/shared/types'

interface PipelineStagesProps {
  stages: PipelineStage[]
  selectedStage: string | null
  onSelectStage: (stage: string | null) => void
}

const STAGE_ICONS: Record<string, React.ReactNode> = {
  'RawData': <Database size={20} />,
  'Slicing': <Scissors size={20} />,
  'View': <Eye size={20} />,
  'Sink': <Rocket size={20} />,
}

export function PipelineStages({ stages, selectedStage, onSelectStage }: PipelineStagesProps) {
  return (
    <motion.div
      className="pipeline-visualization"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
    >
      <div className="pipeline-stages">
        {stages.map((stage, index) => (
          <motion.div
            key={stage.name}
            className={`pipeline-stage-card ${selectedStage === stage.name ? 'active' : ''}`}
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: index * 0.1 }}
            onClick={() => onSelectStage(selectedStage === stage.name ? null : stage.name)}
          >
            <div className="stage-header">
              <span className="stage-icon">{STAGE_ICONS[stage.name] || <Database size={20} />}</span>
              <span className={`stage-status ${stage.status.toLowerCase()}`}>
                {stage.status}
              </span>
            </div>

            <h3 className="stage-title">{stage.name}</h3>
            <p className="stage-desc">{stage.description}</p>

            <div className="stage-count-box">
              <motion.span
                className="stage-count"
                key={stage.count}
                initial={{ scale: 1.2 }}
                animate={{ scale: 1 }}
              >
                {stage.count.toLocaleString()}
              </motion.span>
              <span className="stage-label">
                {stage.name === 'View' ? 'definitions' : 'records'}
              </span>
            </div>

            {index < stages.length - 1 && (
              <div className="stage-arrow">
                <motion.div
                  animate={{ x: [0, 5, 0] }}
                  transition={{ repeat: Infinity, duration: 1.5 }}
                >
                  <ArrowRight size={24} />
                </motion.div>
              </div>
            )}
          </motion.div>
        ))}
      </div>
    </motion.div>
  )
}
