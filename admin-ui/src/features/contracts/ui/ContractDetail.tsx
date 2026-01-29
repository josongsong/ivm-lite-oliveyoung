import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { 
  ArrowLeft, 
  CheckCircle2, 
  Clock, 
  Copy,
  Download,
  FileCode2,
  MessageSquareText
} from 'lucide-react'
import { fetchApi } from '@/shared/api'
import type { ContractListResponse } from '@/shared/types'
import { Loading } from '@/shared/ui'
import { ContractDescription } from '@/features/contracts'
import './ContractDetail.css'

export function ContractDetail() {
  const { kind, id } = useParams<{ kind: string; id: string }>()
  const decodedId = decodeURIComponent(id ?? '')

  // 단일 API 호출로 전체 목록 조회 (캐시 활용)
  const { data: contractsData, isLoading, error } = useQuery({
    queryKey: ['contracts'],
    queryFn: () => fetchApi<ContractListResponse>('/contracts'),
  })

  // 캐시된 데이터에서 현재 contract 추출
  const data = contractsData?.contracts.find(
    c => c.kind === kind && c.id === decodedId
  )

  const handleCopy = () => {
    if (data?.content) {
      navigator.clipboard.writeText(data.content)
    }
  }

  const handleDownload = () => {
    if (data?.content) {
      const blob = new Blob([data.content], { type: 'text/yaml' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = data.fileName
      a.click()
      URL.revokeObjectURL(url)
    }
  }

  if (isLoading) return <Loading />

  if (error || !data) {
    return (
      <div className="page-container">
        <div className="empty-state">
          <div className="empty-state-icon">❌</div>
          <h3 className="empty-state-title">Contract를 찾을 수 없습니다</h3>
          <Link to="/contracts" className="btn btn-secondary">
            <ArrowLeft size={16} />
            목록으로 돌아가기
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="page-container">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <Link to="/contracts" className="back-link">
          <ArrowLeft size={16} />
          Contracts
        </Link>

        <div className="contract-detail-header">
          <div className="contract-icon">
            <FileCode2 size={32} />
          </div>
          <div className="contract-info">
            <div className="contract-badges">
              {data.kind === 'VIEW_DEFINITION' && (data.parsed as Record<string, unknown>)?.entityType ? (
                <Link
                  to={`/contracts/ENTITY_SCHEMA/entity.${String((data.parsed as Record<string, unknown>).entityType).toLowerCase()}.v1`}
                  className={`kind-badge ${data.kind.toLowerCase().replace('_', '-')} clickable`}
                  title="엔티티 스키마로 이동"
                >
                  {data.kind.replace('_', ' ')}
                </Link>
              ) : (
                <span className={`kind-badge ${data.kind.toLowerCase().replace('_', '-')}`}>
                  {data.kind.replace('_', ' ')}
                </span>
              )}
              <span className={`status-badge ${data.status.toLowerCase()}`}>
                {data.status === 'ACTIVE' ? <CheckCircle2 size={12} /> : <Clock size={12} />}
                {data.status}
              </span>
            </div>
            <h1 className="contract-title">{data.id}</h1>
            <div className="contract-version-info">
              <span>Version: <strong>{data.version}</strong></span>
              <span>File: <strong>{data.fileName}</strong></span>
            </div>
          </div>
          <div className="contract-actions">
            <button className="btn btn-secondary" onClick={handleCopy}>
              <Copy size={16} />
              Copy
            </button>
            <button className="btn btn-primary" onClick={handleDownload}>
              <Download size={16} />
              Download
            </button>
          </div>
        </div>

        {/* Schema Description - 서술형 설명 */}
        <div className="contract-section">
          <h2 className="section-title">Schema Description</h2>
          <ContractDescription 
            kind={data.kind} 
            parsed={data.parsed}
            allContracts={contractsData?.contracts}
          />
        </div>

        {/* Parsed Data */}
        <div className="contract-section">
          <h2 className="section-title">Parsed Data</h2>
          <div className="parsed-data">
            <ParsedView data={data.parsed} />
          </div>
        </div>

        {/* Raw YAML */}
        <div className="contract-section">
          <h2 className="section-title">Raw YAML</h2>
          <pre className="yaml-content">
            <code>{data.content}</code>
          </pre>
        </div>
      </motion.div>
    </div>
  )
}

// description 필드를 추출하는 헬퍼
const COMMENT_FIELDS = new Set(['description', 'comment', 'comments', 'note', 'notes', 'doc', 'docs', 'hint'])

function getDescription(obj: Record<string, unknown>): string | null {
  for (const key of Object.keys(obj)) {
    if (COMMENT_FIELDS.has(key.toLowerCase()) && typeof obj[key] === 'string') {
      return obj[key] as string
    }
  }
  return null
}

function ParsedView({ data, depth = 0, siblingDescription }: { data: unknown; depth?: number; siblingDescription?: string | null }) {
  if (data === null || data === undefined) {
    return <span className="value null">null</span>
  }

  if (typeof data === 'string') {
    return (
      <>
        <span className="value string">"{data}"</span>
        {siblingDescription && (
          <span className="inline-desc">
            <MessageSquareText size={11} />
            {siblingDescription}
          </span>
        )}
      </>
    )
  }

  if (typeof data === 'number') {
    return <span className="value number">{data}</span>
  }

  if (typeof data === 'boolean') {
    return <span className="value boolean">{data ? 'true' : 'false'}</span>
  }

  if (Array.isArray(data)) {
    if (data.length === 0) {
      return <span className="value array">[]</span>
    }
    return (
      <div className="array-view" style={{ marginLeft: depth > 0 ? '1rem' : 0 }}>
        {data.map((item, index) => (
          <div key={index} className="array-item">
            <span className="array-index">[{index}]</span>
            <ParsedView data={item} depth={depth + 1} />
          </div>
        ))}
      </div>
    )
  }

  if (typeof data === 'object') {
    const entries = Object.entries(data as Record<string, unknown>)
    if (entries.length === 0) {
      return <span className="value object">{'{}'}</span>
    }
    
    // description 필드 추출
    const desc = getDescription(data as Record<string, unknown>)
    // description 필드는 제외하고 렌더링
    const filteredEntries = entries.filter(([key]) => !COMMENT_FIELDS.has(key.toLowerCase()))
    
    return (
      <div className="object-view" style={{ marginLeft: depth > 0 ? '1rem' : 0 }}>
        {filteredEntries.map(([key, value]) => {
          // name 필드일 때만 description을 옆에 표시
          const showDesc = key === 'name' ? desc : null
          
          return (
            <div key={key} className="object-entry">
              <span className="object-key">{key}:</span>
              <ParsedView data={value} depth={depth + 1} siblingDescription={showDesc} />
            </div>
          )
        })}
      </div>
    )
  }

  return <span className="value">{String(data)}</span>
}
