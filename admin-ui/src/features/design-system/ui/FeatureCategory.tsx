/**
 * Feature Category - Feature별 컴포넌트 카테고리 페이지
 *
 * 각 Feature에서 사용하는 컴포넌트들을 그리드 형태로 표시합니다.
 */

import { Link, useParams } from 'react-router-dom'
import {
  Bell,
  ChevronRight,
  Construction,
  FileEdit,
  FolderSearch,
  GitBranch,
  Globe,
  Heart,
  Activity,
  Inbox,
  LayoutDashboard,
  Network,
  Package,
  Play,
  ScrollText,
  Webhook,
  Workflow,
} from 'lucide-react'
import './FeatureCategory.css'

// ============================================================================
// Feature Data - 각 Feature의 컴포넌트 목록
// ============================================================================

interface FeatureInfo {
  title: string
  description: string
  icon: React.ReactNode
  components: Array<{
    name: string
    description: string
    path: string  // 실제 컴포넌트 경로
  }>
}

const FEATURES: Record<string, FeatureInfo> = {
  alerts: {
    title: 'Alerts',
    description: '알림 관리 기능에서 사용되는 컴포넌트들입니다.',
    icon: <Bell size={32} />,
    components: [
      { name: 'AlertsPage', description: '알림 목록 페이지', path: 'alerts/ui/AlertsPage' },
    ],
  },
  'contract-editor': {
    title: 'Contract Editor',
    description: '계약 편집기에서 사용되는 컴포넌트들입니다.',
    icon: <FileEdit size={32} />,
    components: [
      { name: 'ContractEditorPage', description: '계약 편집 페이지', path: 'contract-editor/ui/ContractEditorPage' },
      { name: 'ChangeSummary', description: '변경 요약 패널', path: 'contract-editor/components/ChangeSummary' },
      { name: 'ValidationPanel', description: '유효성 검사 패널', path: 'contract-editor/components/ValidationPanel' },
      { name: 'ImpactGraph', description: '영향 분석 그래프', path: 'contract-editor/components/ImpactGraph' },
      { name: 'MeaningPanel', description: '계약 의미 설명 패널', path: 'contract-editor/components/MeaningPanel' },
    ],
  },
  contracts: {
    title: 'Contracts',
    description: '계약 관리에서 사용되는 컴포넌트들입니다.',
    icon: <ScrollText size={32} />,
    components: [
      { name: 'ContractsPage', description: '계약 목록 페이지', path: 'contracts/ui/ContractsPage' },
      { name: 'ContractDetail', description: '계약 상세 페이지', path: 'contracts/ui/ContractDetail' },
    ],
  },
  dashboard: {
    title: 'Dashboard',
    description: '대시보드에서 사용되는 컴포넌트들입니다.',
    icon: <LayoutDashboard size={32} />,
    components: [
      { name: 'DashboardPage', description: '메인 대시보드', path: 'dashboard/ui/DashboardPage' },
      { name: 'SummaryRow', description: '요약 정보 행', path: 'dashboard/components/SummaryRow' },
      { name: 'OutboxPanel', description: 'Outbox 패널', path: 'dashboard/components/OutboxPanel' },
      { name: 'ActionsPanel', description: '액션 패널', path: 'dashboard/components/ActionsPanel' },
      { name: 'SliceTypesSection', description: 'Slice 타입 섹션', path: 'dashboard/components/SliceTypesSection' },
      { name: 'HourlyChart', description: '시간별 차트', path: 'dashboard/components/HourlyChart' },
    ],
  },
  environment: {
    title: 'Environment',
    description: '환경 설정에서 사용되는 컴포넌트들입니다.',
    icon: <Globe size={32} />,
    components: [
      { name: 'EnvironmentPage', description: '환경 설정 페이지', path: 'environment/ui/EnvironmentPage' },
    ],
  },
  explorer: {
    title: 'Explorer',
    description: '데이터 탐색기에서 사용되는 컴포넌트들입니다.',
    icon: <FolderSearch size={32} />,
    components: [
      { name: 'DataExplorer', description: '데이터 탐색기 메인', path: 'explorer/ui/DataExplorer' },
      { name: 'JsonViewer', description: 'JSON 데이터 뷰어', path: 'explorer/components/JsonViewer' },
      { name: 'DiffViewer', description: '데이터 비교 뷰어', path: 'explorer/components/DiffViewer' },
      { name: 'LineageGraph', description: '데이터 계보 그래프', path: 'explorer/components/LineageGraph' },
      { name: 'DataTable', description: '데이터 테이블', path: 'explorer/components/DataTable' },
      { name: 'RawDataPanel', description: 'Raw 데이터 패널', path: 'explorer/components/RawDataPanel' },
      { name: 'SliceList', description: 'Slice 목록', path: 'explorer/components/SliceList' },
      { name: 'ViewPreview', description: 'View 미리보기', path: 'explorer/components/ViewPreview' },
      { name: 'SearchBar', description: '검색 바', path: 'explorer/components/SearchBar' },
    ],
  },
  health: {
    title: 'Health',
    description: '시스템 헬스체크에서 사용되는 컴포넌트들입니다.',
    icon: <Heart size={32} />,
    components: [
      { name: 'HealthPage', description: '헬스 체크 페이지', path: 'health/ui/HealthPage' },
    ],
  },
  observability: {
    title: 'Observability',
    description: '모니터링/관측성에서 사용되는 컴포넌트들입니다.',
    icon: <Activity size={32} />,
    components: [
      { name: 'ObservabilityPage', description: '관측성 대시보드', path: 'observability/ui/ObservabilityPage' },
    ],
  },
  outbox: {
    title: 'Outbox',
    description: 'Outbox 관리에서 사용되는 컴포넌트들입니다.',
    icon: <Inbox size={32} />,
    components: [
      { name: 'Outbox', description: 'Outbox 메인 페이지', path: 'outbox/ui/Outbox' },
      { name: 'RecentTable', description: '최근 이벤트 테이블', path: 'outbox/components/RecentTable' },
      { name: 'FailedTable', description: '실패 이벤트 테이블', path: 'outbox/components/FailedTable' },
      { name: 'DlqTable', description: 'DLQ 테이블', path: 'outbox/components/DlqTable' },
      { name: 'StaleTable', description: 'Stale 이벤트 테이블', path: 'outbox/components/StaleTable' },
      { name: 'OutboxDetailModal', description: '이벤트 상세 모달', path: 'outbox/components/OutboxDetailModal' },
    ],
  },
  pipeline: {
    title: 'Pipeline',
    description: '파이프라인 관리에서 사용되는 컴포넌트들입니다.',
    icon: <GitBranch size={32} />,
    components: [
      { name: 'PipelinePage', description: '파이프라인 페이지', path: 'pipeline/ui/PipelinePage' },
    ],
  },
  playground: {
    title: 'Playground',
    description: '플레이그라운드에서 사용되는 컴포넌트들입니다.',
    icon: <Play size={32} />,
    components: [
      { name: 'PlaygroundPage', description: '플레이그라운드 페이지', path: 'playground/PlaygroundPage' },
    ],
  },
  traces: {
    title: 'Traces',
    description: '트레이싱에서 사용되는 컴포넌트들입니다.',
    icon: <Network size={32} />,
    components: [
      { name: 'TracesPage', description: '트레이스 페이지', path: 'traces/ui/TracesPage' },
      { name: 'TracesStatsCards', description: '트레이스 통계 카드', path: 'traces/components/TracesStatsCards' },
      { name: 'TraceList', description: '트레이스 목록', path: 'traces/components/TraceList' },
      { name: 'WaterfallTimeline', description: '워터폴 타임라인', path: 'traces/components/WaterfallTimeline' },
      { name: 'SpanDetails', description: 'Span 상세', path: 'traces/components/SpanDetails' },
    ],
  },
  webhooks: {
    title: 'Webhooks',
    description: '웹훅 관리에서 사용되는 컴포넌트들입니다.',
    icon: <Webhook size={32} />,
    components: [
      { name: 'WebhooksPage', description: '웹훅 페이지', path: 'webhooks/ui/WebhooksPage' },
    ],
  },
  workflow: {
    title: 'Workflow',
    description: '워크플로우 캔버스에서 사용되는 컴포넌트들입니다.',
    icon: <Workflow size={32} />,
    components: [
      { name: 'WorkflowCanvasPage', description: '워크플로우 캔버스', path: 'workflow/ui/WorkflowCanvasPage' },
      { name: 'BaseNode', description: '기본 노드', path: 'workflow/components/nodes/BaseNode' },
      { name: 'RawDataNode', description: 'RawData 노드', path: 'workflow/components/nodes/RawDataNode' },
      { name: 'RuleNode', description: 'Rule 노드', path: 'workflow/components/nodes/RuleNode' },
      { name: 'SliceNode', description: 'Slice 노드', path: 'workflow/components/nodes/SliceNode' },
      { name: 'ViewNode', description: 'View 노드', path: 'workflow/components/nodes/ViewNode' },
      { name: 'SinkNode', description: 'Sink 노드', path: 'workflow/components/nodes/SinkNode' },
      { name: 'LabeledEdge', description: '라벨 엣지', path: 'workflow/components/edges/LabeledEdge' },
      { name: 'WorkflowDetailPanel', description: '워크플로우 상세 패널', path: 'workflow/components/WorkflowDetailPanel' },
    ],
  },
}

