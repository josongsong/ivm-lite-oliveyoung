import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import { Link, useSearchParams } from 'react-router-dom'
import { 
  ArrowRight, 
  CheckCircle2, 
  Clock, 
  Database, 
  Eye,
  FileCode2,
  HelpCircle,
  Layers,
  Search,
  X
} from 'lucide-react'
import { fetchApi } from '@/shared/api'
import type { ContractListResponse } from '@/shared/types'
import { ApiError, Loading, PageHeader } from '@/shared/ui'
import './Contracts.css'

// Contract 종류별 정보 (한글 설명 포함)
const contractKindInfo: Record<string, { label: string; color: string; description: string }> = {
  'ENTITY_SCHEMA': {
    label: 'Entity Schema',
    color: 'cyan',
    description: '엔티티의 데이터 구조를 정의합니다. 필드 타입, 제약조건, 슬라이스 구성 등을 포함합니다.'
  },
  'RULESET': {
    label: 'RuleSet',
    color: 'purple',
    description: '슬라이싱 규칙을 정의합니다. 어떤 필드를 어떤 슬라이스로 분리할지 결정합니다.'
  },
  'VIEW_DEFINITION': {
    label: 'View Definition',
    color: 'green',
    description: '여러 슬라이스를 조합하여 뷰를 정의합니다. 조인 조건, 필터링 규칙을 포함합니다.'
  },
  'SINKRULE': {
    label: 'Sink Rule',
    color: 'orange',
    description: '외부 시스템으로 데이터를 전송하는 규칙을 정의합니다. Kafka, OpenSearch 등의 대상을 설정합니다.'
  },
  'JoinSpecContract': {
    label: 'Join Spec',
    color: 'magenta',
    description: '엔티티 간 조인 규칙을 정의합니다. 관계형 데이터 조합에 사용됩니다.'
  },
  'ChangeSetContract': {
    label: 'ChangeSet',
    color: 'yellow',
    description: '변경 감지 및 델타 계산 규칙을 정의합니다. 증분 업데이트에 사용됩니다.'
  },
}

// 탭 필터용 (주요 타입만)
const contractTabs = [
  { key: 'all', label: '전체', icon: FileCode2 },
  { key: 'ENTITY_SCHEMA', label: 'Schema', icon: Database },
  { key: 'RULESET', label: 'RuleSet', icon: Layers },
  { key: 'VIEW_DEFINITION', label: 'View', icon: Eye },
  { key: 'SINKRULE', label: 'Sink', icon: ArrowRight },
]

