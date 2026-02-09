/**
 * ExplorerDetailView Component
 *
 * DataExplorer의 상세 보기 뷰
 */
import { motion } from 'framer-motion'
import { ErrorBoundary } from '@/shared/ui'
import { LineageGraph } from '@/shared/ui'
import { RawDataEditor } from './RawDataEditor'
import { RawDataPanel } from './RawDataPanel'
import { SliceList } from './SliceList'
import { ViewPreview } from './ViewPreview'

export type DetailTab = 'rawdata' | 'slices' | 'view' | 'lineage' | 'create'

interface SearchState {
  tenant: string
  entityId: string
  version?: number | 'latest'
}

interface ExplorerDetailViewProps {
  activeTab: DetailTab
  searchState: SearchState
  onCreateSuccess: (entityId: string) => void
}

function DetailContent({ activeTab, searchState, onCreateSuccess }: ExplorerDetailViewProps) {
  const version = typeof searchState.version === 'number' ? searchState.version : undefined

  switch (activeTab) {
    case 'rawdata':
      return <RawDataPanel tenant={searchState.tenant} entityId={searchState.entityId} initialVersion={version} />
    case 'slices':
      return <SliceList tenant={searchState.tenant} entityId={searchState.entityId} />
    case 'view':
      return <ViewPreview tenant={searchState.tenant} entityId={searchState.entityId} />
    case 'lineage':
      return (
        <ErrorBoundary resetKeys={[searchState.entityId]}>
          <LineageGraph tenant={searchState.tenant} entityId={searchState.entityId} />
        </ErrorBoundary>
      )
    case 'create':
      return (
        <ErrorBoundary resetKeys={[searchState.tenant]}>
          <RawDataEditor defaultTenant={searchState.tenant} onSuccess={onCreateSuccess} />
        </ErrorBoundary>
      )
    default:
      return null
  }
}

export function ExplorerDetailView({ activeTab, searchState, onCreateSuccess }: ExplorerDetailViewProps) {
  return (
    <motion.div
      key="detail"
      className="detail-view"
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -20 }}
    >
      <DetailContent activeTab={activeTab} searchState={searchState} onCreateSuccess={onCreateSuccess} />
    </motion.div>
  )
}
