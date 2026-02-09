import { AnimatePresence, motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import { ArrowRight, CheckCircle2, Clock, FileCode2 } from 'lucide-react'
import { getContractKindInfo } from '@/shared/config'
import type { Contract } from '@/shared/types'
import { NoResults } from '@/shared/ui'

interface ContractGridProps {
  contracts: Contract[]
}

export function ContractGrid({ contracts }: ContractGridProps) {
  if (contracts.length === 0) {
    return (
      <NoResults
        icon={<FileCode2 size={48} strokeWidth={1} />}
        title="Contract를 찾을 수 없습니다"
        description="검색어나 필터를 변경해보세요"
      />
    )
  }

  return (
    <motion.div
      className="contracts-grid"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ delay: 0.2 }}
    >
      <AnimatePresence mode="popLayout">
        {contracts.map((contract, index) => {
          const info = getContractKindInfo(contract.kind)

          return (
            <motion.div
              key={`${contract.kind}-${contract.id}-${contract.version}-${index}`}
              layout
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.9 }}
            >
              <Link
                to={`/contracts/${contract.kind}/${encodeURIComponent(contract.id)}`}
                className={`contract-card ${info.color}`}
              >
                <div className="contract-header">
                  <span className={`contract-kind badge-${info.color}`}>
                    {info.label}
                  </span>
                  <span className={`contract-status ${contract.status.toLowerCase()}`}>
                    {contract.status === 'ACTIVE' ? <CheckCircle2 size={12} /> : <Clock size={12} />}
                    {contract.status}
                  </span>
                </div>

                <h3 className="contract-id">{contract.id}</h3>

                <div className="contract-meta">
                  <span className="contract-version">v{contract.version}</span>
                  <span className="contract-file">{contract.fileName}</span>
                </div>

                <div className="contract-preview">
                  <code>{contract.content.slice(0, 80)}...</code>
                </div>

                <div className="contract-arrow">
                  <ArrowRight size={14} />
                </div>
              </Link>
            </motion.div>
          )
        })}
      </AnimatePresence>
    </motion.div>
  )
}
