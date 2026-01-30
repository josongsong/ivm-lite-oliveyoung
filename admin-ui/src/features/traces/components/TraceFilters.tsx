import { useState } from 'react'
import { Calendar, Filter, RefreshCw } from 'lucide-react'
import type { TraceFilterOptions } from '@/shared/types'
import './TraceFilters.css'

interface TraceFiltersProps {
  filters: TraceFilterOptions
  onChange: (filters: TraceFilterOptions) => void
  onRefresh: () => void
}

const TIME_RANGES = [
  { label: '15분', minutes: 15 },
  { label: '1시간', minutes: 60 },
  { label: '3시간', minutes: 180 },
  { label: '24시간', minutes: 1440 },
] as const

export function TraceFilters({ filters, onChange, onRefresh }: TraceFiltersProps) {
  const [serviceName, setServiceName] = useState(filters.serviceName || '')

  const handleTimeRangeSelect = (minutes: number) => {
    const endTime = new Date()
    const startTime = new Date(endTime.getTime() - minutes * 60 * 1000)
    onChange({
      ...filters,
      startTime: startTime.toISOString(),
      endTime: endTime.toISOString(),
      nextToken: null, // 시간 범위 변경 시 페이징 리셋
    })
  }

  const handleServiceFilter = () => {
    onChange({
      ...filters,
      serviceName: serviceName || undefined,
      nextToken: null,
    })
  }

  return (
    <div className="trace-filters">
      {/* 시간 범위 선택 */}
      <div className="filter-group">
        <Calendar size={16} />
        <div className="time-range-buttons">
          {TIME_RANGES.map((range) => (
            <button
              key={range.minutes}
              className="time-range-btn"
              onClick={() => handleTimeRangeSelect(range.minutes)}
            >
              {range.label}
            </button>
          ))}
        </div>
      </div>

      {/* 서비스 필터 */}
      <div className="filter-group">
        <Filter size={16} />
        <input
          type="text"
          className="service-filter-input"
          placeholder="서비스 이름 필터..."
          value={serviceName}
          onChange={(e) => setServiceName(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              handleServiceFilter()
            }
          }}
        />
        <button className="filter-apply-btn" onClick={handleServiceFilter}>
          적용
        </button>
      </div>

      {/* 새로고침 */}
      <button className="refresh-btn" onClick={onRefresh}>
        <RefreshCw size={16} />
        새로고침
      </button>
    </div>
  )
}
