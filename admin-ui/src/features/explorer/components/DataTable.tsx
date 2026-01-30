import { useState } from 'react'
import { motion } from 'framer-motion'
import {
  ChevronRight,
  Clock,
  Database,
  Eye,
  Layers,
  Plus,
  RefreshCw,
  Search as SearchIcon
} from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { explorerApi } from '@/shared/api'
import { Loading } from '@/shared/ui'
import './DataTable.css'

export type DataTableType = 'rawdata' | 'slices' | 'views'

interface DataTableProps {
  tenant: string
  type: DataTableType
  onSelect: (entityId: string, type: DataTableType) => void
  onCreateNew?: () => void
}

export function DataTable({ tenant, type, onSelect, onCreateNew }: DataTableProps) {
  const [searchFilter, setSearchFilter] = useState('')
  const [page, setPage] = useState(0)
  const limit = 20

  // RawData 목록
  const { data: rawDataList, isLoading: loadingRaw, isError: errorRaw, refetch: refetchRaw } = useQuery({
    queryKey: ['rawdata-list', tenant, searchFilter, page],
    queryFn: () => explorerApi.listRawData(tenant, searchFilter || undefined, limit),
    enabled: type === 'rawdata',
    retry: false,
  })

  // Slice 목록 (tenant 전체)
  const { data: sliceList, isLoading: loadingSlices, isError: errorSlices, refetch: refetchSlices } = useQuery({
    queryKey: ['slices-list', tenant, page],
    queryFn: () => explorerApi.search({ tenant, limit }),
    enabled: type === 'slices',
    retry: false,
  })

  const isLoading = type === 'rawdata' ? loadingRaw : loadingSlices
  const isError = type === 'rawdata' ? errorRaw : errorSlices

  const handleRefresh = () => {
    if (type === 'rawdata') refetchRaw()
    else refetchSlices()
  }

  return (
    <div className="data-table">
      {/* 헤더 */}
      <div className="table-header">
        <div className="table-title">
          {type === 'rawdata' && <Database size={18} />}
          {type === 'slices' && <Layers size={18} />}
          {type === 'views' && <Eye size={18} />}
          <h3>
            {type === 'rawdata' && 'RawData'}
            {type === 'slices' && 'Slices'}
            {type === 'views' && 'Views'}
          </h3>
          <span className="table-count">
            {type === 'rawdata' && rawDataList?.total}
            {type === 'slices' && sliceList?.total}
          </span>
        </div>

        <div className="table-actions">
          <div className="search-filter">
            <SearchIcon size={14} />
            <input
              type="text"
              placeholder="Entity ID로 필터..."
              value={searchFilter}
              onChange={(e) => setSearchFilter(e.target.value)}
            />
          </div>
          <button className="action-btn" onClick={handleRefresh}>
            <RefreshCw size={14} />
          </button>
          {onCreateNew && type === 'rawdata' && (
            <button className="action-btn primary" onClick={onCreateNew}>
              <Plus size={14} />
              New
            </button>
          )}
        </div>
      </div>

      {/* 테이블 */}
      {isLoading ? (
        <div className="table-loading">
          <Loading />
        </div>
      ) : isError ? (
        <div className="table-error">
          <p>API 연결 대기 중...</p>
          <span>백엔드 서버가 실행되면 데이터가 표시됩니다</span>
          {onCreateNew && type === 'rawdata' && (
            <button className="create-btn" onClick={onCreateNew}>
              <Plus size={14} />
              새 RawData 등록
            </button>
          )}
        </div>
      ) : (
        <div className="table-content">
          <table>
            <thead>
              <tr>
                <th>Entity ID</th>
                <th>Version</th>
                <th>Schema / Type</th>
                <th>Updated</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {type === 'rawdata' && rawDataList?.entries.map((entry, idx) => (
                <motion.tr
                  key={`${entry.entityId}-${entry.version}`}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: idx * 0.02 }}
                  onClick={() => onSelect(entry.entityId, type)}
                >
                  <td className="entity-cell">
                    <Database size={14} />
                    <span>{entry.entityId}</span>
                  </td>
                  <td className="version-cell">
                    <span className="version-badge">v{entry.version}</span>
                  </td>
                  <td className="schema-cell">{entry.schemaRef.split('/').pop()}</td>
                  <td className="time-cell">
                    <Clock size={12} />
                    {entry.updatedAt ? new Date(entry.updatedAt).toLocaleDateString() : '-'}
                  </td>
                  <td className="action-cell">
                    <ChevronRight size={14} />
                  </td>
                </motion.tr>
              ))}
              {type === 'slices' && sliceList?.entries.map((entry, idx) => (
                <motion.tr
                  key={`${entry.entityId}-${entry.version}`}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: idx * 0.02 }}
                  onClick={() => onSelect(entry.entityId, type)}
                >
                  <td className="entity-cell">
                    <Layers size={14} />
                    <span>{entry.entityId}</span>
                  </td>
                  <td className="version-cell">
                    <span className="version-badge">v{entry.version}</span>
                  </td>
                  <td className="schema-cell">{entry.schemaRef.split('/').pop()}</td>
                  <td className="time-cell">
                    <Clock size={12} />
                    {entry.updatedAt ? new Date(entry.updatedAt).toLocaleDateString() : '-'}
                  </td>
                  <td className="action-cell">
                    <ChevronRight size={14} />
                  </td>
                </motion.tr>
              ))}
            </tbody>
          </table>

          {/* Empty State */}
          {((type === 'rawdata' && !rawDataList?.entries.length) ||
            (type === 'slices' && !sliceList?.entries.length)) && (
            <div className="table-empty">
              <p>데이터가 없습니다</p>
              {onCreateNew && type === 'rawdata' && (
                <button onClick={onCreateNew}>
                  <Plus size={14} />
                  새 RawData 등록
                </button>
              )}
            </div>
          )}
        </div>
      )}

      {/* 페이지네이션 */}
      {((type === 'rawdata' && rawDataList?.hasMore) ||
        (type === 'slices' && sliceList?.hasMore)) && (
        <div className="table-pagination">
          <button
            disabled={page === 0}
            onClick={() => setPage(p => Math.max(0, p - 1))}
          >
            이전
          </button>
          <span>Page {page + 1}</span>
          <button onClick={() => setPage(p => p + 1)}>
            다음
          </button>
        </div>
      )}
    </div>
  )
}
