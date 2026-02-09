import { useMemo, useState } from 'react'
import {
  Check,
  ChevronLeft,
  ChevronRight,
  Clock,
  Copy,
  Database,
  FileCode2,
  GitCompare,
  Hash
} from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { explorerApi } from '@/shared/api'
import { JsonViewer, DiffViewer, computeDiff } from '@/shared/ui'
import { Loading } from '@/shared/ui'
import './RawDataPanel.css'

interface RawDataPanelProps {
  tenant: string
  entityId: string
  initialVersion?: number | 'latest'
}

export function RawDataPanel({ tenant, entityId, initialVersion }: RawDataPanelProps) {
  const [selectedVersion, setSelectedVersion] = useState<number | 'latest'>(initialVersion ?? 'latest')
  const [compareVersion, setCompareVersion] = useState<number | null>(null)
  const [showDiff, setShowDiff] = useState(false)
  const [copied, setCopied] = useState(false)

  const { data, isLoading, error } = useQuery({
    queryKey: ['rawdata', tenant, entityId, selectedVersion],
    queryFn: () => explorerApi.getRawData(tenant, entityId, selectedVersion),
    enabled: !!tenant && !!entityId,
  })

  const { data: compareData } = useQuery({
    queryKey: ['rawdata', tenant, entityId, compareVersion],
    queryFn: () => explorerApi.getRawData(tenant, entityId, compareVersion!),
    enabled: !!compareVersion && showDiff,
  })

  // Diff 계산
  const diffs = useMemo(() => {
    if (!showDiff || !data?.entry || !compareData?.entry) return []
    return computeDiff(
      compareData.entry.data as Record<string, unknown>,
      data.entry.data as Record<string, unknown>
    )
  }, [showDiff, data?.entry, compareData?.entry])

  const handleCopyJson = async () => {
    if (!data?.entry) return
    await navigator.clipboard.writeText(JSON.stringify(data.entry.data, null, 2))
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const handleVersionNav = (direction: 'prev' | 'next') => {
    if (!data?.versions || !data.entry) return
    const currentIdx = data.versions.indexOf(data.entry.version)
    const newIdx = direction === 'prev' ? currentIdx - 1 : currentIdx + 1
    if (newIdx >= 0 && newIdx < data.versions.length) {
      setSelectedVersion(data.versions[newIdx])
    }
  }

  if (isLoading) return <Loading />

  if (error) {
    return (
      <div className="rawdata-panel error">
        <p>데이터를 불러오는 중 오류가 발생했습니다.</p>
      </div>
    )
  }

  if (!data?.entry) {
    return (
      <div className="rawdata-panel empty">
        <Database size={48} />
        <p>RawData가 존재하지 않습니다.</p>
      </div>
    )
  }

  const entry = data.entry
  const currentIdx = data.versions.indexOf(entry.version)

  return (
    <div className="rawdata-panel">
      {/* 메타데이터 헤더 */}
      <div className="rawdata-header">
        <div className="rawdata-meta">
          <div className="meta-item">
            <Database size={14} />
            <span className="meta-label">Entity</span>
            <span className="meta-value">{entry.entityId}</span>
          </div>
          <div className="meta-item">
            <FileCode2 size={14} />
            <span className="meta-label">Schema</span>
            <span className="meta-value">{entry.schemaRef}</span>
          </div>
          <div className="meta-item">
            <Clock size={14} />
            <span className="meta-label">Updated</span>
            <span className="meta-value">
              {new Date(entry.updatedAt).toLocaleString()}
            </span>
          </div>
          <div className="meta-item">
            <Hash size={14} />
            <span className="meta-label">Hash</span>
            <span className="meta-value mono">{entry.hash.slice(0, 12)}...</span>
          </div>
        </div>

        <div className="rawdata-actions">
          <button
            className={`action-btn ${copied ? 'copied' : ''}`}
            onClick={handleCopyJson}
          >
            {copied ? <Check size={14} /> : <Copy size={14} />}
            {copied ? 'Copied!' : 'Copy JSON'}
          </button>
          <button
            className={`action-btn ${showDiff ? 'active' : ''}`}
            onClick={() => setShowDiff(!showDiff)}
          >
            <GitCompare size={14} />
            Diff
          </button>
        </div>
      </div>

      {/* 버전 네비게이션 */}
      <div className="rawdata-version-nav">
        <button
          className="version-nav-btn"
          onClick={() => handleVersionNav('prev')}
          disabled={currentIdx <= 0}
        >
          <ChevronLeft size={16} />
        </button>

        <div className="version-timeline">
          {data.versions.map((v) => (
            <button
              key={v}
              className={`version-dot ${v === entry.version ? 'active' : ''} ${
                v === compareVersion ? 'compare' : ''
              }`}
              onClick={() => {
                if (showDiff && v !== entry.version) {
                  setCompareVersion(v)
                } else {
                  setSelectedVersion(v)
                }
              }}
              title={`Version ${v}`}
            >
              <span className="version-label">v{v}</span>
            </button>
          ))}
        </div>

        <button
          className="version-nav-btn"
          onClick={() => handleVersionNav('next')}
          disabled={currentIdx >= data.versions.length - 1}
        >
          <ChevronRight size={16} />
        </button>

        <span className="version-info">
          v{entry.version} / {data.latestVersion}
          {entry.version === data.latestVersion && (
            <span className="latest-badge">latest</span>
          )}
        </span>
      </div>

      {/* 컨텐츠 */}
      <div className="rawdata-content">
        {showDiff && compareVersion ? (
          <DiffViewer
            fromVersion={compareVersion}
            toVersion={entry.version}
            diffs={diffs}
          />
        ) : (
          <JsonViewer data={entry.data} initialExpanded={true} />
        )}
      </div>
    </div>
  )
}
