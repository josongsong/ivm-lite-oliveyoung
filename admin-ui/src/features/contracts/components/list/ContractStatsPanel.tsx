import { motion } from 'framer-motion'
import { HelpCircle, X } from 'lucide-react'
import { getContractKindInfo } from '@/shared/config'
import { IconButton } from '@/shared/ui'

interface ContractStatsPanelProps {
  totalCount: number
  byKind: Record<string, number>
  tooltipKind: string | null
  onKindSelect: (kind: string) => void
  onTooltipToggle: (kind: string | null) => void
}

export function ContractStatsPanel({
  totalCount,
  byKind,
  tooltipKind,
  onKindSelect,
  onTooltipToggle,
}: ContractStatsPanelProps) {
  return (
    <motion.div
      className="contract-stats"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
    >
      <div className="stat-overview">
        <div className="stat-total">
          <span className="stat-number">{totalCount}</span>
          <span className="stat-text">Total Contracts</span>
        </div>
        <div className="stat-breakdown-grid">
          {Object.entries(byKind).map(([kind, count]) => {
            const info = getContractKindInfo(kind)
            return (
              <div
                role="button"
                tabIndex={0}
                key={kind}
                className={`stat-item-fixed ${info.color}`}
                onClick={() => onKindSelect(kind)}
                onKeyDown={(e) => e.key === 'Enter' && onKindSelect(kind)}
              >
                <span className="stat-count">{count}</span>
                <span className="stat-kind">{info.label}</span>
                <IconButton
                  icon={HelpCircle}
                  size="sm"
                  variant="ghost"
                  className="stat-help"
                  onClick={(e) => {
                    e.stopPropagation()
                    onTooltipToggle(tooltipKind === kind ? null : kind)
                  }}
                  tooltip="설명 보기"
                />

                {tooltipKind === kind && (
                  <motion.div
                    className="stat-tooltip"
                    initial={{ opacity: 0, y: -5 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0 }}
                  >
                    <IconButton
                      icon={X}
                      size="sm"
                      variant="ghost"
                      className="tooltip-close"
                      onClick={(e) => {
                        e.stopPropagation()
                        onTooltipToggle(null)
                      }}
                      tooltip="닫기"
                    />
                    <p>{info.description}</p>
                  </motion.div>
                )}
              </div>
            )
          })}
        </div>
      </div>
    </motion.div>
  )
}
