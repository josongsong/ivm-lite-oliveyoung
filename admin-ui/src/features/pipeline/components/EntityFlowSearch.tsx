import { motion } from 'framer-motion'
import { ArrowRight, Database, Layers, Search, Send } from 'lucide-react'
import type { EntityFlowResponse } from '@/shared/types'
import { Button } from '@/shared/ui'

interface EntityFlowSearchProps {
  searchKey: string
  onSearchKeyChange: (key: string) => void
  onSearch: (e: React.FormEvent) => void
  entityFlow: EntityFlowResponse | undefined
  placeholder: string
}

export function EntityFlowSearch({
  searchKey,
  onSearchKeyChange,
  onSearch,
  entityFlow,
  placeholder,
}: EntityFlowSearchProps) {
  return (
    <motion.div
      className="entity-search-section"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.3 }}
    >
      <h2 className="section-title">Entity Flow 추적</h2>
      <form className="entity-search-form" onSubmit={onSearch}>
        <div className="search-input-wrapper">
          <Search size={18} />
          <input
            type="text"
            placeholder={placeholder}
            value={searchKey}
            onChange={(e) => onSearchKeyChange(e.target.value)}
          />
        </div>
        <Button type="submit" variant="primary">
          추적
        </Button>
      </form>

      {entityFlow ? (
        <motion.div
          className="entity-flow-results"
          initial={{ opacity: 0, height: 0 }}
          animate={{ opacity: 1, height: 'auto' }}
        >
          <h3 className="flow-title">
            <span className="flow-key">{entityFlow.entityKey}</span>
            의 데이터 흐름
          </h3>

          <div className="flow-timeline">
            {/* Raw Data */}
            <FlowSection
              icon={<Database size={20} />}
              title="Raw Data"
              count={entityFlow.rawData.length}
            >
              {entityFlow.rawData.length > 0 ? (
                <div className="flow-items">
                  {entityFlow.rawData.map((item, i) => (
                    <div key={`raw-${item.version}-${i}`} className="flow-item">
                      <span className="item-version">v{item.version}</span>
                      <span className="item-schema">{item.schemaId}</span>
                      <span className="item-time">
                        {item.createdAt ? new Date(item.createdAt).toLocaleString('ko-KR') : '-'}
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="flow-empty">데이터 없음</div>
              )}
            </FlowSection>

            <FlowConnector />

            {/* Slices */}
            <FlowSection
              icon={<Layers size={20} />}
              title="Slices"
              count={entityFlow.slices.length}
            >
              {entityFlow.slices.length > 0 ? (
                <div className="flow-items">
                  {entityFlow.slices.map((item, i) => (
                    <div key={`slice-${item.sliceType}-${i}`} className="flow-item">
                      <span className="item-type badge-info">{item.sliceType}</span>
                      <span className="item-version">v{item.version}</span>
                      <span className="item-hash mono">{item.hash.slice(0, 12)}...</span>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="flow-empty">슬라이스 없음</div>
              )}
            </FlowSection>

            <FlowConnector />

            {/* Outbox */}
            <FlowSection
              icon={<Send size={20} />}
              title="Outbox"
              count={entityFlow.outbox.length}
            >
              {entityFlow.outbox.length > 0 ? (
                <div className="flow-items">
                  {entityFlow.outbox.map((item, i) => (
                    <div key={`outbox-${item.eventType}-${i}`} className="flow-item">
                      <span className={`item-status badge-${item.status.toLowerCase()}`}>{item.status}</span>
                      <span className="item-event">{item.eventType}</span>
                      <span className="item-time">
                        {item.createdAt ? new Date(item.createdAt).toLocaleString('ko-KR') : '-'}
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="flow-empty">Outbox 엔트리 없음</div>
              )}
            </FlowSection>
          </div>
        </motion.div>
      ) : null}
    </motion.div>
  )
}

function FlowSection({
  icon,
  title,
  count,
  children,
}: {
  icon: React.ReactNode
  title: string
  count: number
  children: React.ReactNode
}) {
  return (
    <div className="flow-section">
      <div className="flow-section-header">
        {icon}
        <span>{title}</span>
        <span className="flow-count">{count}</span>
      </div>
      {children}
    </div>
  )
}

function FlowConnector() {
  return (
    <div className="flow-connector">
      <ArrowRight size={20} />
    </div>
  )
}
