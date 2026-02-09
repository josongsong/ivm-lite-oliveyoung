import { ArrowDownRight, ArrowUpRight } from 'lucide-react'
import type { NodeDetailResponse } from '../../model/types'

interface ConnectionsSectionProps {
  detail: NodeDetailResponse
}

export function ConnectionsSection({ detail }: ConnectionsSectionProps) {
  const hasUpstream = Array.isArray(detail.upstreamNodes) && detail.upstreamNodes.length > 0
  const hasDownstream = Array.isArray(detail.downstreamNodes) && detail.downstreamNodes.length > 0

  if (!hasUpstream && !hasDownstream) return null

  return (
    <section className="panel-section">
      <h4 className="section-title">연결된 노드</h4>
      {hasUpstream ? <div className="connection-group">
          <div className="connection-label">
            <ArrowDownRight size={12} />
            Upstream ({detail.upstreamNodes.length})
          </div>
          <div className="connection-nodes">
            {detail.upstreamNodes.map((nodeId) => (
              <span key={`upstream-${nodeId}`} className="connection-tag">
                {nodeId || 'Unknown'}
              </span>
            ))}
          </div>
        </div> : null}
      {hasDownstream ? <div className="connection-group">
          <div className="connection-label">
            <ArrowUpRight size={12} />
            Downstream ({detail.downstreamNodes.length})
          </div>
          <div className="connection-nodes">
            {detail.downstreamNodes.map((nodeId) => (
              <span key={`downstream-${nodeId}`} className="connection-tag">
                {nodeId || 'Unknown'}
              </span>
            ))}
          </div>
        </div> : null}
    </section>
  )
}
