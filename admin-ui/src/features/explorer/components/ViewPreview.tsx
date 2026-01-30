import { useState } from 'react'
import { motion } from 'framer-motion'
import { ChevronDown, Clock, Eye, FileCode2, Layers } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { explorerApi } from '@/shared/api'
import { JsonViewer } from './JsonViewer'
import { Loading } from '@/shared/ui'
import './ViewPreview.css'

interface ViewPreviewProps {
  tenant: string
  entityId: string
  initialViewDefId?: string
}

export function ViewPreview({ tenant, entityId, initialViewDefId }: ViewPreviewProps) {
  const [selectedViewDef, setSelectedViewDef] = useState<string | undefined>(initialViewDefId)
  const [showDropdown, setShowDropdown] = useState(false)

  const { data, isLoading, error } = useQuery({
    queryKey: ['view', tenant, entityId, selectedViewDef],
    queryFn: () => explorerApi.getView(tenant, entityId, selectedViewDef),
    enabled: !!tenant && !!entityId,
  })

  if (isLoading) return <Loading />

  if (error) {
    return (
      <div className="view-preview error">
        <p>View를 불러오는 중 오류가 발생했습니다.</p>
      </div>
    )
  }

  if (!data?.view && !data?.availableViewDefs.length) {
    return (
      <div className="view-preview empty">
        <Eye size={48} />
        <p>사용 가능한 ViewDef가 없습니다.</p>
      </div>
    )
  }

  const view = data.view

  return (
    <div className="view-preview">
      {/* ViewDef 선택 */}
      <div className="view-selector">
        <div className="view-selector-wrapper">
          <button
            className="view-selector-btn"
            onClick={() => setShowDropdown(!showDropdown)}
          >
            <Eye size={16} />
            <span>{selectedViewDef || data.availableViewDefs[0] || 'Select ViewDef'}</span>
            <ChevronDown size={14} className={showDropdown ? 'rotated' : ''} />
          </button>

          {showDropdown && (
            <motion.div
              className="view-dropdown"
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
            >
              {data.availableViewDefs.map(vd => (
                <button
                  key={vd}
                  className={`view-dropdown-item ${vd === selectedViewDef ? 'active' : ''}`}
                  onClick={() => {
                    setSelectedViewDef(vd)
                    setShowDropdown(false)
                  }}
                >
                  {vd}
                </button>
              ))}
            </motion.div>
          )}
        </div>
      </div>

      {view ? (
        <>
          {/* View 메타데이터 */}
          <div className="view-meta">
            <div className="view-meta-item">
              <FileCode2 size={14} />
              <span className="meta-label">ViewDef</span>
              <span className="meta-value">{view.viewDefRef}</span>
            </div>
            <div className="view-meta-item">
              <Clock size={14} />
              <span className="meta-label">Assembled</span>
              <span className="meta-value">
                {new Date(view.assembledAt).toLocaleString()}
              </span>
            </div>
          </div>

          {/* Source Slices */}
          <div className="view-sources">
            <span className="sources-label">
              <Layers size={14} /> Source Slices:
            </span>
            {view.sourceSlices.map(src => (
              <span key={src.sliceType} className="source-chip">
                {src.sliceType} v{src.version}
              </span>
            ))}
          </div>

          {/* View 데이터 */}
          <div className="view-content">
            <JsonViewer data={view.assembledData} initialExpanded={true} />
          </div>
        </>
      ) : (
        <div className="view-not-assembled">
          <Eye size={32} />
          <p>선택한 ViewDef로 조합된 View가 없습니다.</p>
          <span className="hint">
            먼저 RawData가 입력되고 Slice가 생성되어야 View가 조합됩니다.
          </span>
        </div>
      )}
    </div>
  )
}