export function Contracts() {
  const [searchParams] = useSearchParams()
  const [selectedKind, setSelectedKind] = useState('all')
  const [searchTerm, setSearchTerm] = useState('')
  const [tooltipKind, setTooltipKind] = useState<string | null>(null)

  // URL 파라미터에서 search 읽기
  useEffect(() => {
    const search = searchParams.get('search')
    if (search) {
      setSearchTerm(search)
    }
  }, [searchParams])

  const { data: contractsData, isLoading, isError, refetch } = useQuery({
    queryKey: ['contracts'],
    queryFn: () => fetchApi<ContractListResponse>('/contracts'),
    retry: 1,
  })

  const filteredContracts = contractsData?.contracts?.filter(contract => {
    const matchesKind = selectedKind === 'all' || contract.kind === selectedKind
    const matchesSearch = !searchTerm ||
      contract.id.toLowerCase().includes(searchTerm.toLowerCase()) ||
      contract.kind.toLowerCase().includes(searchTerm.toLowerCase())
    return matchesKind && matchesSearch
  }) ?? []

  if (isLoading) return <Loading />

  if (isError) {
    return (
      <div className="page-container">
        <PageHeader title="Contracts" subtitle="스키마, 룰셋, 뷰 정의 등 시스템 계약을 관리합니다" />
        <ApiError onRetry={refetch} />
      </div>
    )
  }

  // 실제 contract 목록에서 kind별 개수 계산 (동기화)
  const actualByKind: Record<string, number> = {}
  contractsData?.contracts?.forEach(c => {
    actualByKind[c.kind] = (actualByKind[c.kind] || 0) + 1
  })

  return (
    <div className="page-container">
      <PageHeader title="Contracts" subtitle="스키마, 룰셋, 뷰 정의 등 시스템 계약을 관리합니다" />

      {/* Stats Overview - 고정 그리드 */}
      <motion.div 
        className="contract-stats"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <div className="stat-overview">
          <div className="stat-total">
            <span className="stat-number">{contractsData?.contracts?.length ?? 0}</span>
            <span className="stat-text">Total Contracts</span>
          </div>
          <div className="stat-breakdown-grid">
            {Object.entries(actualByKind).map(([kind, count]) => {
              const info = contractKindInfo[kind] || { label: kind, color: 'cyan', description: '' }
              return (
                <div 
                  key={kind} 
                  className={`stat-item-fixed ${info.color}`}
                  onClick={() => setSelectedKind(kind)}
                >
                  <span className="stat-count">{count}</span>
                  <span className="stat-kind">{info.label}</span>
                  <button 
                    className="stat-help"
                    onClick={(e) => {
                      e.stopPropagation()
                      setTooltipKind(tooltipKind === kind ? null : kind)
                    }}
                  >
                    <HelpCircle size={12} />
                  </button>
                  
                  {/* 툴팁 */}
                  {tooltipKind === kind && (
                    <motion.div 
                      className="stat-tooltip"
                      initial={{ opacity: 0, y: -5 }}
                      animate={{ opacity: 1, y: 0 }}
                      exit={{ opacity: 0 }}
                    >
                      <button 
                        className="tooltip-close"
                        onClick={(e) => {
                          e.stopPropagation()
                          setTooltipKind(null)
                        }}
                      >
                        <X size={12} />
                      </button>
                      <p>{info.description}</p>
                    </motion.div>
                  )}
                </div>
              )
            })}
          </div>
        </div>
      </motion.div>

      {/* Filter Bar */}
      <motion.div 
        className="filter-bar"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
      >
        <div className="search-box">
          <Search size={16} />
          <input
            type="text"
            placeholder="Search contracts..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>
        <div className="kind-tabs">
          {contractTabs.map(({ key, label, icon: Icon }) => (
            <button
              key={key}
              className={`kind-tab ${selectedKind === key ? 'active' : ''}`}
              onClick={() => setSelectedKind(key)}
            >
              <Icon size={14} />
              <span>{label}</span>
            </button>
          ))}
        </div>
      </motion.div>

      {/* Contract Grid */}
      <motion.div 
        className="contracts-grid"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.2 }}
      >
        <AnimatePresence mode="popLayout">
          {filteredContracts.map((contract) => {
            const info = contractKindInfo[contract.kind] || { label: contract.kind, color: 'cyan', description: '' }
            
            return (
              <motion.div
                key={`${contract.kind}-${contract.id}-${contract.version}`}
                layout
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.9 }}
              >
                <Link 
                  to={`/contracts/${contract.kind}/${encodeURIComponent(contract.id)}`}
                  className={`contract-card ${info.color}`}
                >
                  <div className="contract-header">
                    <span className={`contract-kind badge-${info.color}`}>
                      {info.label}
                    </span>
                    <span className={`contract-status ${contract.status.toLowerCase()}`}>
                      {contract.status === 'ACTIVE' ? <CheckCircle2 size={12} /> : <Clock size={12} />}
                      {contract.status}
                    </span>
                  </div>
                  
                  <h3 className="contract-id">{contract.id}</h3>
                  
                  <div className="contract-meta">
                    <span className="contract-version">v{contract.version}</span>
                    <span className="contract-file">{contract.fileName}</span>
                  </div>

                  <div className="contract-preview">
                    <code>{contract.content.slice(0, 80)}...</code>
                  </div>

                  <div className="contract-arrow">
                    <ArrowRight size={14} />
                  </div>
                </Link>
              </motion.div>
            )
          })}
        </AnimatePresence>
      </motion.div>

      {filteredContracts.length === 0 && (
        <motion.div 
          className="empty-state"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
        >
          <FileCode2 size={48} strokeWidth={1} />
          <h3 className="empty-state-title">Contract를 찾을 수 없습니다</h3>
          <p>검색어나 필터를 변경해보세요</p>
        </motion.div>
      )}
    </div>
  )
}
