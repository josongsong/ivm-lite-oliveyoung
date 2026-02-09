import { Link } from 'react-router-dom'
import { ExternalLink } from 'lucide-react'
import type { NodeDetailResponse } from '../../model/types'

interface RelatedContractsSectionProps {
  contracts: NonNullable<NodeDetailResponse['relatedContracts']>
}

export function RelatedContractsSection({ contracts }: RelatedContractsSectionProps) {
  if (contracts.length === 0) return null

  return (
    <section className="panel-section">
      <h4 className="section-title">관련 Contract</h4>
      <div className="contract-list">
        {contracts.map((contract) => (
          <Link
            key={`contract-${contract?.id || 'unknown'}`}
            to={`/contracts/${contract?.kind || 'unknown'}/${encodeURIComponent(contract?.id || '')}`}
            className="contract-link"
          >
            <span className="contract-kind">{contract?.kind || 'Unknown'}</span>
            <span className="contract-id">{contract?.id || 'N/A'}</span>
            <ExternalLink size={12} />
          </Link>
        ))}
      </div>
    </section>
  )
}