// ============================================================================
// Feature Component Card
// ============================================================================

interface FeatureComponentCardProps {
  feature: string
  name: string
  description: string
  path: string
}

function FeatureComponentCard({ feature, name, description, path }: FeatureComponentCardProps) {
  return (
    <Link
      to={`/design-system/features/${feature}/${name.toLowerCase()}`}
      className="ds-feature-card"
    >
      {/* Preview Area */}
      <div className="ds-feature-card-preview">
        <div className="ds-feature-card-placeholder">
          <code className="ds-feature-card-path">{path}</code>
        </div>
      </div>

      {/* Info Area */}
      <div className="ds-feature-card-info">
        <div className="ds-feature-card-header">
          <span className="ds-feature-card-name">{name}</span>
          <ChevronRight size={16} className="ds-feature-card-arrow" />
        </div>
        <p className="ds-feature-card-description">{description}</p>
      </div>
    </Link>
  )
}

// ============================================================================
// All Features Overview
// ============================================================================

function AllFeaturesOverview() {
  return (
    <div className="ds-section">
      <header className="ds-section-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.5rem' }}>
          <span style={{ color: 'var(--accent-cyan)' }}><Package size={32} /></span>
          <h1 className="ds-section-title" style={{ margin: 0 }}>Features</h1>
        </div>
        <p className="ds-section-description">
          각 Feature에서 사용되는 컴포넌트들을 살펴볼 수 있습니다.
          Feature별로 분리된 컴포넌트들은 해당 도메인에 특화된 UI를 제공합니다.
        </p>
      </header>

      <div className="ds-feature-overview-grid">
        {Object.entries(FEATURES).map(([key, feature]) => (
          <Link
            key={key}
            to={`/design-system/features/${key}`}
            className="ds-feature-overview-card"
          >
            <div className="ds-feature-overview-icon" style={{ color: 'var(--accent-cyan)' }}>
              {feature.icon}
            </div>
            <div className="ds-feature-overview-content">
              <h3 className="ds-feature-overview-title">{feature.title}</h3>
              <p className="ds-feature-overview-description">{feature.description}</p>
              <div className="ds-feature-overview-count">
                {feature.components.length} components
              </div>
            </div>
            <ChevronRight size={20} className="ds-feature-overview-arrow" />
          </Link>
        ))}
      </div>
    </div>
  )
}

