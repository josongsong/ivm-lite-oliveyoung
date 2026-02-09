import { useNavigate } from 'react-router-dom'
import {
  ChevronLeft,
  Database,
  Eye,
  GitBranch,
  Layers,
  Plus,
  RefreshCw,
  Search,
  X,
} from 'lucide-react'
import type { ExplorerTab } from '@/shared/types'
import { Button, IconButton, Tabs, TabsList, TabsTrigger } from '@/shared/ui'
import type { ListType, SearchState } from '../hooks/useExplorerState'

interface ExplorerGNBProps {
  viewMode: 'list' | 'detail'
  listType: ListType
  activeTab: ExplorerTab
  searchState: SearchState | null
  searchFilter: string
  isRefreshing: boolean
  onBackToList: () => void
  onTabChange: (tab: ListType) => void
  onActiveTabChange: (tab: ExplorerTab) => void
  onSearchFilterChange: (value: string) => void
  onSearch: (value: string) => void
  onRefresh: () => void
}

export function ExplorerGNB({
  viewMode,
  listType,
  activeTab,
  searchState,
  searchFilter,
  isRefreshing,
  onBackToList,
  onTabChange,
  onActiveTabChange,
  onSearchFilterChange,
  onSearch,
  onRefresh,
}: ExplorerGNBProps) {
  const navigate = useNavigate()

  return (
    <div className="explorer-gnb">
      <div className="gnb-left">
        {viewMode === 'detail' ? (
          <Button variant="ghost" size="sm" onClick={onBackToList} icon={<ChevronLeft size={16} />}>
            목록
          </Button>
        ) : (
          <Tabs value={listType} onValueChange={(v) => onTabChange(v as ListType)}>
            <TabsList variant="segmented" responsive="iconsOnlyMd">
              <TabsTrigger value="rawdata" icon={<Database size={16} />}>RawData</TabsTrigger>
              <TabsTrigger value="slices" icon={<Layers size={16} />}>Slices</TabsTrigger>
              <TabsTrigger value="views" icon={<Eye size={16} />}>Views</TabsTrigger>
            </TabsList>
          </Tabs>
        )}
      </div>

      <div className="gnb-center">
        {viewMode === 'detail' && searchState ? (
          <div className="current-entity-bar">
            <Tabs value={activeTab} onValueChange={(v) => onActiveTabChange(v as ExplorerTab)}>
              <TabsList className="entity-tabs">
                <TabsTrigger value="rawdata" icon={<Database size={14} />}>RawData</TabsTrigger>
                <TabsTrigger value="slices" icon={<Layers size={14} />}>Slices</TabsTrigger>
                <TabsTrigger value="view" icon={<Eye size={14} />}>View</TabsTrigger>
                <TabsTrigger value="lineage" icon={<GitBranch size={14} />}>Lineage</TabsTrigger>
              </TabsList>
            </Tabs>
            <div className="entity-info">
              <span className="entity-id">{searchState.entityId}</span>
              {searchState.version ? <span className="entity-version">v{searchState.version}</span> : null}
            </div>
          </div>
        ) : (
          <div className="search-bar-inline">
            <Search size={16} className="search-icon" />
            <input
              type="text"
              placeholder="Entity ID 검색... (예: SKU-12345)"
              value={searchFilter}
              onChange={(e) => onSearchFilterChange(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && searchFilter && onSearch(searchFilter)}
            />
            {searchFilter ? (
              <IconButton
                icon={X}
                size="sm"
                variant="ghost"
                className="clear-btn"
                onClick={() => onSearchFilterChange('')}
                tooltip="검색어 지우기"
              />
            ) : null}
          </div>
        )}
      </div>

      <div className="gnb-right">
        <IconButton
          icon={RefreshCw}
          size="sm"
          variant="ghost"
          className={isRefreshing ? 'spinning' : ''}
          onClick={onRefresh}
          disabled={isRefreshing}
          tooltip="새로고침"
        />
        {viewMode === 'list' && listType === 'rawdata' && (
          <Button variant="primary" size="sm" onClick={() => navigate('/explorer/new')} icon={<Plus size={16} />}>
            New
          </Button>
        )}
      </div>
    </div>
  )
}
