import { useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { ChevronRight, Clock, FileCode2, Hash, Layers } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { explorerApi } from '@/shared/api'
import type { SliceEntry } from '@/shared/types'
import { JsonViewer } from './JsonViewer'
import { Loading } from '@/shared/ui'
import './SliceList.css'

interface SliceListProps {
  tenant: string
  entityId: string
}

export function SliceList({ tenant, entityId }: SliceListProps) {
  const [selectedSlice, setSelectedSlice] = useState<SliceEntry | null>(null)
  const [filterType, setFilterType] = useState<string | undefined>()

  const { data, isLoading, error } = useQuery({
    queryKey: ['slices', tenant, entityId, filterType],
    queryFn: () => explorerApi.getSlices(tenant, entityId, filterType),
    enabled: !!tenant && !!entityId,
  })

  // 슬라이스 타입별 그룹핑
  const sliceTypes = data?.slices
    ? [...new Set(data.slices.map(s => s.sliceType))]
    : []

  if (isLoading) return <Loading />

  if (error) {
    return (
      <div className="slice-list error">
        <p>슬라이스를 불러오는 중 오류가 발생했습니다.</p>
      </div>
    )
  }

  if (!data?.slices.length) {
    return (
      <div className="slice-list empty">
        <Layers size={48} />
        <p>생성된 Slice가 없습니다.</p>
      </div>
    )
  }

  return (
    <div className="slice-list">
      {/* 필터 */}
      <div className="slice-filters">
        <span className="filter-label">Type:</span>
        <button
          className={`filter-chip ${!filterType ? 'active' : ''}`}
          onClick={() => setFilterType(undefined)}
        >
          All ({data.total})
        </button>
        {sliceTypes.map(type => (
          <button
            key={type}
            className={`filter-chip ${filterType === type ? 'active' : ''}`}
            onClick={() => setFilterType(type)}
          >
            {type}
          </button>
        ))}
      </div>

      {/* 슬라이스 목록 */}
      <div className="slice-grid">
        <div className="slice-items">
          {data.slices.map((slice, index) => (
            <motion.button
              key={`${slice.sliceType}-${slice.version}`}
              className={`slice-item ${selectedSlice?.sliceType === slice.sliceType ? 'selected' : ''}`}
              onClick={() => setSelectedSlice(slice)}
              initial={{ opacity: 0, x: -10 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: index * 0.03 }}
            >
              <div className="slice-item-header">
                <Layers size={16} className="slice-icon" />
                <span className="slice-type">{slice.sliceType}</span>
                <ChevronRight size={14} className="slice-arrow" />
              </div>
              <div className="slice-item-meta">
                <span className="slice-meta-item">
                  <FileCode2 size={12} />
                  {slice.rulesetRef.split('/').pop()}
                </span>
                <span className="slice-meta-item">
                  <Hash size={12} />
                  v{slice.version}
                </span>
              </div>
            </motion.button>
          ))}
        </div>

        {/* 상세 패널 */}
        <AnimatePresence mode="wait">
          {selectedSlice ? (
            <motion.div
              key={selectedSlice.sliceType}
              className="slice-detail"
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: 20 }}
            >
              <div className="slice-detail-header">
                <div className="slice-detail-title">
                  <Layers size={18} />
                  <span>{selectedSlice.sliceType}</span>
                </div>
                <div className="slice-detail-meta">
                  <div className="detail-meta-item">
                    <FileCode2 size={12} />
                    <span>{selectedSlice.rulesetRef}</span>
                  </div>
                  <div className="detail-meta-item">
                    <Hash size={12} />
                    <span>Version {selectedSlice.version}</span>
                  </div>
                  <div className="detail-meta-item">
                    <Clock size={12} />
                    <span>{new Date(selectedSlice.createdAt).toLocaleString()}</span>
                  </div>
                  <div className="detail-meta-item">
                    <span className="source-badge">
                      Source: RawData v{selectedSlice.sourceRawDataVersion}
                    </span>
                  </div>
                </div>
              </div>
              <div className="slice-detail-content">
                <JsonViewer data={selectedSlice.data} initialExpanded={true} />
              </div>
            </motion.div>
          ) : (
            <motion.div
              className="slice-detail-empty"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
            >
              <Layers size={32} />
              <p>Slice를 선택하세요</p>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  )
}