// ============================================================================
// Main Component
// ============================================================================

export function FeatureCategory() {
  const { feature } = useParams<{ feature: string }>()

  // 전체 Features 개요 페이지
  if (!feature) {
    return <AllFeaturesOverview />
  }

  const featureInfo = FEATURES[feature]

  if (!featureInfo) {
    return (
      <div className="ds-placeholder">
        <Construction size={48} className="ds-placeholder-icon" />
        <h2 className="ds-placeholder-title">Feature를 찾을 수 없습니다</h2>
        <p className="ds-placeholder-description">
          좌측 메뉴에서 원하는 Feature를 선택해주세요.
        </p>
      </div>
    )
  }

  return (
    <div className="ds-section">
      <header className="ds-section-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.5rem' }}>
          <span style={{ color: 'var(--accent-cyan)' }}>{featureInfo.icon}</span>
          <h1 className="ds-section-title" style={{ margin: 0 }}>{featureInfo.title}</h1>
        </div>
        <p className="ds-section-description">{featureInfo.description}</p>
      </header>

      {/* Grid Layout */}
      <div className="ds-feature-grid">
        {featureInfo.components.map((component) => (
          <FeatureComponentCard
            key={component.name}
            feature={feature}
            name={component.name}
            description={component.description}
            path={component.path}
          />
        ))}
      </div>
    </div>
  )
}
