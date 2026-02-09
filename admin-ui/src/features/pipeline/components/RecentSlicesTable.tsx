import { motion } from 'framer-motion'
import type { SliceDetailResponse } from '@/shared/types'

interface RecentSlicesTableProps {
  sliceDetail: SliceDetailResponse | undefined
}

export function RecentSlicesTable({ sliceDetail }: RecentSlicesTableProps) {
  return (
    <motion.div
      className="recent-section"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.7 }}
    >
      <h2 className="section-title">최근 Slice</h2>
      <div className="table-container">
        <table>
          <thead>
            <tr>
              <th>Entity Key</th>
              <th>Slice Type</th>
              <th>Version</th>
              <th>Hash</th>
              <th>Created At</th>
            </tr>
          </thead>
          <tbody>
            {sliceDetail?.recent?.map((slice, i) => (
              <tr key={`${slice.entityKey}-${slice.sliceType}-${i}`}>
                <td className="mono truncate" style={{ maxWidth: '200px' }}>{slice.entityKey}</td>
                <td><span className="badge badge-info">{slice.sliceType}</span></td>
                <td className="mono">v{slice.version}</td>
                <td className="mono text-muted">{slice.hash.slice(0, 16)}...</td>
                <td className="text-secondary">
                  {slice.createdAt ? new Date(slice.createdAt).toLocaleString('ko-KR') : '-'}
                </td>
              </tr>
            ))}
            {(!sliceDetail?.recent || sliceDetail.recent.length === 0) && (
              <tr>
                <td colSpan={5} className="text-muted" style={{ textAlign: 'center', padding: '2rem' }}>
                  데이터가 없습니다
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </motion.div>
  )
}
