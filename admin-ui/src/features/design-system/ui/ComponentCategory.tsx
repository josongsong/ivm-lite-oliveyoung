/**
 * Component Category - 컴포넌트 카테고리 페이지
 *
 * 그리드 형태의 컴포넌트 미리보기 카드를 제공합니다.
 */

import { Link, useParams } from 'react-router-dom'
import {
  AlertCircle,
  Bell,
  Blocks,
  ChevronRight,
  Construction,
  FormInput as FormInputIcon,
  LayoutGrid,
  MousePointer,
  Search,
  Settings,
  Table2,
  Trash2,
} from 'lucide-react'
import {
  Alert,
  Banner,
  Button,
  Card,
  Chip,
  EmptyState,
  IconButton,
  InfoRow,
  InlineAlert,
  Input,
  Label,
  Loading,
  Pagination,
  Select,
  Skeleton,
  StatusBadge,
  Switch,
  TextArea,
} from '@/shared/ui'
import './ComponentCategory.css'

// ============================================================================
// Component Previews - 각 컴포넌트의 미리보기 렌더링
// ============================================================================

const COMPONENT_PREVIEWS: Record<string, React.ReactNode> = {
  Button: (
    <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
      <Button size="sm">Primary</Button>
      <Button variant="secondary" size="sm">Secondary</Button>
      <Button variant="ghost" size="sm">Ghost</Button>
    </div>
  ),
  IconButton: (
    <div style={{ display: 'flex', gap: '0.5rem' }}>
      <IconButton icon={Settings} size="sm" aria-label="Settings" />
      <IconButton icon={Search} size="sm" aria-label="Search" />
      <IconButton icon={Trash2} variant="danger" size="sm" aria-label="Delete" />
    </div>
  ),
  Input: (
    <Input placeholder="Enter text..." size="sm" style={{ width: '100%' }} />
  ),
  TextArea: (
    <TextArea placeholder="Enter long text..." rows={2} style={{ width: '100%', fontSize: '0.75rem' }} />
  ),
  Select: (
    <Select
      value="option1"
      onChange={() => {}}
      options={[
        { value: 'option1', label: 'Option 1' },
        { value: 'option2', label: 'Option 2' },
      ]}
      size="sm"
    />
  ),
  Switch: (
    <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
      <Switch checked={false} onChange={() => {}} size="sm" />
      <Switch checked={true} onChange={() => {}} size="sm" />
    </div>
  ),
  Loading: (
    <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
      <Loading size="sm" />
      <Loading size="md" />
    </div>
  ),
  Modal: (
    <div style={{
      background: 'var(--bg-card)',
      border: '1px solid var(--border-color)',
      borderRadius: '8px',
      padding: '0.75rem',
      fontSize: '0.75rem',
    }}>
      <div style={{ fontWeight: 600, marginBottom: '0.25rem' }}>Modal Title</div>
      <div style={{ color: 'var(--text-secondary)' }}>Modal content...</div>
    </div>
  ),
  Alert: (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
      <Alert variant="info" size="sm">Info alert</Alert>
    </div>
  ),
  Banner: (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
      <Banner variant="info" size="sm">Banner message</Banner>
    </div>
  ),
  InlineAlert: (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
      <InlineAlert variant="info">Inline alert</InlineAlert>
    </div>
  ),
  Toast: (
    <div style={{
      background: 'var(--bg-card)',
      border: '1px solid var(--border-color)',
      borderRadius: '8px',
      padding: '0.75rem',
      fontSize: '0.75rem',
      display: 'flex',
      alignItems: 'center',
      gap: '0.5rem',
    }}>
      <div style={{ color: 'var(--accent-cyan)' }}>✓</div>
      <div>Toast notification</div>
    </div>
  ),
  EmptyState: (
    <div style={{ width: '100%' }}>
      <EmptyState
        title="No data"
        description="Empty state message"
        variant="compact"
        size="sm"
        animate={false}
      />
    </div>
  ),
  Skeleton: (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', width: '100%' }}>
      <Skeleton width="100%" height={16} />
      <Skeleton width="80%" height={16} />
      <Skeleton width="60%" height={16} />
    </div>
  ),
  Tooltip: (
    <div style={{
      background: 'var(--bg-tertiary)',
      padding: '0.5rem 0.75rem',
      borderRadius: '6px',
      fontSize: '0.75rem',
      display: 'inline-block',
    }}>
      Tooltip text
    </div>
  ),
  Card: (
    <Card style={{ padding: '0.75rem' }}>
      <div style={{ fontWeight: 600, fontSize: '0.875rem' }}>Card Title</div>
      <div style={{ color: 'var(--text-secondary)', fontSize: '0.75rem' }}>Card content</div>
    </Card>
  ),
  Section: (
    <div style={{
      borderLeft: '2px solid var(--accent-cyan)',
      paddingLeft: '0.75rem',
    }}>
      <div style={{ fontWeight: 600, fontSize: '0.875rem' }}>Section Header</div>
      <div style={{ color: 'var(--text-secondary)', fontSize: '0.75rem' }}>Section content</div>
    </div>
  ),
  Accordion: (
    <div style={{
      background: 'var(--bg-card)',
      border: '1px solid var(--border-color)',
      borderRadius: '6px',
      fontSize: '0.75rem',
    }}>
      <div style={{ padding: '0.5rem 0.75rem', borderBottom: '1px solid var(--border-color)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span>Accordion Item 1</span>
        <span style={{ color: 'var(--text-muted)' }}>+</span>
      </div>
      <div style={{ padding: '0.5rem 0.75rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span>Accordion Item 2</span>
        <span style={{ color: 'var(--text-muted)' }}>+</span>
      </div>
    </div>
  ),
  Table: (
    <div style={{
      background: 'var(--bg-card)',
      border: '1px solid var(--border-color)',
      borderRadius: '6px',
      overflow: 'hidden',
      fontSize: '0.75rem',
    }}>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', borderBottom: '1px solid var(--border-color)', background: 'var(--bg-tertiary)' }}>
        <div style={{ padding: '0.5rem', fontWeight: 600 }}>Name</div>
        <div style={{ padding: '0.5rem', fontWeight: 600 }}>Status</div>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr' }}>
        <div style={{ padding: '0.5rem' }}>Item 1</div>
        <div style={{ padding: '0.5rem' }}>Active</div>
      </div>
    </div>
  ),
  StatusBadge: (
    <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
      <StatusBadge status="healthy" />
      <StatusBadge status="pending" />
      <StatusBadge status="failed" />
    </div>
  ),
  Chip: (
    <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
      <Chip>Default</Chip>
      <Chip variant="primary">Primary</Chip>
      <Chip variant="success">Success</Chip>
    </div>
  ),
  Label: (
    <div style={{ display: 'flex', gap: '0.5rem' }}>
      <Label>Default Label</Label>
      <Label required>Required</Label>
    </div>
  ),
  YamlViewer: (
    <div style={{
      background: 'var(--bg-card)',
      border: '1px solid var(--border-color)',
      borderRadius: '8px',
      padding: '0.75rem',
      fontSize: '0.75rem',
      fontFamily: 'var(--font-mono)',
      color: 'var(--text-secondary)',
    }}>
      <div>name: example</div>
      <div>  value: 123</div>
    </div>
  ),
  InfoRow: (
    <div style={{
      background: 'var(--bg-card)',
      border: '1px solid var(--border-color)',
      borderRadius: '8px',
      padding: '0.75rem',
      fontSize: '0.75rem',
    }}>
      <InfoRow label="Status" value="Active" />
      <InfoRow label="ID" value="abc-123" mono />
    </div>
  ),
  Tabs: (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
      <div style={{ display: 'flex', gap: '0.25rem', background: 'var(--bg-tertiary)', padding: '0.25rem', borderRadius: '6px' }}>
        <div style={{ padding: '0.375rem 0.75rem', background: 'var(--bg-card)', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 500 }}>Tab 1</div>
        <div style={{ padding: '0.375rem 0.75rem', fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Tab 2</div>
      </div>
      <div style={{ padding: '0.5rem', fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Tab 1 content</div>
    </div>
  ),
  Pagination: (
    <Pagination
      page={2}
      totalPages={5}
      totalItems={50}
      pageSize={10}
      onPageChange={() => {}}
    />
  ),
  // Additional components - placeholders for now
  ToggleGroup: (
    <div style={{ display: 'flex', gap: '0.5rem' }}>
      <Switch checked={true} onChange={() => {}} size="sm" />
      <Switch checked={false} onChange={() => {}} size="sm" />
    </div>
  ),
  Form: (
    <div style={{
      background: 'var(--bg-card)',
      border: '1px solid var(--border-color)',
      borderRadius: '8px',
      padding: '0.75rem',
      fontSize: '0.75rem',
    }}>
      <div style={{ fontWeight: 600, marginBottom: '0.5rem' }}>Form Container</div>
      <Input placeholder="Form input..." size="sm" />
    </div>
  ),
  FormRow: (
    <div style={{ display: 'flex', gap: '0.5rem', fontSize: '0.75rem' }}>
      <Input placeholder="Field 1" size="sm" style={{ flex: 1 }} />
      <Input placeholder="Field 2" size="sm" style={{ flex: 1 }} />
    </div>
  ),
  FormGroup: (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', fontSize: '0.75rem' }}>
      <Label>Form Group</Label>
      <Input placeholder="Grouped input..." size="sm" />
    </div>
  ),
  FormInput: (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', fontSize: '0.75rem' }}>
      <Label>Form Input</Label>
      <Input placeholder="Form input..." size="sm" />
    </div>
  ),
  FormTextArea: (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', fontSize: '0.75rem' }}>
      <Label>Form TextArea</Label>
      <TextArea placeholder="Form textarea..." rows={2} style={{ fontSize: '0.75rem' }} />
    </div>
  ),
  FileUpload: (
    <div style={{
      background: 'var(--bg-card)',
      border: '2px dashed var(--border-color)',
      borderRadius: '8px',
      padding: '1rem',
      textAlign: 'center',
      fontSize: '0.75rem',
      color: 'var(--text-secondary)',
    }}>
      📁 파일 업로드
    </div>
  ),
  SchemaSelector: (
    <Select
      value="schema1"
      onChange={() => {}}
      options={[
        { value: 'schema1', label: 'Schema 1' },
        { value: 'schema2', label: 'Schema 2' },
      ]}
      size="sm"
    />
  ),
  SearchFilter: (
    <Input placeholder="검색 필터..." leftIcon={<Search size={14} />} size="sm" />
  ),
  TableHeader: (
    <div style={{
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      padding: '0.75rem',
      background: 'var(--bg-tertiary)',
      borderRadius: '6px',
      fontSize: '0.75rem',
    }}>
      <div style={{ fontWeight: 600 }}>Table Header</div>
      <Button size="sm" variant="ghost">Action</Button>
    </div>
  ),
  PanelHeader: (
    <div style={{
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      padding: '0.75rem',
      fontSize: '0.75rem',
    }}>
      <div style={{ fontWeight: 600 }}>Panel Header</div>
      <Button size="sm" variant="ghost">View All</Button>
    </div>
  ),
  ActionCard: (
    <Card style={{ padding: '1rem', textAlign: 'center', cursor: 'pointer' }}>
      <div style={{ fontSize: '1.5rem', marginBottom: '0.5rem' }}>⚡</div>
      <div style={{ fontWeight: 600, fontSize: '0.875rem' }}>Action Card</div>
    </Card>
  ),
  StatCard: (
    <Card style={{ padding: '1rem' }}>
      <div style={{ fontSize: '1.5rem', fontWeight: 700, fontFamily: 'var(--font-mono)' }}>42</div>
      <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Statistics</div>
    </Card>
  ),
  StatsCard: (
    <Card style={{ padding: '1rem' }}>
      <div style={{ fontSize: '1.25rem', fontWeight: 700, fontFamily: 'var(--font-mono)' }}>98%</div>
      <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Uptime</div>
    </Card>
  ),
  StatsGrid: (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '0.5rem', fontSize: '0.75rem' }}>
      <Card style={{ padding: '0.75rem' }}>
        <div style={{ fontWeight: 600 }}>42</div>
        <div style={{ color: 'var(--text-secondary)' }}>Items</div>
      </Card>
      <Card style={{ padding: '0.75rem' }}>
        <div style={{ fontWeight: 600 }}>12</div>
        <div style={{ color: 'var(--text-secondary)' }}>Active</div>
      </Card>
    </div>
  ),
  BreakdownItem: (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.75rem', padding: '0.5rem' }}>
      <span>Item Name</span>
      <Chip>42%</Chip>
    </div>
  ),
  SectionHeader: (
    <div style={{
      borderLeft: '2px solid var(--accent-cyan)',
      paddingLeft: '0.75rem',
      fontSize: '0.875rem',
      fontWeight: 600,
    }}>
      Section Header
    </div>
  ),
  CollapsibleSection: (
    <div style={{
      border: '1px solid var(--border-color)',
      borderRadius: '6px',
      padding: '0.75rem',
      fontSize: '0.75rem',
    }}>
      <div style={{ fontWeight: 600, marginBottom: '0.5rem' }}>Collapsible Section</div>
      <div style={{ color: 'var(--text-secondary)' }}>Content...</div>
    </div>
  ),
  GroupPanel: (
    <Card style={{ padding: '0.75rem', fontSize: '0.75rem' }}>
      <div style={{ fontWeight: 600, marginBottom: '0.5rem' }}>Group Panel</div>
      <div style={{ color: 'var(--text-secondary)' }}>Panel content</div>
    </Card>
  ),
  Divider: (
    <div style={{ height: '1px', background: 'var(--border-color)', margin: '0.5rem 0' }} />
  ),
  ChipGroup: (
    <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
      <Chip>Tag 1</Chip>
      <Chip>Tag 2</Chip>
      <Chip>Tag 3</Chip>
    </div>
  ),
  NoResults: (
    <EmptyState
      title="No results found"
      description="Try adjusting your search"
      variant="compact"
      size="sm"
      animate={false}
    />
  ),
  NoData: (
    <EmptyState
      title="No data"
      description="No data available"
      variant="compact"
      size="sm"
      animate={false}
    />
  ),
  ErrorState: (
    <EmptyState
      icon={<AlertCircle size={48} />}
      title="Error"
      description="Something went wrong"
      variant="compact"
      size="sm"
      animate={false}
    />
  ),
  LoadingState: (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', alignItems: 'center', padding: '2rem' }}>
      <Loading size="md" />
      <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Loading...</div>
    </div>
  ),
  JsonViewer: (
    <div style={{
      background: 'var(--bg-card)',
      border: '1px solid var(--border-color)',
      borderRadius: '8px',
      padding: '0.75rem',
      fontSize: '0.75rem',
      fontFamily: 'var(--font-mono)',
      color: 'var(--text-secondary)',
    }}>
      <div>{"{"}</div>
      <div style={{ paddingLeft: '1rem' }}>"key": "value"</div>
      <div>{"}"}</div>
    </div>
  ),
  DiffViewer: (
    <div style={{
      background: 'var(--bg-card)',
      border: '1px solid var(--border-color)',
      borderRadius: '8px',
      padding: '0.75rem',
      fontSize: '0.75rem',
      fontFamily: 'var(--font-mono)',
    }}>
      <div style={{ color: 'var(--accent-green)' }}>+ Added line</div>
      <div style={{ color: 'var(--accent-red)' }}>- Removed line</div>
    </div>
  ),
  SearchBar: (
    <Input placeholder="Search..." leftIcon={<Search size={16} />} size="sm" />
  ),
  LineageGraph: (
    <div style={{
      background: 'var(--bg-card)',
      border: '1px solid var(--border-color)',
      borderRadius: '8px',
      padding: '1rem',
      textAlign: 'center',
      fontSize: '0.75rem',
      color: 'var(--text-secondary)',
    }}>
      📊 Lineage Graph
    </div>
  ),
}

// ============================================================================
// Category Data
// ============================================================================

interface CategoryInfo {
  title: string
  description: string
  icon: React.ReactNode
  components: Array<{ name: string; description: string }>
}

const CATEGORIES: Record<string, CategoryInfo> = {
  actions: {
    title: 'Actions',
    description: '사용자 인터랙션을 트리거하는 버튼 및 액션 컴포넌트입니다.',
    icon: <MousePointer size={32} />,
    components: [
      { name: 'Button', description: '기본 버튼 컴포넌트' },
      { name: 'IconButton', description: '아이콘만 있는 버튼' },
    ],
  },
  inputs: {
    title: 'Inputs',
    description: '사용자 입력을 받는 폼 컴포넌트입니다.',
    icon: <FormInputIcon size={32} />,
    components: [
      { name: 'Input', description: '텍스트 입력 필드' },
      { name: 'TextArea', description: '멀티라인 텍스트 입력' },
      { name: 'Select', description: '드롭다운 선택' },
      { name: 'Switch', description: '토글 스위치' },
      { name: 'ToggleGroup', description: '토글 그룹' },
      { name: 'Form', description: '폼 컨테이너' },
      { name: 'FormRow', description: '폼 행' },
      { name: 'FormGroup', description: '폼 그룹' },
      { name: 'FormInput', description: '폼 입력 필드' },
      { name: 'FormTextArea', description: '폼 텍스트 영역' },
      { name: 'FileUpload', description: '파일 업로드' },
      { name: 'SchemaSelector', description: '스키마 선택기' },
      { name: 'SearchFilter', description: '검색 필터' },
    ],
  },
  feedback: {
    title: 'Feedback',
    description: '사용자에게 피드백을 제공하는 컴포넌트입니다.',
    icon: <Bell size={32} />,
    components: [
      { name: 'Loading', description: '로딩 인디케이터' },
      { name: 'Modal', description: '모달 다이얼로그' },
      { name: 'Alert', description: '알림 메시지' },
      { name: 'Banner', description: '배너 알림' },
      { name: 'InlineAlert', description: '인라인 알림' },
      { name: 'Toast', description: '토스트 알림' },
      { name: 'EmptyState', description: '빈 상태 표시' },
      { name: 'NoResults', description: '결과 없음 상태' },
      { name: 'NoData', description: '데이터 없음 상태' },
      { name: 'ErrorState', description: '에러 상태' },
      { name: 'LoadingState', description: '로딩 상태' },
      { name: 'Skeleton', description: '로딩 스켈레톤' },
      { name: 'Tooltip', description: '툴팁' },
    ],
  },
  layout: {
    title: 'Layout',
    description: '레이아웃 및 구조화를 위한 컴포넌트입니다.',
    icon: <LayoutGrid size={32} />,
    components: [
      { name: 'Card', description: '카드 컨테이너' },
      { name: 'StatsCard', description: '통계 카드' },
      { name: 'StatsGrid', description: '통계 그리드' },
      { name: 'BreakdownItem', description: '분해 항목' },
      { name: 'Section', description: '섹션 컨테이너' },
      { name: 'SectionHeader', description: '섹션 헤더' },
      { name: 'CollapsibleSection', description: '접을 수 있는 섹션' },
      { name: 'GroupPanel', description: '그룹 패널' },
      { name: 'Divider', description: '구분선' },
      { name: 'Accordion', description: '아코디언/확장 패널' },
      { name: 'InfoRow', description: '정보 행 표시' },
      { name: 'PanelHeader', description: '패널 헤더' },
      { name: 'ActionCard', description: '액션 카드' },
    ],
  },
  'data-display': {
    title: 'Data Display',
    description: '데이터를 표시하는 컴포넌트입니다.',
    icon: <Table2 size={32} />,
    components: [
      { name: 'Table', description: '데이터 테이블' },
      { name: 'TableHeader', description: '테이블 헤더' },
      { name: 'StatusBadge', description: '상태 배지' },
      { name: 'Chip', description: '태그/칩' },
      { name: 'ChipGroup', description: '칩 그룹' },
      { name: 'Label', description: '라벨' },
      { name: 'YamlViewer', description: 'YAML 뷰어' },
      { name: 'JsonViewer', description: 'JSON 뷰어' },
      { name: 'DiffViewer', description: 'Diff 뷰어' },
      { name: 'SearchBar', description: '검색 바' },
      { name: 'LineageGraph', description: '계보 그래프' },
      { name: 'StatCard', description: '통계 카드' },
    ],
  },
  navigation: {
    title: 'Navigation',
    description: '네비게이션 관련 컴포넌트입니다.',
    icon: <Blocks size={32} />,
    components: [
      { name: 'Tabs', description: '탭 네비게이션' },
      { name: 'Pagination', description: '페이지네이션' },
    ],
  },
}

// ============================================================================
// Component Preview Card
// ============================================================================

interface ComponentCardProps {
  category: string
  name: string
  description: string
}

function ComponentCard({ category, name, description }: ComponentCardProps) {
  const preview = COMPONENT_PREVIEWS[name]

  return (
    <Link
      to={`/design-system/components/${category}/${name.toLowerCase()}`}
      className="ds-component-card"
    >
      {/* Preview Area */}
      <div className="ds-component-card-preview">
        {preview || (
          <div className="ds-component-card-placeholder">
            <Construction size={24} />
          </div>
        )}
      </div>

      {/* Info Area */}
      <div className="ds-component-card-info">
        <div className="ds-component-card-header">
          <span className="ds-component-card-name">{name}</span>
          <ChevronRight size={16} className="ds-component-card-arrow" />
        </div>
        <p className="ds-component-card-description">{description}</p>
      </div>
    </Link>
  )
}

// ============================================================================
// Main Component
// ============================================================================

export function ComponentCategory() {
  const { category } = useParams<{ category: string }>()
  const categoryInfo = category ? CATEGORIES[category] : null

  if (!categoryInfo) {
    return (
      <div className="ds-placeholder">
        <Construction size={48} className="ds-placeholder-icon" />
        <h2 className="ds-placeholder-title">카테고리를 찾을 수 없습니다</h2>
        <p className="ds-placeholder-description">
          좌측 메뉴에서 원하는 컴포넌트 카테고리를 선택해주세요.
        </p>
      </div>
    )
  }

  return (
    <div className="ds-section">
      <header className="ds-section-header">
        <div style={{ marginBottom: '0.5rem' }}>
          <h1 className="ds-section-title" style={{ margin: 0 }}>{categoryInfo.title}</h1>
        </div>
        <p className="ds-section-description">{categoryInfo.description}</p>
      </header>

      {/* Grid Layout */}
      <div className="ds-component-grid">
        {categoryInfo.components.map((component) => (
          <ComponentCard
            key={component.name}
            category={category!}
            name={component.name}
            description={component.description}
          />
        ))}
      </div>
    </div>
  )
}
