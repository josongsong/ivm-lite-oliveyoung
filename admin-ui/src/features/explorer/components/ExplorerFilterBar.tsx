import { Filter, Layers } from 'lucide-react'
import { Select } from '@/shared/ui'

type ListType = 'rawdata' | 'slices' | 'views'

interface SliceType {
  type: string
  count: number
}

interface ExplorerFilterBarProps {
  listType: ListType
  tenant: string
  totalItems: number
  pageSize: number
  selectedSliceType: string
  sliceTypes: SliceType[] | undefined
  onSliceTypeChange: (type: string) => void
  onPageSizeChange: (size: number) => void
}

export function ExplorerFilterBar({
  listType,
  tenant,
  totalItems,
  pageSize,
  selectedSliceType,
  sliceTypes,
  onSliceTypeChange,
  onPageSizeChange,
}: ExplorerFilterBarProps) {
  return (
    <div className="sub-filter-bar">
      <div className="filter-left">
        <div className="filter-item">
          <Filter size={14} />
          <span className="filter-label">Tenant:</span>
          <span className="filter-value">{tenant}</span>
        </div>
        {listType === 'slices' && sliceTypes?.length ? (
          <div className="filter-item">
            <Layers size={14} />
            <span className="filter-label">Type:</span>
            <Select
              value={selectedSliceType}
              onChange={onSliceTypeChange}
              options={sliceTypes.map((t) => ({
                value: t.type,
                label: `${t.type} (${t.count})`,
              }))}
              size="sm"
              className="slice-type-select"
            />
          </div>
        ) : null}
        <div className="filter-item">
          <span className="filter-label">Total:</span>
          <span className="filter-value accent">{totalItems.toLocaleString()}</span>
        </div>
      </div>
      <div className="filter-right">
        <Select
          value={pageSize.toString()}
          onChange={(v) => onPageSizeChange(Number(v))}
          options={[
            { value: '10', label: '10개씩' },
            { value: '20', label: '20개씩' },
            { value: '50', label: '50개씩' },
            { value: '100', label: '100개씩' },
          ]}
          size="sm"
          className="page-size-select"
        />
      </div>
    </div>
  )
}
