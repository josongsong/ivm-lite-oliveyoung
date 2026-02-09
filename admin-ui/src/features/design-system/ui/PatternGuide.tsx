/**
 * Pattern Guide - 패턴 가이드 페이지
 *
 * 재사용 가능한 UI 패턴과 베스트 프랙티스를 제공합니다.
 * SOTA급 DX/UX로 각 패턴의 상세 가이드를 제공합니다.
 */

import { Link, useParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import {
  ArrowRight,
  AlertCircle,
  Loader2,
  FileCode,
  Table2,
  Search,
  MousePointer2,
  Grid3x3,
  BarChart3,
  Database,
  Filter,
  Copy,
  Check,
  Play,
  Eye,
  Trash2,
  Clock,
  Inbox,
  Layers,
  Plus,
  RefreshCw,
  Wand2,
  Code2,
  Upload,
  FileJson,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Activity,
  Server,
  Info,
  Type,
  Navigation2,
  LayoutGrid,
  Zap,
} from 'lucide-react'
import {
  Button,
  Card,
  Input,
  Label,
  Select,
  TextArea,
  Alert,
  Loading,
  Skeleton,
  EmptyState,
  Table,
  StatusBadge,
  Chip,
  IconButton,
  Pagination,
  Tabs,
  TabsList,
  TabsTrigger,
  TabsContent,
  PageHeader,
  Accordion,
  AccordionItem,
  AccordionTrigger,
  AccordionContent,
} from '@/shared/ui'
import { JsonViewer } from '@/shared/ui/recipes'
import { cloneElement, isValidElement, useState } from 'react'
import './PatternGuide.css'

// ============================================================================
// Pattern Data
// ============================================================================

function normalizeLucide(
  node: React.ReactNode,
  opts: { size: number; tone?: 'default' | 'muted'; className?: string }
): React.ReactNode {
  if (!isValidElement(node)) return node

  // Lucide 아이콘(ReactElement) 기준으로만 정규화합니다.
  // - strokeWidth/absoluteStrokeWidth: 아이콘 톤(세련됨) 통일
  // - className: 전역 아이콘 시스템(.ui-icon) 연결
  const props = node.props as {
    className?: string
    size?: number
    strokeWidth?: number
    absoluteStrokeWidth?: boolean
  }

  const toneClass = opts.tone === 'muted' ? 'ui-icon--muted' : ''
  const nextClassName = [props.className, 'ui-icon', toneClass, opts.className]
    .filter(Boolean)
    .join(' ')

  return cloneElement(node as React.ReactElement<any>, {
    // 디자인 시스템에서는 크기/두께를 강제로 통일해서 “정돈된” 인상을 만듭니다.
    size: opts.size,
    strokeWidth: props.strokeWidth ?? 1.6,
    absoluteStrokeWidth: props.absoluteStrokeWidth ?? true,
    className: nextClassName,
  } as any)
}

interface PatternExample {
  title: string
  description: string
  code: string
  preview: React.ReactNode
}

interface PatternInfo {
  title: string
  description: string
  icon: React.ReactNode
  preview: React.ReactNode
  color: string
  examples: PatternExample[]
  bestPractices: string[]
}

const PATTERNS: Record<string, PatternInfo> = {
  forms: {
    title: 'Form Patterns',
    description: '폼 레이아웃, 유효성 검사, 에러 표시 등 폼 관련 패턴입니다.',
    icon: <FileCode size={32} />,
    color: 'var(--accent-purple)',
    preview: (
      <div className="pattern-preview pattern-preview-form">
        <div className="pattern-preview-field">
          <div className="pattern-preview-label" />
          <div className="pattern-preview-input" />
        </div>
        <div className="pattern-preview-field">
          <div className="pattern-preview-label short" />
          <div className="pattern-preview-input" />
        </div>
        <div className="pattern-preview-button" />
      </div>
    ),
    examples: [
      {
        title: '기본 폼 레이아웃',
        description: 'Label과 Input을 함께 사용하는 기본 패턴',
        code: `import { Input, Label } from '@/shared/ui'

<Label required>이름</Label>
<Input
  placeholder="이름을 입력하세요"
  value={name}
  onChange={(e) => setName(e.target.value)}
/>`,
        preview: (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <Label required>이름</Label>
            <Input placeholder="이름을 입력하세요" />
          </div>
        ),
      },
      {
        title: '에러 상태 표시',
        description: '유효성 검사 실패 시 에러 메시지 표시',
        code: `<Label required>이메일</Label>
<Input
  placeholder="email@example.com"
  error={errors.email}
/>
{errors.email && (
  <Alert variant="error" size="sm">
    {errors.email}
  </Alert>
)}`,
        preview: (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <Label required>이메일</Label>
            <Input placeholder="email@example.com" error errorMessage="올바른 이메일 형식이 아닙니다" />
            <Alert variant="error" size="sm">올바른 이메일 형식이 아닙니다</Alert>
          </div>
        ),
      },
      {
        title: 'Select와 TextArea 조합',
        description: '드롭다운과 멀티라인 입력 필드',
        code: `<Label>카테고리</Label>
<Select
  value={category}
  onChange={setCategory}
  options={[
    { value: 'option1', label: '옵션 1' },
    { value: 'option2', label: '옵션 2' },
  ]}
/>

<Label>설명</Label>
<TextArea
  placeholder="상세 설명을 입력하세요"
  rows={4}
/>`,
        preview: (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
              <Label>카테고리</Label>
              <Select
                value="option1"
                onChange={() => {}}
                options={[
                  { value: 'option1', label: '옵션 1' },
                  { value: 'option2', label: '옵션 2' },
                ]}
              />
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
              <Label>설명</Label>
              <TextArea placeholder="상세 설명을 입력하세요" rows={4} />
            </div>
          </div>
        ),
      },
    ],
    bestPractices: [
      'Label은 항상 Input과 함께 사용하여 접근성을 향상시킵니다',
      '필수 필드는 Label에 required prop을 추가합니다',
      '에러 메시지는 Input 아래에 Alert 컴포넌트로 표시합니다',
      '긴 폼은 섹션으로 나누어 사용자 경험을 개선합니다',
      '제출 버튼은 로딩 상태를 표시하여 사용자에게 피드백을 제공합니다',
    ],
  },
  errors: {
    title: 'Error Handling',
    description: '에러 상태 표시, 에러 메시지, 복구 가이드 등 에러 처리 패턴입니다.',
    icon: <AlertCircle size={32} />,
    color: 'var(--accent-red)',
    preview: (
      <div className="pattern-preview pattern-preview-error">
        <div className="pattern-preview-error-icon">
          <AlertCircle size={16} />
        </div>
        <div className="pattern-preview-error-content">
          <div className="pattern-preview-error-title" />
          <div className="pattern-preview-error-message" />
        </div>
      </div>
    ),
    examples: [
      {
        title: '인라인 에러 메시지',
        description: 'Input 필드 바로 아래에 표시되는 에러',
        code: `<Input
  placeholder="이메일을 입력하세요"
  error="올바른 이메일 형식이 아닙니다"
/>`,
        preview: (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <Input placeholder="이메일을 입력하세요" error errorMessage="올바른 이메일 형식이 아닙니다" />
          </div>
        ),
      },
      {
        title: 'Alert 에러 메시지',
        description: '중요한 에러를 Alert 컴포넌트로 표시',
        code: `<Alert variant="error" title="저장 실패">
  데이터를 저장하는 중 오류가 발생했습니다.
  잠시 후 다시 시도해주세요.
</Alert>`,
        preview: (
          <Alert variant="error" title="저장 실패">
            데이터를 저장하는 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.
          </Alert>
        ),
      },
      {
        title: 'EmptyState 에러',
        description: '데이터를 불러올 수 없을 때 표시',
        code: `<EmptyState
  icon={<AlertCircle />}
  title="데이터를 불러올 수 없습니다"
  description="네트워크 연결을 확인하고 다시 시도해주세요"
  action={<Button onClick={retry}>다시 시도</Button>}
/>`,
        preview: (
          <EmptyState
            icon={<AlertCircle size={48} />}
            title="데이터를 불러올 수 없습니다"
            description="네트워크 연결을 확인하고 다시 시도해주세요"
            action={<Button onClick={() => {}}>다시 시도</Button>}
          />
        ),
      },
    ],
    bestPractices: [
      '에러 메시지는 명확하고 실행 가능한 내용을 포함합니다',
      '사용자가 에러를 해결할 수 있는 방법을 제시합니다',
      '중요한 에러는 Alert로, 경미한 에러는 인라인으로 표시합니다',
      '에러 발생 시 사용자가 이전 작업을 잃지 않도록 주의합니다',
      '에러 메시지는 기술적인 용어보다 사용자 친화적인 언어를 사용합니다',
    ],
  },
  loading: {
    title: 'Loading States',
    description: '로딩 스켈레톤, 스피너, 프로그레스 표시 등 로딩 상태 패턴입니다.',
    icon: <Loader2 size={32} />,
    color: 'var(--accent-cyan)',
    preview: (
      <div className="pattern-preview pattern-preview-loading">
        <div className="pattern-preview-loading-spinner">
          <Loader2 size={20} className="spinning" />
        </div>
        <div className="pattern-preview-loading-content">
          <div className="pattern-preview-skeleton" />
          <div className="pattern-preview-skeleton short" />
          <div className="pattern-preview-skeleton" />
        </div>
      </div>
    ),
    examples: [
      {
        title: '로딩 스피너',
        description: '간단한 로딩 인디케이터',
        code: `import { Loading } from '@/shared/ui'

<Loading size="md" />`,
        preview: <Loading size="md" />,
      },
      {
        title: '스켈레톤 로딩',
        description: '콘텐츠 구조를 미리 보여주는 스켈레톤',
        code: `import { Skeleton } from '@/shared/ui'

<Skeleton width="100%" height={20} />
<Skeleton width="80%" height={20} />
<Skeleton width="60%" height={20} />`,
        preview: (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <Skeleton width="100%" height={20} />
            <Skeleton width="80%" height={20} />
            <Skeleton width="60%" height={20} />
          </div>
        ),
      },
      {
        title: '버튼 로딩 상태',
        description: '버튼 클릭 시 로딩 상태 표시',
        code: `<Button loading={isLoading} onClick={handleSubmit}>
  저장하기
</Button>`,
        preview: (
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <Button loading={false}>저장하기</Button>
            <Button loading={true}>저장 중...</Button>
          </div>
        ),
      },
    ],
    bestPractices: [
      '로딩 시간이 짧으면 스피너, 길면 스켈레톤을 사용합니다',
      '스켈레톤은 실제 콘텐츠 구조와 유사하게 만듭니다',
      '버튼 로딩 상태는 사용자가 액션을 취했음을 명확히 보여줍니다',
      '로딩 시간이 3초 이상이면 진행률 표시를 고려합니다',
      '로딩 중에도 사용자가 취소할 수 있는 옵션을 제공합니다',
    ],
  },
  tables: {
    title: 'Table Patterns',
    description: '테이블 레이아웃, 헤더, 액션, 페이지네이션 등 테이블 관련 패턴입니다.',
    icon: <Table2 size={32} />,
    color: 'var(--accent-cyan)',
    preview: (
      <div className="pattern-preview pattern-preview-table">
        <div className="pattern-preview-table-header" />
        <div className="pattern-preview-table-row" />
        <div className="pattern-preview-table-row" />
      </div>
    ),
    examples: [
      {
        title: '기본 테이블',
        description: 'Table 컴포넌트를 사용한 기본 패턴',
        code: `import { Table } from '@/shared/ui'

<Table
  columns={[
    { key: 'name', label: '이름' },
    { key: 'status', label: '상태' },
  ]}
  data={[
    { name: 'Item 1', status: 'Active' },
    { name: 'Item 2', status: 'Pending' },
  ]}
/>`,
        preview: (
          <Table
            columns={[
              { key: 'name', header: '이름' },
              { key: 'status', header: '상태' },
            ]}
            data={[
              { name: 'Item 1', status: 'Active' },
              { name: 'Item 2', status: 'Pending' },
            ]}
            keyExtractor={(item) => item.name}
          />
        ),
      },
      {
        title: '테이블 헤더와 액션',
        description: '제목, 카운트, 액션 버튼이 있는 테이블',
        code: `<div className="table-container">
  <div className="table-header">
    <div className="table-title">
      <Database size={18} />
      <h3>RawData</h3>
      <span className="table-count">42</span>
    </div>
    <div className="table-actions">
      <Button size="sm">추가</Button>
    </div>
  </div>
  <Table ... />
</div>`,
        preview: (
          <Card style={{ padding: '1rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Database size={18} />
                <h3 style={{ margin: 0 }}>RawData</h3>
                <Chip>42</Chip>
              </div>
              <Button size="sm">추가</Button>
            </div>
            <Table
              columns={[
                { key: 'id', header: 'ID' },
                { key: 'name', header: '이름' },
              ]}
              data={[
                { id: '1', name: 'Item 1' },
                { id: '2', name: 'Item 2' },
              ]}
              keyExtractor={(item) => item.id}
            />
          </Card>
        ),
      },
      {
        title: '테이블 페이지네이션',
        description: '페이지네이션과 함께 사용하는 테이블',
        code: `<Table ... />
<Pagination
  page={currentPage}
  totalPages={totalPages}
  totalItems={totalItems}
  pageSize={pageSize}
  onPageChange={setCurrentPage}
/>`,
        preview: (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <Table
              columns={[{ key: 'id', header: 'ID' }]}
              data={[{ id: '1' }, { id: '2' }]}
              keyExtractor={(item) => item.id}
            />
            <Pagination
              page={2}
              totalPages={5}
              totalItems={50}
              pageSize={10}
              onPageChange={() => {}}
            />
          </div>
        ),
      },
    ],
    bestPractices: [
      '테이블 헤더에는 제목, 카운트, 액션 버튼을 포함합니다',
      '긴 데이터는 truncate 클래스를 사용하여 잘라냅니다',
      '정렬 가능한 컬럼은 아이콘으로 표시합니다',
      '빈 상태와 에러 상태를 명확히 구분합니다',
      '페이지네이션은 항상 테이블 하단에 배치합니다',
    ],
  },
  search: {
    title: 'Search & Filter',
    description: '검색 입력, 필터 그룹, 고급 필터 등 검색 및 필터링 패턴입니다.',
    icon: <Search size={32} />,
    color: 'var(--accent-purple)',
    preview: (
      <div className="pattern-preview pattern-preview-search">
        <div className="pattern-preview-search-box" />
      </div>
    ),
    examples: [
      {
        title: '기본 검색 입력',
        description: '아이콘이 있는 검색 필드',
        code: `<Input
  placeholder="검색..."
  leftIcon={<Search size={16} />}
  value={query}
  onChange={(e) => setQuery(e.target.value)}
/>`,
        preview: (
          <Input placeholder="검색..." leftIcon={<Search size={16} />} />
        ),
      },
      {
        title: '필터 그룹',
        description: '여러 필터를 그룹으로 표시',
        code: `<div className="traces-filters">
  <Select
    value={statusFilter}
    onChange={setStatusFilter}
    options={[
      { value: 'all', label: '전체' },
      { value: 'active', label: '활성' },
    ]}
  />
  <Select
    value={typeFilter}
    onChange={setTypeFilter}
    options={[...]}
  />
</div>`,
        preview: (
          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            <Select
              value="all"
              onChange={() => {}}
              options={[
                { value: 'all', label: '전체' },
                { value: 'active', label: '활성' },
              ]}
            />
            <Select
              value="type1"
              onChange={() => {}}
              options={[
                { value: 'type1', label: '타입 1' },
                { value: 'type2', label: '타입 2' },
              ]}
            />
          </div>
        ),
      },
      {
        title: '검색 + 필터 조합',
        description: '검색과 필터를 함께 사용',
        code: `<div style={{ display: 'flex', gap: '0.5rem' }}>
  <Input
    placeholder="Entity ID로 필터..."
    leftIcon={<Search size={14} />}
    style={{ flex: 1 }}
  />
  <Select ... />
  <Button variant="ghost" size="sm">
    초기화
  </Button>
</div>`,
        preview: (
          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
            <Input
              placeholder="Entity ID로 필터..."
              leftIcon={<Search size={14} />}
              style={{ flex: 1 }}
            />
            <Select
              value="all"
              onChange={() => {}}
              options={[{ value: 'all', label: '전체' }]}
            />
            <Button variant="ghost" size="sm">초기화</Button>
          </div>
        ),
      },
    ],
    bestPractices: [
      '검색 입력은 항상 아이콘을 포함하여 시각적으로 명확하게 합니다',
      '필터는 관련된 것끼리 그룹화합니다',
      '필터 초기화 버튼을 제공하여 사용자 경험을 개선합니다',
      '검색은 debounce를 적용하여 성능을 최적화합니다',
      '필터 상태는 URL 쿼리 파라미터로 관리하여 공유 가능하게 합니다',
    ],
  },
  actions: {
    title: 'Action Buttons',
    description: '액션 버튼 그룹, 아이콘 버튼, 프로세스 버튼 등 액션 관련 패턴입니다.',
    icon: <MousePointer2 size={32} />,
    color: 'var(--accent-purple)',
    preview: (
      <div className="pattern-preview pattern-preview-actions">
        <div className="pattern-preview-button" />
        <div className="pattern-preview-button" />
      </div>
    ),
    examples: [
      {
        title: '액션 버튼 그룹',
        description: '여러 액션을 그룹으로 표시',
        code: `<div className="action-buttons">
  <IconButton icon={<Play />} aria-label="실행" />
  <IconButton icon={<Eye />} aria-label="보기" />
  <IconButton icon={<Trash2 />} variant="danger" aria-label="삭제" />
</div>`,
        preview: (
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <IconButton icon={Play} aria-label="실행" />
            <IconButton icon={Eye} aria-label="보기" />
            <IconButton icon={Trash2} variant="danger" aria-label="삭제" />
          </div>
        ),
      },
      {
        title: '프로세스 버튼',
        description: '특별한 스타일의 실행 버튼',
        code: `<button className="btn-icon btn-process">
  <Play size={16} />
</button>`,
        preview: (
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <Button variant="primary" size="sm" icon={<Play size={16} />}>
              실행
            </Button>
            <Button variant="secondary" size="sm" icon={<Eye size={16} />}>
              보기
            </Button>
          </div>
        ),
      },
      {
        title: '버튼과 드롭다운 조합',
        description: '주요 액션과 보조 액션을 구분',
        code: `<div style={{ display: 'flex', gap: '0.5rem' }}>
  <Button variant="primary">주요 액션</Button>
  <Button variant="secondary">보조 액션</Button>
  <IconButton icon={<MoreVertical />} />
</div>`,
        preview: (
          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
            <Button variant="primary" size="sm">주요 액션</Button>
            <Button variant="secondary" size="sm">보조 액션</Button>
            <IconButton icon={Filter} aria-label="더보기" />
          </div>
        ),
      },
    ],
    bestPractices: [
      '주요 액션은 primary variant를 사용하여 강조합니다',
      '위험한 액션(삭제 등)은 danger variant를 사용합니다',
      '아이콘 버튼은 항상 aria-label을 제공합니다',
      '액션 버튼은 관련된 것끼리 그룹화합니다',
      '로딩 상태를 표시하여 사용자에게 피드백을 제공합니다',
    ],
  },
  cards: {
    title: 'Card & Panel',
    description: '카드 레이아웃, 패널 헤더, 액션 카드 등 카드 및 패널 관련 패턴입니다.',
    icon: <Grid3x3 size={32} />,
    color: 'var(--accent-purple)',
    preview: (
      <div className="pattern-preview pattern-preview-card">
        <div className="pattern-preview-card-header" />
        <div className="pattern-preview-card-content" />
      </div>
    ),
    examples: [
      {
        title: '기본 카드',
        description: 'Card 컴포넌트를 사용한 기본 패턴',
        code: `import { Card } from '@/shared/ui'

<Card>
  <h3>카드 제목</h3>
  <p>카드 내용입니다.</p>
</Card>`,
        preview: (
          <Card>
            <h3 style={{ marginTop: 0 }}>카드 제목</h3>
            <p style={{ marginBottom: 0 }}>카드 내용입니다.</p>
          </Card>
        ),
      },
      {
        title: '패널 헤더',
        description: '아이콘, 제목, 링크가 있는 패널 헤더',
        code: `<div className="panel-header">
  <Inbox size={18} />
  <h3>Outbox Queue</h3>
  <Link to="/outbox" className="view-all">
    상세보기 <ArrowRight size={14} />
  </Link>
</div>`,
        preview: (
          <Card>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Inbox size={18} />
                <h3 style={{ margin: 0 }}>Outbox Queue</h3>
              </div>
              <Link to="#" style={{ display: 'flex', alignItems: 'center', gap: '0.25rem', color: 'var(--accent-cyan)', textDecoration: 'none' }}>
                상세보기 <ArrowRight size={14} />
              </Link>
            </div>
            <p style={{ margin: 0, color: 'var(--text-secondary)' }}>패널 내용</p>
          </Card>
        ),
      },
      {
        title: '액션 카드',
        description: '클릭 가능한 액션 카드',
        code: `<div className="action-card">
  <Icon size={24} />
  <h4>액션 제목</h4>
  <p>액션 설명</p>
</div>`,
        preview: (
          <Card style={{ cursor: 'pointer', textAlign: 'center', padding: '1.5rem' }}>
            <Database size={24} style={{ marginBottom: '0.5rem', color: 'var(--accent-cyan)' }} />
            <h4 style={{ margin: '0.5rem 0' }}>액션 제목</h4>
            <p style={{ margin: 0, color: 'var(--text-secondary)', fontSize: '0.875rem' }}>액션 설명</p>
          </Card>
        ),
      },
    ],
    bestPractices: [
      '카드는 관련된 콘텐츠를 논리적으로 그룹화합니다',
      '패널 헤더는 아이콘, 제목, 액션을 포함합니다',
      '클릭 가능한 카드는 hover 상태를 명확히 표시합니다',
      '카드 간 일관된 간격을 유지합니다',
      '중요한 정보는 카드 상단에 배치합니다',
    ],
  },
  stats: {
    title: 'Stats & Metrics',
    description: '통계 카드, 메트릭 표시, 진행바 등 통계 및 메트릭 관련 패턴입니다.',
    icon: <BarChart3 size={32} />,
    color: 'var(--accent-cyan)',
    preview: (
      <div className="pattern-preview pattern-preview-stats">
        <div className="pattern-preview-stat" />
        <div className="pattern-preview-stat" />
      </div>
    ),
    examples: [
      {
        title: '기본 통계 카드',
        description: '값과 라벨이 있는 통계 카드',
        code: `import { Card } from '@/shared/ui'

<Card>
  <div className="stat-info">
    <span className="stat-value">42</span>
    <span className="stat-label">Pending</span>
  </div>
</Card>`,
        preview: (
          <Card style={{ padding: '1rem' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
              <span style={{ fontSize: '2rem', fontWeight: 700, fontFamily: 'var(--font-mono)' }}>42</span>
              <span style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>Pending</span>
            </div>
          </Card>
        ),
      },
      {
        title: '아이콘이 있는 통계',
        description: '아이콘과 함께 표시되는 통계',
        code: `<div className="outbox-stat pending">
  <Clock size={20} />
  <div className="stat-info">
    <span className="stat-value">42</span>
    <span className="stat-label">Pending</span>
  </div>
  <div className="stat-bar" style={{ '--progress': '75%' }} />
</div>`,
        preview: (
          <Card style={{ padding: '1rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              <Clock size={20} style={{ color: 'var(--accent-orange)' }} />
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
                <span style={{ fontSize: '1.5rem', fontWeight: 700, fontFamily: 'var(--font-mono)' }}>42</span>
                <span style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>Pending</span>
              </div>
              <div style={{ width: '4px', height: '100%', background: 'var(--accent-orange)', borderRadius: '2px' }} />
            </div>
          </Card>
        ),
      },
      {
        title: 'StatusBadge와 함께',
        description: '상태 배지와 함께 표시되는 통계',
        code: `<div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
  <StatusBadge status="healthy" />
  <span className="stat-value">98%</span>
  <span className="stat-label">Uptime</span>
</div>`,
        preview: (
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <StatusBadge status="healthy" />
            <span style={{ fontSize: '1.25rem', fontWeight: 700, fontFamily: 'var(--font-mono)' }}>98%</span>
            <span style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>Uptime</span>
          </div>
        ),
      },
    ],
    bestPractices: [
      '통계 값은 monospace 폰트를 사용하여 가독성을 향상시킵니다',
      '큰 숫자는 천 단위 구분자를 사용합니다',
      '비율이나 퍼센트는 진행바로 시각화합니다',
      '통계 카드는 관련된 것끼리 그룹화합니다',
      '변화 추이를 보여주는 경우 화살표나 색상으로 표시합니다',
    ],
  },
  modals: {
    title: 'Modal Patterns',
    description: '모달 다이얼로그, 폼 모달, 확인 다이얼로그 등 모달 관련 패턴입니다.',
    icon: <Grid3x3 size={32} />,
    color: 'var(--accent-purple)',
    preview: (
      <div className="pattern-preview pattern-preview-modal">
        <div className="pattern-preview-modal-header" />
        <div className="pattern-preview-modal-content" />
        <div className="pattern-preview-modal-footer" />
      </div>
    ),
    examples: [
      {
        title: '기본 모달',
        description: 'Modal 컴포넌트를 사용한 기본 패턴',
        code: `import { Modal, Button } from '@/shared/ui'

const [isOpen, setIsOpen] = useState(false)

<Modal
  isOpen={isOpen}
  onClose={() => setIsOpen(false)}
  title="Confirm Action"
  footer={
    <>
      <Button variant="ghost" onClick={() => setIsOpen(false)}>Cancel</Button>
      <Button variant="primary" onClick={handleConfirm}>Confirm</Button>
    </>
  }
>
  Are you sure you want to proceed?
</Modal>`,
        preview: (
          <Card style={{ padding: '1rem' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <h3 style={{ margin: 0, fontSize: '1.25rem' }}>Confirm Action</h3>
              <p style={{ margin: 0, color: 'var(--text-secondary)' }}>Are you sure you want to proceed?</p>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
                <Button variant="ghost" size="sm">Cancel</Button>
                <Button variant="primary" size="sm">Confirm</Button>
              </div>
            </div>
          </Card>
        ),
      },
      {
        title: '폼이 있는 모달',
        description: '모달 안에 폼을 배치하는 패턴',
        code: `import { Modal, Form, FormGroup, FormInput, Button } from '@/shared/ui'

<Modal
  isOpen={isOpen}
  onClose={() => setIsOpen(false)}
  title="New Job"
  footer={
    <>
      <Button variant="ghost" onClick={() => setIsOpen(false)}>Cancel</Button>
      <Button variant="primary" type="submit" form="job-form">Create</Button>
    </>
  }
>
  <Form id="job-form" onSubmit={handleSubmit}>
    <FormGroup label="Job Name" htmlFor="name">
      <FormInput
        id="name"
        name="name"
        placeholder="e.g., Product Full Reprocess"
        required
      />
    </FormGroup>
  </Form>
</Modal>`,
        preview: (
          <Card style={{ padding: '1rem' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <h3 style={{ margin: 0, fontSize: '1.25rem' }}>New Job</h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                <Label>Job Name</Label>
                <Input placeholder="e.g., Product Full Reprocess" />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
                <Button variant="ghost" size="sm">Cancel</Button>
                <Button variant="primary" size="sm">Create</Button>
              </div>
            </div>
          </Card>
        ),
      },
      {
        title: '웹훅 폼 모달',
        description: '여러 필드와 체크박스가 있는 복잡한 폼 모달',
        code: `import { Modal, Form, FormGroup, FormInput, Select, Button } from '@/shared/ui'

<Modal
  isOpen={isOpen}
  onClose={() => setIsOpen(false)}
  title={webhook ? '웹훅 수정' : '웹훅 추가'}
  size="lg"
  footer={
    <>
      <Button variant="ghost" onClick={() => setIsOpen(false)}>취소</Button>
      <Button variant="primary" type="submit" form="webhook-form">
        {webhook ? '수정' : '생성'}
      </Button>
    </>
  }
>
  <Form id="webhook-form" onSubmit={handleSubmit}>
    <FormGroup label="이름" htmlFor="name">
      <FormInput id="name" name="name" required />
    </FormGroup>
    <FormGroup label="URL" htmlFor="url">
      <FormInput id="url" type="url" name="url" required />
    </FormGroup>
    <FormGroup label="이벤트">
      <div className="events-selector">
        {/* 체크박스 목록 */}
      </div>
    </FormGroup>
  </Form>
</Modal>`,
        preview: (
          <Card style={{ padding: '1rem' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <h3 style={{ margin: 0, fontSize: '1.25rem' }}>웹훅 추가</h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                  <Label>이름</Label>
                  <Input placeholder="Webhook Name" />
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                  <Label>URL</Label>
                  <Input type="url" placeholder="https://example.com/webhook" />
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                  <Label>이벤트</Label>
                  <div style={{ padding: '0.5rem', border: '1px solid var(--border-color)', borderRadius: 'var(--radius-md)', maxHeight: '150px', overflowY: 'auto' }}>
                    <div style={{ fontSize: '0.875rem', padding: '0.25rem' }}>✓ Event 1</div>
                    <div style={{ fontSize: '0.875rem', padding: '0.25rem' }}>Event 2</div>
                  </div>
                </div>
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
                <Button variant="ghost" size="sm">취소</Button>
                <Button variant="primary" size="sm">생성</Button>
              </div>
            </div>
          </Card>
        ),
      },
    ],
    bestPractices: [
      '모달은 중요한 작업이나 확인이 필요할 때만 사용합니다',
      '폼이 있는 모달은 footer에 액션 버튼을 배치합니다',
      '취소 버튼은 항상 제공하여 사용자가 쉽게 닫을 수 있게 합니다',
      '긴 폼은 스크롤 가능하도록 max-height를 설정합니다',
      '모달 제목은 명확하고 간결하게 작성합니다',
    ],
  },
  tabs: {
    title: 'Tabs Patterns',
    description: '탭 네비게이션, 탭과 콘텐츠, 필터 탭 등 탭 관련 패턴입니다.',
    icon: <Layers size={32} />,
    color: 'var(--accent-purple)',
    preview: (
      <div className="pattern-preview pattern-preview-tabs">
        <div className="pattern-preview-tab active" />
        <div className="pattern-preview-tab" />
        <div className="pattern-preview-tab" />
      </div>
    ),
    examples: [
      {
        title: '기본 탭',
        description: 'Tabs 컴포넌트를 사용한 기본 패턴',
        code: `import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/shared/ui'

const [activeTab, setActiveTab] = useState('tab1')

<Tabs value={activeTab} onValueChange={setActiveTab}>
  <TabsList>
    <TabsTrigger value="tab1">Tab 1</TabsTrigger>
    <TabsTrigger value="tab2">Tab 2</TabsTrigger>
  </TabsList>
  <TabsContent value="tab1">Content 1</TabsContent>
  <TabsContent value="tab2">Content 2</TabsContent>
</Tabs>`,
        preview: (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <Tabs value="tab1" onValueChange={() => {}}>
              <TabsList>
                <TabsTrigger value="tab1">Tab 1</TabsTrigger>
                <TabsTrigger value="tab2">Tab 2</TabsTrigger>
              </TabsList>
              <TabsContent value="tab1">
                <div style={{ padding: '1rem', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-md)' }}>Content 1</div>
              </TabsContent>
              <TabsContent value="tab2">
                <div style={{ padding: '1rem', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-md)' }}>Content 2</div>
              </TabsContent>
            </Tabs>
          </div>
        ),
      },
      {
        title: '아이콘과 배지가 있는 탭',
        description: '아이콘과 카운트 배지가 있는 탭 (Outbox 패턴)',
        code: `import { Tabs, TabsList, TabsTrigger } from '@/shared/ui'
import { Inbox, Clock, AlertCircle } from 'lucide-react'

const tabs = [
  { key: 'all', label: 'All', icon: Inbox, count: 42 },
  { key: 'pending', label: 'Pending', icon: Clock, count: 12 },
  { key: 'failed', label: 'Failed', icon: AlertCircle, count: 3 },
]

<Tabs value={activeTab} onValueChange={setActiveTab}>
  <TabsList>
    {tabs.map((tab) => (
      <TabsTrigger
        key={tab.key}
        value={tab.key}
        icon={<tab.icon size={16} />}
        badge={tab.count}
      >
        {tab.label}
      </TabsTrigger>
    ))}
  </TabsList>
</Tabs>`,
        preview: (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <Tabs value="all" onValueChange={() => {}}>
              <TabsList>
                <TabsTrigger value="all" icon={<Inbox size={16} />} badge={<Chip>42</Chip>}>
                  All
                </TabsTrigger>
                <TabsTrigger value="pending" icon={<Clock size={16} />} badge={<Chip>12</Chip>}>
                  Pending
                </TabsTrigger>
                <TabsTrigger value="failed" icon={<AlertCircle size={16} />} badge={<Chip>3</Chip>}>
                  Failed
                </TabsTrigger>
              </TabsList>
            </Tabs>
          </div>
        ),
      },
      {
        title: '탭과 액션 버튼',
        description: '탭과 함께 액션 버튼이 있는 패턴',
        code: `import { Tabs, TabsList, TabsTrigger, Button } from '@/shared/ui'

<div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
  <Tabs value={activeTab} onValueChange={setActiveTab}>
    <TabsList>
      <TabsTrigger value="all">All</TabsTrigger>
      <TabsTrigger value="failed">Failed</TabsTrigger>
    </TabsList>
  </Tabs>
  {activeTab === 'failed' && (
    <Button size="sm" onClick={handleRetryAll}>
      Retry All
    </Button>
  )}
</div>`,
        preview: (
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '1rem' }}>
            <Tabs value="failed" onValueChange={() => {}}>
              <TabsList>
                <TabsTrigger value="all">All</TabsTrigger>
                <TabsTrigger value="failed">Failed</TabsTrigger>
              </TabsList>
            </Tabs>
            <Button size="sm">Retry All</Button>
          </div>
        ),
      },
    ],
    bestPractices: [
      '탭은 관련된 콘텐츠를 논리적으로 그룹화할 때 사용합니다',
      '탭 개수는 2-5개가 적절하며, 너무 많으면 다른 네비게이션 방식을 고려합니다',
      '활성 탭은 시각적으로 명확하게 구분합니다',
      '탭과 함께 액션 버튼이 있을 때는 관련된 탭에만 표시합니다',
      '탭 전환 시 애니메이션을 사용하여 사용자 경험을 개선합니다',
    ],
  },
  filters: {
    title: 'Filter Bar',
    description: '필터 바, 검색과 필터 조합, 서브 필터 바 등 필터링 관련 패턴입니다.',
    icon: <Filter size={32} />,
    color: 'var(--accent-cyan)',
    preview: (
      <div className="pattern-preview pattern-preview-filter">
        <div className="pattern-preview-filter-box" />
        <div className="pattern-preview-filter-item" />
        <div className="pattern-preview-filter-item" />
      </div>
    ),
    examples: [
      {
        title: '검색과 탭 필터',
        description: '검색 입력과 탭 필터가 함께 있는 패턴 (Contracts 패턴)',
        code: `import { Input, Search } from '@/shared/ui'

<div className="filter-bar">
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
    {tabs.map(({ key, label, icon: Icon }) => (
      <button
        key={key}
        className={\`kind-tab \${selectedKind === key ? 'active' : ''}\`}
        onClick={() => setSelectedKind(key)}
      >
        <Icon size={14} />
        <span>{label}</span>
      </button>
    ))}
  </div>
</div>`,
        preview: (
          <div style={{ display: 'flex', gap: '1rem', alignItems: 'center', padding: '0.75rem', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-md)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.5rem', background: 'var(--bg-tertiary)', borderRadius: 'var(--radius-md)', flex: 1 }}>
              <Search size={16} style={{ color: 'var(--text-muted)' }} />
              <input
                type="text"
                placeholder="Search contracts..."
                style={{ background: 'transparent', border: 'none', outline: 'none', flex: 1, color: 'var(--text-primary)' }}
              />
            </div>
            <div style={{ display: 'flex', gap: '0.25rem' }}>
              <button style={{ padding: '0.5rem 0.75rem', background: 'var(--accent-cyan)', color: 'white', border: 'none', borderRadius: 'var(--radius-md)', fontSize: '0.875rem' }}>전체</button>
              <button style={{ padding: '0.5rem 0.75rem', background: 'transparent', color: 'var(--text-secondary)', border: 'none', borderRadius: 'var(--radius-md)', fontSize: '0.875rem' }}>Schema</button>
            </div>
          </div>
        ),
      },
      {
        title: '서브 필터 바',
        description: '필터 아이템과 페이지 크기 선택이 있는 서브 필터 바 (DataExplorer 패턴)',
        code: `import { Filter, Layers } from 'lucide-react'

<div className="sub-filter-bar">
  <div className="filter-left">
    <div className="filter-item">
      <Filter size={14} />
      <span className="filter-label">Tenant:</span>
      <span className="filter-value">oliveyoung</span>
    </div>
    <div className="filter-item">
      <Layers size={14} />
      <span className="filter-label">Type:</span>
      <select className="slice-type-select" value={type} onChange={handleTypeChange}>
        <option value="all">All</option>
        <option value="product">Product (42)</option>
      </select>
    </div>
    <div className="filter-item">
      <span className="filter-label">Total:</span>
      <span className="filter-value accent">1,234</span>
    </div>
  </div>
  <div className="filter-right">
    <select className="page-size-select" value={pageSize} onChange={handlePageSizeChange}>
      <option value={10}>10개씩</option>
      <option value={20}>20개씩</option>
      <option value={50}>50개씩</option>
    </select>
  </div>
</div>`,
        preview: (
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.75rem', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-md)', gap: '1rem' }}>
            <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.875rem' }}>
                <Filter size={14} style={{ color: 'var(--text-muted)' }} />
                <span style={{ color: 'var(--text-secondary)' }}>Tenant:</span>
                <span style={{ color: 'var(--text-primary)', fontWeight: 500 }}>oliveyoung</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.875rem' }}>
                <span style={{ color: 'var(--text-secondary)' }}>Total:</span>
                <span style={{ color: 'var(--accent-cyan)', fontWeight: 600, fontFamily: 'var(--font-mono)' }}>1,234</span>
              </div>
            </div>
            <select style={{ padding: '0.5rem', background: 'var(--bg-tertiary)', border: '1px solid var(--border-color)', borderRadius: 'var(--radius-md)', color: 'var(--text-primary)', fontSize: '0.875rem' }}>
              <option>20개씩</option>
            </select>
          </div>
        ),
      },
      {
        title: '툴바 필터',
        description: '필터, 액션, 통계가 함께 있는 툴바 패턴 (Workflow 패턴)',
        code: `import { Filter, RefreshCw } from 'lucide-react'

<div className="workflow-toolbar">
  <div className="filter-group">
    <Filter size={14} />
    <select value={entityFilter} onChange={handleFilterChange}>
      <option value="">전체 엔티티</option>
      <option value="product">Product</option>
    </select>
  </div>
  <div className="action-group">
    <button onClick={refetch} disabled={isFetching}>
      <RefreshCw size={14} className={isFetching ? 'spinning' : ''} />
      새로고침
    </button>
  </div>
  <div className="stats-group">
    <span className="stat">42 nodes</span>
    <span className="stat">128 edges</span>
  </div>
</div>`,
        preview: (
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.75rem', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-md)', gap: '1rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Filter size={14} style={{ color: 'var(--text-muted)' }} />
              <select style={{ padding: '0.5rem', background: 'var(--bg-tertiary)', border: '1px solid var(--border-color)', borderRadius: 'var(--radius-md)', color: 'var(--text-primary)', fontSize: '0.875rem' }}>
                <option>전체 엔티티</option>
              </select>
            </div>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <Button variant="ghost" size="sm" icon={<RefreshCw size={14} />}>새로고침</Button>
            </div>
            <div style={{ display: 'flex', gap: '1rem', fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
              <span>42 nodes</span>
              <span>128 edges</span>
            </div>
          </div>
        ),
      },
    ],
    bestPractices: [
      '필터는 관련된 것끼리 그룹화하여 배치합니다',
      '검색과 필터는 함께 사용할 때 시각적으로 구분합니다',
      '필터 상태는 URL 쿼리 파라미터로 관리하여 공유 가능하게 합니다',
      '필터 초기화 버튼을 제공하여 사용자 경험을 개선합니다',
      '필터 변경 시 즉시 적용되도록 debounce를 적절히 사용합니다',
    ],
  },
  pageHeaders: {
    title: 'Page Header Patterns',
    description: '페이지 헤더, 통계 카드, 액션 버튼이 있는 헤더 등 페이지 헤더 관련 패턴입니다.',
    icon: <FileCode size={32} />,
    color: 'var(--accent-purple)',
    preview: (
      <div className="pattern-preview pattern-preview-header">
        <div className="pattern-preview-header-title" />
        <div className="pattern-preview-header-stats" />
      </div>
    ),
    examples: [
      {
        title: '기본 페이지 헤더',
        description: 'PageHeader 컴포넌트를 사용한 기본 패턴',
        code: `import { PageHeader } from '@/shared/ui'

<PageHeader
  title="Contracts"
  subtitle="스키마, 룰셋, 뷰 정의 등 시스템 계약을 관리합니다"
/>`,
        preview: (
          <PageHeader
            title="Contracts"
            subtitle="스키마, 룰셋, 뷰 정의 등 시스템 계약을 관리합니다"
          />
        ),
      },
      {
        title: '헤더와 통계 카드',
        description: '페이지 헤더 아래에 통계 카드가 있는 패턴 (Contracts 패턴)',
        code: `import { PageHeader, Card } from '@/shared/ui'

<PageHeader title="Contracts" subtitle="..." />

<div className="contract-stats">
  <div className="stat-overview">
    <div className="stat-total">
      <span className="stat-number">42</span>
      <span className="stat-text">Total Contracts</span>
    </div>
    <div className="stat-breakdown-grid">
      {kinds.map((kind) => (
        <div key={kind} className={\`stat-item \${kind.color}\`}>
          <span className="stat-count">{kind.count}</span>
          <span className="stat-kind">{kind.label}</span>
        </div>
      ))}
    </div>
  </div>
</div>`,
        preview: (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <PageHeader title="Contracts" subtitle="스키마, 룰셋, 뷰 정의 등 시스템 계약을 관리합니다" />
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))', gap: '0.75rem' }}>
              <Card style={{ padding: '1rem', textAlign: 'center' }}>
                <div style={{ fontSize: '1.5rem', fontWeight: 700, fontFamily: 'var(--font-mono)', marginBottom: '0.25rem' }}>42</div>
                <div style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>Total</div>
              </Card>
              <Card style={{ padding: '1rem', textAlign: 'center' }}>
                <div style={{ fontSize: '1.5rem', fontWeight: 700, fontFamily: 'var(--font-mono)', marginBottom: '0.25rem' }}>12</div>
                <div style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>Schema</div>
              </Card>
              <Card style={{ padding: '1rem', textAlign: 'center' }}>
                <div style={{ fontSize: '1.5rem', fontWeight: 700, fontFamily: 'var(--font-mono)', marginBottom: '0.25rem' }}>8</div>
                <div style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>RuleSet</div>
              </Card>
            </div>
          </div>
        ),
      },
      {
        title: '헤더와 액션 버튼',
        description: '페이지 헤더와 함께 액션 버튼이 있는 패턴',
        code: `import { PageHeader, Button } from '@/shared/ui'

<div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
  <PageHeader
    title="RawData"
    subtitle="원본 데이터를 탐색하고 관리합니다"
  />
  <Button variant="primary" icon={<Plus size={16} />}>
    새로 만들기
  </Button>
</div>`,
        preview: (
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '1rem' }}>
            <PageHeader
              title="RawData"
              subtitle="원본 데이터를 탐색하고 관리합니다"
            />
            <Button variant="primary" size="sm" icon={<Plus size={16} />}>
              새로 만들기
            </Button>
          </div>
        ),
      },
    ],
    bestPractices: [
      '페이지 헤더는 항상 명확한 제목과 설명을 포함합니다',
      '통계 카드는 관련된 정보를 그룹화하여 표시합니다',
      '액션 버튼은 헤더 오른쪽에 배치하여 일관성을 유지합니다',
      '통계 값은 monospace 폰트를 사용하여 가독성을 향상시킵니다',
      '헤더와 콘텐츠 사이에 적절한 간격을 유지합니다',
    ],
  },
  formActions: {
    title: 'Form with Actions',
    description: '폼 필드에 액션 버튼이 있는 패턴, JSON 에디터 등 고급 폼 패턴입니다.',
    icon: <FileCode size={32} />,
    color: 'var(--accent-purple)',
    preview: (
      <div className="pattern-preview pattern-preview-form-actions">
        <div className="pattern-preview-label" />
        <div className="pattern-preview-input-with-actions" />
      </div>
    ),
    examples: [
      {
        title: '라벨과 액션 버튼',
        description: '라벨 옆에 액션 버튼이 있는 패턴 (RawDataEditor 패턴)',
        code: `import { Label, Button } from '@/shared/ui'
import { Wand2, Code2, Copy, Eye } from 'lucide-react'

<div className="form-group">
  <div className="json-header">
    <Label>JSON Data</Label>
    <div className="json-actions">
      <Button size="sm" variant="ghost" icon={<Wand2 size={12} />}>
        샘플 생성
      </Button>
      <Button size="sm" variant="ghost" icon={<Code2 size={12} />}>
        Format
      </Button>
      <IconButton icon={<Copy size={12} />} aria-label="Copy" />
      <IconButton icon={<Eye size={12} />} aria-label="Preview" />
    </div>
  </div>
  <TextArea rows={10} placeholder="JSON 데이터를 입력하세요" />
</div>`,
        preview: (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Label>JSON Data</Label>
              <div style={{ display: 'flex', gap: '0.25rem' }}>
                <IconButton icon={Wand2} aria-label="샘플 생성" size="sm" />
                <IconButton icon={Code2} aria-label="Format" size="sm" />
                <IconButton icon={Copy} aria-label="Copy" size="sm" />
                <IconButton icon={Eye} aria-label="Preview" size="sm" />
              </div>
            </div>
            <TextArea rows={6} placeholder="JSON 데이터를 입력하세요" />
          </div>
        ),
      },
      {
        title: '드래그 앤 드롭 파일 업로드',
        description: '파일을 드롭하여 업로드할 수 있는 패턴',
        code: `import { TextArea, Upload } from 'lucide-react'

<div
  className={\`json-input-container \${isDragging ? 'dragging' : ''}\`}
  onDragOver={(e) => {
    e.preventDefault()
    setIsDragging(true)
  }}
  onDragLeave={() => setIsDragging(false)}
  onDrop={handleDrop}
>
  {isDragging && (
    <div className="drop-overlay">
      <Upload size={32} />
      <span>JSON 파일을 여기에 드롭하세요</span>
    </div>
  )}
  <TextArea value={jsonInput} onChange={handleChange} />
  <button onClick={() => fileInputRef.current?.click()}>
    <Upload size={14} />
    파일 업로드
  </button>
</div>`,
        preview: (
          <div style={{ position: 'relative', border: '2px dashed var(--border-color)', borderRadius: 'var(--radius-md)', padding: '1rem' }}>
            <TextArea rows={6} placeholder="JSON 데이터를 입력하거나 파일을 드롭하세요" />
            <div style={{ marginTop: '0.5rem', display: 'flex', justifyContent: 'flex-end' }}>
              <Button variant="ghost" size="sm" icon={<Upload size={14} />}>
                파일 업로드
              </Button>
            </div>
          </div>
        ),
      },
      {
        title: '에러 메시지 표시',
        description: '폼 필드 아래에 에러 메시지를 표시하는 패턴',
        code: `import { Label, Input, Alert } from '@/shared/ui'

<div className="form-group">
  <Label required>JSON Data</Label>
  <TextArea
    value={jsonInput}
    onChange={handleChange}
    error={jsonError}
  />
  {jsonError && (
    <Alert variant="error" size="sm">
      {jsonError}
    </Alert>
  )}
</div>`,
        preview: (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <Label required>JSON Data</Label>
            <TextArea rows={4} error errorMessage="유효하지 않은 JSON 형식입니다" />
            <Alert variant="error" size="sm">유효하지 않은 JSON 형식입니다</Alert>
          </div>
        ),
      },
    ],
    bestPractices: [
      '폼 필드의 액션 버튼은 관련된 기능만 포함합니다',
      '드래그 앤 드롭은 시각적 피드백을 제공합니다',
      '에러 메시지는 필드 바로 아래에 표시하여 명확하게 합니다',
      '액션 버튼은 작은 크기로 하여 필드와 조화롭게 배치합니다',
      '파일 업로드는 명확한 안내 메시지를 제공합니다',
    ],
  },
  emptyStates: {
    title: 'Empty States',
    description: '데이터가 없을 때, 에러 상태, 초기 상태 등 빈 상태 표시 패턴입니다.',
    icon: <Inbox size={32} />,
    color: 'var(--accent-cyan)',
    preview: (
      <div className="pattern-preview pattern-preview-empty">
        <div className="pattern-preview-empty-icon" />
        <div className="pattern-preview-empty-title" />
        <div className="pattern-preview-empty-description" />
      </div>
    ),
    examples: [
      {
        title: '기본 EmptyState',
        description: 'EmptyState 컴포넌트를 사용한 기본 패턴',
        code: `import { EmptyState, Button } from '@/shared/ui'
import { Database } from 'lucide-react'

<EmptyState
  icon={<Database size={48} />}
  title="데이터가 없습니다"
  description="검색 조건을 변경하거나 새 데이터를 등록해보세요"
  action={<Button variant="primary">새로 만들기</Button>}
/>`,
        preview: (
          <EmptyState
            icon={<Database size={48} />}
            title="데이터가 없습니다"
            description="검색 조건을 변경하거나 새 데이터를 등록해보세요"
            action={<Button variant="primary" size="sm">새로 만들기</Button>}
          />
        ),
      },
      {
        title: '에러 EmptyState',
        description: '에러 상태를 표시하는 EmptyState',
        code: `import { EmptyState, Button } from '@/shared/ui'
import { AlertCircle, RefreshCw } from 'lucide-react'

<EmptyState
  icon={<AlertCircle size={48} />}
  title="데이터를 불러올 수 없습니다"
  description="네트워크 연결을 확인하고 다시 시도해주세요"
  action={
    <Button variant="primary" icon={<RefreshCw size={14} />} onClick={refetch}>
      다시 시도
    </Button>
  }
/>`,
        preview: (
          <EmptyState
            icon={<AlertCircle size={48} />}
            title="데이터를 불러올 수 없습니다"
            description="네트워크 연결을 확인하고 다시 시도해주세요"
            action={<Button variant="primary" size="sm" icon={<RefreshCw size={14} />}>다시 시도</Button>}
          />
        ),
      },
      {
        title: '테이블 빈 상태',
        description: '테이블 내부에 표시되는 빈 상태 (ExplorerTable 패턴)',
        code: `import { Database, Eye } from 'lucide-react'

{data.length === 0 && (
  <div className="table-empty">
    {listType === 'views' ? <Eye size={48} /> : <Database size={48} />}
    <h3>{listType === 'views' ? 'ViewDefinition이 없습니다' : '데이터가 없습니다'}</h3>
    <p>
      {listType === 'views'
        ? 'Contract에서 ViewDefinition을 추가하세요.'
        : '검색 조건을 변경하거나 새 데이터를 등록해보세요.'}
    </p>
    {onCreateNew && (
      <Button variant="primary" icon={<Plus size={16} />} onClick={onCreateNew}>
        새 RawData 등록
      </Button>
    )}
  </div>
)}`,
        preview: (
          <Card style={{ padding: '3rem', textAlign: 'center' }}>
            <Database size={48} style={{ color: 'var(--text-muted)', marginBottom: '1rem' }} />
            <h3 style={{ margin: '0 0 0.5rem 0', fontSize: '1.125rem' }}>데이터가 없습니다</h3>
            <p style={{ margin: '0 0 1rem 0', color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
              검색 조건을 변경하거나 새 데이터를 등록해보세요.
            </p>
            <Button variant="primary" size="sm" icon={<Plus size={16} />}>새 RawData 등록</Button>
          </Card>
        ),
      },
    ],
    bestPractices: [
      '빈 상태는 명확한 아이콘과 메시지를 제공합니다',
      '사용자가 다음 행동을 취할 수 있는 액션 버튼을 제공합니다',
      '에러 상태와 빈 상태를 명확히 구분합니다',
      '컨텍스트에 맞는 적절한 아이콘을 사용합니다',
      '긍정적인 톤으로 메시지를 작성하여 사용자 경험을 개선합니다',
    ],
  },
  statusDisplays: {
    title: 'Status Displays',
    description: '상태 배지, 헬스 배너, 상태 인디케이터 등 상태 표시 패턴입니다.',
    icon: <Activity size={32} />,
    color: 'var(--accent-green)',
    preview: (
      <div className="pattern-preview pattern-preview-status">
        <div className="pattern-preview-status-dot" />
        <div className="pattern-preview-status-text" />
      </div>
    ),
    examples: [
      {
        title: 'StatusBadge',
        description: 'StatusBadge 컴포넌트를 사용한 상태 표시',
        code: `import { StatusBadge } from '@/shared/ui'

<StatusBadge status="healthy" />
<StatusBadge status="pending" />
<StatusBadge status="failed" />`,
        preview: (
          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            <StatusBadge status="healthy" />
            <StatusBadge status="pending" />
            <StatusBadge status="failed" />
            <StatusBadge status="degraded" />
          </div>
        ),
      },
      {
        title: '헬스 배너',
        description: '전체 시스템 상태를 표시하는 배너 (Health 페이지 패턴)',
        code: `import { CheckCircle2, AlertTriangle, XCircle } from 'lucide-react'

<div className={\`health-banner \${getStatusClass(status)}\`}>
  <div className="health-banner-status">
    <div className="health-banner-icon">
      {getStatusIcon(status)}
    </div>
    <div className="health-banner-text">
      <span className="health-banner-label">Overall Status</span>
      <span className="health-banner-value">{status}</span>
    </div>
  </div>
  <div className="health-banner-uptime">
    <span className="uptime-label">Uptime</span>
    <span className="uptime-value">{formatUptime(uptime)}</span>
  </div>
</div>`,
        preview: (
          <Card style={{ padding: '1.5rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'linear-gradient(135deg, rgba(0, 255, 136, 0.1), rgba(0, 255, 136, 0.05))', borderColor: 'rgba(0, 255, 136, 0.3)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
              <CheckCircle2 size={48} style={{ color: 'var(--status-success)' }} />
              <div style={{ display: 'flex', flexDirection: 'column' }}>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', textTransform: 'uppercase' }}>Overall Status</span>
                <span style={{ fontSize: '1.5rem', fontWeight: 700, fontFamily: 'var(--font-mono)', color: 'var(--status-success)' }}>HEALTHY</span>
              </div>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', textAlign: 'right' }}>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Uptime</span>
              <span style={{ fontSize: '1.25rem', fontWeight: 700, fontFamily: 'var(--font-mono)' }}>99.9%</span>
            </div>
          </Card>
        ),
      },
      {
        title: '컴포넌트 상태 카드',
        description: '개별 컴포넌트의 상태를 표시하는 카드',
        code: `import { Server, CheckCircle2 } from 'lucide-react'

<div className={\`health-card \${getStatusClass(component.status)}\`}>
  <div className="health-card-header">
    <div className={\`health-status-dot \${getStatusClass(component.status)}\`}>
      {getStatusIcon(component.status)}
    </div>
  </div>
  <div className="health-card-icon">
    <Server size={32} />
  </div>
  <div className="health-card-content">
    <span className="health-card-name">{component.name}</span>
    {component.latencyMs && (
      <span className="health-card-latency">{component.latencyMs}ms</span>
    )}
  </div>
</div>`,
        preview: (
          <Card style={{ padding: '1rem', textAlign: 'center', position: 'relative' }}>
            <div style={{ position: 'absolute', top: '0.5rem', right: '0.5rem' }}>
              <div style={{ width: '12px', height: '12px', borderRadius: '50%', background: 'var(--status-success)' }} />
            </div>
            <Server size={32} style={{ marginBottom: '0.5rem', color: 'var(--accent-cyan)' }} />
            <div style={{ fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.25rem' }}>Database</div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', fontFamily: 'var(--font-mono)' }}>42ms</div>
          </Card>
        ),
      },
    ],
    bestPractices: [
      '상태는 색상과 아이콘으로 명확하게 구분합니다',
      '상태 배지는 일관된 스타일을 유지합니다',
      '헬스 상태는 전체 시스템과 개별 컴포넌트를 모두 표시합니다',
      '상태 변경 시 애니메이션을 사용하여 사용자에게 알립니다',
      '상태 정보는 monospace 폰트를 사용하여 가독성을 향상시킵니다',
    ],
  },
  collapsible: {
    title: 'Collapsible Sections',
    description: 'Accordion, 접을 수 있는 섹션, 확장 가능한 패널 등 접기/펼치기 패턴입니다.',
    icon: <ChevronDown size={32} />,
    color: 'var(--accent-purple)',
    preview: (
      <div className="pattern-preview pattern-preview-collapsible">
        <div className="pattern-preview-collapsible-header" />
        <div className="pattern-preview-collapsible-content" />
      </div>
    ),
    examples: [
      {
        title: '기본 Accordion',
        description: 'Accordion 컴포넌트를 사용한 기본 패턴',
        code: `import { Accordion, AccordionItem, AccordionTrigger, AccordionContent } from '@/shared/ui'

<Accordion type="single" defaultValue="item1">
  <AccordionItem value="item1">
    <AccordionTrigger>Section 1</AccordionTrigger>
    <AccordionContent>
      <p>Content for section 1</p>
    </AccordionContent>
  </AccordionItem>
  <AccordionItem value="item2">
    <AccordionTrigger>Section 2</AccordionTrigger>
    <AccordionContent>
      <p>Content for section 2</p>
    </AccordionContent>
  </AccordionItem>
</Accordion>`,
        preview: (
          <Accordion type="single" defaultValue="item1">
            <AccordionItem value="item1">
              <AccordionTrigger value="item1">Section 1</AccordionTrigger>
              <AccordionContent value="item1">
                <p style={{ margin: 0, padding: '0.5rem 0', color: 'var(--text-secondary)' }}>Content for section 1</p>
              </AccordionContent>
            </AccordionItem>
            <AccordionItem value="item2">
              <AccordionTrigger value="item2">Section 2</AccordionTrigger>
              <AccordionContent value="item2">
                <p style={{ margin: 0, padding: '0.5rem 0', color: 'var(--text-secondary)' }}>Content for section 2</p>
              </AccordionContent>
            </AccordionItem>
          </Accordion>
        ),
      },
      {
        title: 'Multiple Accordion',
        description: '여러 섹션을 동시에 열 수 있는 패턴',
        code: `import { Accordion, AccordionItem, AccordionTrigger, AccordionContent } from '@/shared/ui'

<Accordion type="multiple" defaultValue={['item1', 'item2']}>
  <AccordionItem value="item1">
    <AccordionTrigger>Basic Info</AccordionTrigger>
    <AccordionContent>
      {/* Basic information */}
    </AccordionContent>
  </AccordionItem>
  <AccordionItem value="item2">
    <AccordionTrigger>Metadata</AccordionTrigger>
    <AccordionContent>
      {/* Metadata */}
    </AccordionContent>
  </AccordionItem>
</Accordion>`,
        preview: (
          <Accordion type="multiple" defaultValue={['item1', 'item2']}>
            <AccordionItem value="item1">
              <AccordionTrigger value="item1">Basic Info</AccordionTrigger>
              <AccordionContent value="item1">
                <div style={{ padding: '0.5rem 0', color: 'var(--text-secondary)', fontSize: '0.875rem' }}>Basic information content</div>
              </AccordionContent>
            </AccordionItem>
            <AccordionItem value="item2">
              <AccordionTrigger value="item2">Metadata</AccordionTrigger>
              <AccordionContent value="item2">
                <div style={{ padding: '0.5rem 0', color: 'var(--text-secondary)', fontSize: '0.875rem' }}>Metadata content</div>
              </AccordionContent>
            </AccordionItem>
          </Accordion>
        ),
      },
      {
        title: '아이콘과 카운트가 있는 섹션',
        description: '아이콘과 항목 개수가 있는 접을 수 있는 섹션 (SpanDetails 패턴)',
        code: `import { Info, AlertCircle, Copy } from 'lucide-react'

<div className="collapsible-section">
  <button className="section-header" onClick={toggleSection}>
    <span className="section-icon"><Info size={14} /></span>
    <span className="section-title">Basic Info</span>
    {count !== undefined && (
      <span className="section-count">{count}</span>
    )}
    <ChevronDown className={\`section-chevron \${isExpanded ? 'expanded' : ''}\`} />
  </button>
  {isExpanded && (
    <div className="section-content">
      {/* Content */}
    </div>
  )}
</div>`,
        preview: (
          <Card style={{ padding: 0, overflow: 'hidden' }}>
            <button style={{ width: '100%', display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.75rem 1rem', background: 'transparent', border: 'none', color: 'var(--text-primary)', cursor: 'pointer' }}>
              <Info size={14} style={{ color: 'var(--text-muted)' }} />
              <span style={{ fontSize: '0.75rem', fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-secondary)' }}>Basic Info</span>
              <span style={{ marginLeft: 'auto' }}><Chip>3</Chip></span>
              <ChevronDown size={14} style={{ color: 'var(--text-muted)' }} />
            </button>
            <div style={{ padding: '0.5rem 1rem 1rem', borderTop: '1px solid var(--border-color)' }}>
              <div style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>Section content here</div>
            </div>
          </Card>
        ),
      },
    ],
    bestPractices: [
      '접을 수 있는 섹션은 명확한 헤더와 아이콘을 제공합니다',
      '여러 섹션을 동시에 열 수 있는 경우 multiple 모드를 사용합니다',
      '섹션 헤더에 항목 개수를 표시하여 사용자에게 정보를 제공합니다',
      '접기/펼치기 애니메이션을 사용하여 사용자 경험을 개선합니다',
      '키보드 네비게이션을 지원하여 접근성을 향상시킵니다',
    ],
  },
  jsonViewer: {
    title: 'JSON Viewer',
    description: 'JSON 데이터 표시, JsonViewer 컴포넌트, 코드 에디터 등 JSON 관련 패턴입니다.',
    icon: <FileJson size={32} />,
    color: 'var(--accent-purple)',
    preview: (
      <div className="pattern-preview pattern-preview-json">
        <div className="pattern-preview-json-key" />
        <div className="pattern-preview-json-value" />
      </div>
    ),
    examples: [
      {
        title: '기본 JsonViewer',
        description: 'JsonViewer 컴포넌트를 사용한 기본 패턴',
        code: `import { JsonViewer } from '@/shared/ui/recipes'

const data = {
  name: 'Product',
  price: 25000,
  stock: 150,
  tags: ['beauty', 'skincare']
}

<JsonViewer data={data} />`,
        preview: (
          <Card style={{ padding: '1rem', maxHeight: '200px', overflow: 'auto' }}>
            <JsonViewer
              data={{
                name: 'Product',
                price: 25000,
                stock: 150,
                tags: ['beauty', 'skincare'],
              }}
              initialExpanded={true}
            />
          </Card>
        ),
      },
      {
        title: '접을 수 있는 JsonViewer',
        description: '초기 상태에서 접혀있는 JsonViewer',
        code: `import { JsonViewer } from '@/shared/ui/recipes'

<JsonViewer
  data={complexData}
  initialExpanded={false}
  maxDepth={3}
/>`,
        preview: (
          <Card style={{ padding: '1rem', maxHeight: '200px', overflow: 'auto' }}>
            <JsonViewer
              data={{
                id: '123',
                metadata: {
                  created: '2024-01-01',
                  updated: '2024-01-02',
                },
              }}
              initialExpanded={false}
            />
          </Card>
        ),
      },
      {
        title: 'Monaco Editor로 JSON 표시',
        description: 'Monaco Editor를 사용한 JSON 에디터 (Playground 패턴)',
        code: `import Editor from '@monaco-editor/react'

<Editor
  height="400px"
  language="json"
  theme="vs-dark"
  value={jsonString}
  onChange={handleChange}
  options={{
    minimap: { enabled: false },
    fontSize: 12,
    lineHeight: 18,
    tabSize: 2,
    wordWrap: 'on',
    formatOnPaste: true,
    formatOnType: true,
  }}
/>`,
        preview: (
          <Card style={{ padding: '1rem', background: 'var(--bg-tertiary)' }}>
            <pre style={{ margin: 0, fontSize: '0.75rem', fontFamily: 'var(--font-mono)', color: 'var(--text-primary)', whiteSpace: 'pre-wrap' }}>
{`{
  "name": "Product",
  "price": 25000,
  "stock": 150
}`}
            </pre>
          </Card>
        ),
      },
    ],
    bestPractices: [
      'JSON 데이터는 JsonViewer 컴포넌트를 사용하여 구조화된 형태로 표시합니다',
      '큰 JSON 객체는 초기 상태에서 접혀있게 하여 성능을 최적화합니다',
      'Monaco Editor를 사용할 때는 적절한 옵션을 설정하여 사용자 경험을 개선합니다',
      'JSON 복사 기능을 제공하여 사용자 편의성을 향상시킵니다',
      'JSON 검증 에러는 명확하게 표시합니다',
    ],
  },
  diffViewer: {
    title: 'Diff Viewer',
    description: '변경사항 비교, 버전 차이 표시, DiffViewer 컴포넌트 등 diff 관련 패턴입니다.',
    icon: <RefreshCw size={32} />,
    color: 'var(--accent-cyan)',
    preview: (
      <div className="pattern-preview pattern-preview-diff">
        <div className="pattern-preview-diff-added" />
        <div className="pattern-preview-diff-removed" />
        <div className="pattern-preview-diff-modified" />
      </div>
    ),
    examples: [
      {
        title: '기본 DiffViewer',
        description: 'DiffViewer 컴포넌트를 사용한 버전 비교',
        code: `import { DiffViewer } from '@/shared/ui/recipes'

const diffs: VersionDiff[] = [
  { type: 'added', path: 'name', value: 'New Product' },
  { type: 'removed', path: 'oldField', value: 'Old Value' },
  { type: 'changed', path: 'price', oldValue: 20000, newValue: 25000 },
]

<DiffViewer
  fromVersion={1}
  toVersion={2}
  diffs={diffs}
/>`,
        preview: (
          <Card style={{ padding: '1rem' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
                <span style={{ fontSize: '0.875rem', fontWeight: 600 }}>v1</span>
                <ArrowRight size={14} />
                <span style={{ fontSize: '0.875rem', fontWeight: 600 }}>v2</span>
              </div>
              <div style={{ padding: '0.5rem', background: 'rgba(0, 255, 136, 0.1)', borderRadius: 'var(--radius-sm)', fontSize: '0.875rem' }}>
                <span style={{ color: 'var(--status-success)' }}>+</span> name: "New Product"
              </div>
              <div style={{ padding: '0.5rem', background: 'rgba(245, 101, 101, 0.1)', borderRadius: 'var(--radius-sm)', fontSize: '0.875rem' }}>
                <span style={{ color: 'var(--status-error)' }}>-</span> oldField: "Old Value"
              </div>
              <div style={{ padding: '0.5rem', background: 'rgba(255, 204, 0, 0.1)', borderRadius: 'var(--radius-sm)', fontSize: '0.875rem' }}>
                <span style={{ color: 'var(--status-warning)' }}>~</span> price: 20000 → 25000
              </div>
            </div>
          </Card>
        ),
      },
      {
        title: '변경사항 요약',
        description: '변경사항 통계와 함께 표시하는 패턴',
        code: `import { DiffViewer } from '@/shared/ui/recipes'

const stats = {
  added: diffs.filter(d => d.type === 'added').length,
  removed: diffs.filter(d => d.type === 'removed').length,
  changed: diffs.filter(d => d.type === 'changed').length,
}

<div className="diff-summary">
  <span>Added: {stats.added}</span>
  <span>Removed: {stats.removed}</span>
  <span>Changed: {stats.changed}</span>
</div>
<DiffViewer fromVersion={1} toVersion={2} diffs={diffs} />`,
        preview: (
          <Card style={{ padding: '1rem' }}>
            <div style={{ display: 'flex', gap: '1rem', marginBottom: '1rem', fontSize: '0.875rem' }}>
              <span style={{ color: 'var(--status-success)' }}>Added: 2</span>
              <span style={{ color: 'var(--status-error)' }}>Removed: 1</span>
              <span style={{ color: 'var(--status-warning)' }}>Changed: 1</span>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
              <div style={{ padding: '0.5rem', background: 'rgba(0, 255, 136, 0.1)', borderRadius: 'var(--radius-sm)', fontSize: '0.875rem' }}>
                <span style={{ color: 'var(--status-success)' }}>+</span> name: "New Product"
              </div>
              <div style={{ padding: '0.5rem', background: 'rgba(245, 101, 101, 0.1)', borderRadius: 'var(--radius-sm)', fontSize: '0.875rem' }}>
                <span style={{ color: 'var(--status-error)' }}>-</span> oldField
              </div>
            </div>
          </Card>
        ),
      },
      {
        title: '변경사항 없음',
        description: '변경사항이 없을 때 표시하는 패턴',
        code: `import { DiffViewer } from '@/shared/ui/recipes'
import { CheckCircle2 } from 'lucide-react'

{diffs.length === 0 ? (
  <div className="diff-empty-state">
    <CheckCircle2 size={32} />
    <p>v{fromVersion}과 v{toVersion} 사이에 변경사항이 없습니다.</p>
  </div>
) : (
  <DiffViewer fromVersion={fromVersion} toVersion={toVersion} diffs={diffs} />
)}`,
        preview: (
          <Card style={{ padding: '2rem', textAlign: 'center' }}>
            <CheckCircle2 size={32} style={{ color: 'var(--status-success)', marginBottom: '0.5rem' }} />
            <p style={{ margin: 0, color: 'var(--text-secondary)', fontSize: '0.875rem' }}>v1과 v2 사이에 변경사항이 없습니다.</p>
          </Card>
        ),
      },
    ],
    bestPractices: [
      '변경사항은 타입별로 색상을 구분하여 표시합니다 (추가: 녹색, 삭제: 빨간색, 수정: 노란색)',
      '변경사항 통계를 먼저 표시하여 사용자가 전체 변경 내용을 파악할 수 있게 합니다',
      '변경사항이 없을 때는 명확한 메시지를 표시합니다',
      '버전 정보는 명확하게 표시하여 사용자가 비교 대상을 이해할 수 있게 합니다',
      '큰 diff는 페이지네이션 또는 필터링을 고려합니다',
    ],
  },
  cells: {
    title: 'Table Cell Patterns',
    description: '테이블 셀 스타일링, 엔티티 셀, 버전 셀, 시간 셀, 액션 셀 등 테이블 셀 관련 패턴입니다.',
    icon: <Table2 size={32} />,
    color: 'var(--accent-cyan)',
    preview: (
      <div className="pattern-preview pattern-preview-cells">
        <div className="pattern-preview-cell" />
        <div className="pattern-preview-cell" />
        <div className="pattern-preview-cell" />
      </div>
    ),
    examples: [
      {
        title: 'Entity Cell',
        description: '아이콘과 텍스트가 함께 있는 엔티티 ID 셀',
        code: `import { Database } from 'lucide-react'

<td className="entity-cell">
  <Database size={14} />
  <span>product-12345</span>
</td>`,
        preview: (
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.5rem', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-sm)' }}>
            <Database size={14} style={{ color: 'var(--accent-cyan)' }} />
            <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.875rem' }}>product-12345</span>
          </div>
        ),
      },
      {
        title: 'Version Cell',
        description: '버전 배지가 있는 버전 셀',
        code: `<td className="version-cell">
  <span className="version-badge">v{entry.version}</span>
</td>`,
        preview: (
          <div style={{ padding: '0.5rem', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-sm)' }}>
            <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem', padding: '0.25rem 0.5rem', background: 'var(--bg-tertiary)', borderRadius: 'var(--radius-sm)', display: 'inline-block' }}>v1</span>
          </div>
        ),
      },
      {
        title: 'Time Cell',
        description: '아이콘과 함께 표시되는 시간 셀',
        code: `import { Clock } from 'lucide-react'

<td className="time-cell">
  <Clock size={12} />
  {entry.updatedAt}
</td>`,
        preview: (
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.375rem', padding: '0.5rem', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-sm)' }}>
            <Clock size={12} style={{ color: 'var(--text-muted)' }} />
            <span style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>2024-01-15 14:30</span>
          </div>
        ),
      },
      {
        title: 'Action Cell',
        description: '호버 시 표시되는 액션 셀',
        code: `import { ChevronRight } from 'lucide-react'

<td className="action-cell">
  <ChevronRight size={14} />
</td>`,
        preview: (
          <div style={{ display: 'flex', justifyContent: 'center', padding: '0.5rem', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-sm)', cursor: 'pointer' }}>
            <ChevronRight size={14} style={{ color: 'var(--text-muted)' }} />
          </div>
        ),
      },
    ],
    bestPractices: [
      '엔티티 ID는 monospace 폰트를 사용하여 가독성을 향상시킵니다',
      '버전은 배지 형태로 표시하여 시각적으로 구분합니다',
      '시간은 아이콘과 함께 표시하여 정보를 명확하게 전달합니다',
      '액션 셀은 호버 시에만 표시하여 UI를 깔끔하게 유지합니다',
      '셀 스타일은 일관되게 유지하여 사용자 경험을 개선합니다',
    ],
  },
  layouts: {
    title: 'Layout Patterns',
    description: '페이지 컨테이너, 대시보드 그리드, GNB, 레이아웃 구조 등 레이아웃 관련 패턴입니다.',
    icon: <LayoutGrid size={32} />,
    color: 'var(--accent-purple)',
    preview: (
      <div className="pattern-preview pattern-preview-layout">
        <div className="pattern-preview-layout-header" />
        <div className="pattern-preview-layout-content" />
      </div>
    ),
    examples: [
      {
        title: 'Page Container',
        description: '모든 페이지에서 사용하는 기본 컨테이너',
        code: `<div className="page-container">
  <PageHeader title="Page Title" subtitle="Page description" />
  {/* 페이지 내용 */}
</div>`,
        preview: (
          <Card style={{ padding: '1rem' }}>
            <div style={{ marginBottom: '1rem' }}>
              <h2 style={{ margin: '0 0 0.25rem 0', fontSize: '1.5rem', fontWeight: 700 }}>Page Title</h2>
              <p style={{ margin: 0, color: 'var(--text-secondary)', fontSize: '0.875rem' }}>Page description</p>
            </div>
            <div style={{ padding: '1rem', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-md)' }}>
              페이지 내용
            </div>
          </Card>
        ),
      },
      {
        title: 'GNB (Global Navigation Bar)',
        description: '상단 네비게이션 바 패턴 (DataExplorer)',
        code: `<div className="explorer-gnb">
  <div className="gnb-left">
    <button className="back-btn" onClick={handleBack}>
      <ChevronLeft size={14} />
      뒤로가기
    </button>
  </div>
  <div className="gnb-center">
    <div className="type-tabs">
      <button className="type-tab active">RawData</button>
      <button className="type-tab">Slices</button>
      <button className="type-tab">Views</button>
    </div>
  </div>
  <div className="gnb-right">
    <Input placeholder="검색..." leftIcon={<Search size={14} />} />
  </div>
</div>`,
        preview: (
          <Card style={{ padding: '0.75rem', background: 'var(--bg-secondary)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '1rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Button variant="ghost" size="sm" icon={<ChevronLeft size={14} />}>뒤로가기</Button>
              </div>
              <div style={{ display: 'flex', gap: '0.25rem', padding: '0.25rem', background: 'var(--bg-tertiary)', borderRadius: 'var(--radius-md)' }}>
                <button style={{ padding: '0.5rem 1rem', background: 'var(--accent-cyan)', color: 'white', border: 'none', borderRadius: 'var(--radius-sm)', fontSize: '0.8rem' }}>RawData</button>
                <button style={{ padding: '0.5rem 1rem', background: 'transparent', color: 'var(--text-muted)', border: 'none', borderRadius: 'var(--radius-sm)', fontSize: '0.8rem' }}>Slices</button>
              </div>
              <div style={{ minWidth: '200px' }}>
                <Input placeholder="검색..." leftIcon={<Search size={14} />} size="sm" />
              </div>
            </div>
          </Card>
        ),
      },
      {
        title: 'Dashboard Grid',
        description: '대시보드 2열 그리드 레이아웃',
        code: `<div className="dashboard-grid">
  <div className="outbox-panel">
    <div className="panel-header">
      <Inbox size={18} />
      <h3>Outbox Queue</h3>
      <Link to="/outbox" className="view-all">상세보기</Link>
    </div>
    {/* 패널 내용 */}
  </div>
  <div className="actions-panel">
    {/* 액션 패널 내용 */}
  </div>
</div>`,
        preview: (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '1rem' }}>
            <Card style={{ padding: '1rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <Inbox size={18} />
                  <h3 style={{ margin: 0 }}>Outbox Queue</h3>
                </div>
                <Link to="#" style={{ color: 'var(--accent-cyan)', textDecoration: 'none', fontSize: '0.875rem' }}>상세보기</Link>
              </div>
              <div style={{ padding: '1rem', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-md)' }}>패널 내용</div>
            </Card>
            <Card style={{ padding: '1rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <Zap size={18} />
                  <h3 style={{ margin: 0 }}>Quick Actions</h3>
                </div>
              </div>
              <div style={{ padding: '1rem', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-md)' }}>액션 패널 내용</div>
            </Card>
          </div>
        ),
      },
    ],
    bestPractices: [
      '페이지 컨테이너는 일관된 패딩과 마진을 사용합니다',
      'GNB는 left, center, right 영역으로 구분하여 명확한 구조를 만듭니다',
      '대시보드 그리드는 반응형으로 작동하도록 CSS Grid를 사용합니다',
      '레이아웃은 모바일에서도 사용 가능하도록 반응형을 고려합니다',
      '일관된 간격 시스템을 사용하여 시각적 조화를 유지합니다',
    ],
  },
  textUtilities: {
    title: 'Text Utilities',
    description: '텍스트 유틸리티 클래스, monospace 폰트, 색상 유틸리티, truncate 등 텍스트 스타일링 패턴입니다.',
    icon: <Type size={32} />,
    color: 'var(--accent-purple)',
    preview: (
      <div className="pattern-preview pattern-preview-text">
        <div className="pattern-preview-text-mono" />
        <div className="pattern-preview-text-secondary" />
        <div className="pattern-preview-text-truncate" />
      </div>
    ),
    examples: [
      {
        title: 'Monospace 폰트',
        description: 'ID, 버전 등 코드성 데이터에 사용',
        code: `<td className="mono">{item.id.slice(0, 8)}...</td>
<span className="mono">v1.2.3</span>`,
        preview: (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.875rem' }}>abc12345...</span>
            <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.875rem' }}>v1.2.3</span>
          </div>
        ),
      },
      {
        title: '텍스트 색상 유틸리티',
        description: '보조 텍스트, 에러, 경고 등 색상 클래스',
        code: `<span className="text-secondary">보조 텍스트</span>
<span className="text-muted">흐린 텍스트</span>
<span className="text-error">에러 메시지</span>
<span className="text-warning">경고 메시지</span>`,
        preview: (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <span style={{ color: 'var(--text-secondary)' }}>보조 텍스트</span>
            <span style={{ color: 'var(--text-muted)' }}>흐린 텍스트</span>
            <span style={{ color: 'var(--status-error)' }}>에러 메시지</span>
            <span style={{ color: 'var(--status-warning)' }}>경고 메시지</span>
          </div>
        ),
      },
      {
        title: '텍스트 잘라내기 (Truncate)',
        description: '긴 텍스트를 ellipsis로 처리',
        code: `<span className="truncate" style={{ maxWidth: '200px' }}>
  매우 긴 텍스트가 여기에 표시됩니다...
</span>`,
        preview: (
          <div style={{ maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            매우 긴 텍스트가 여기에 표시됩니다...
          </div>
        ),
      },
    ],
    bestPractices: [
      'ID, 버전, 해시 등은 monospace 폰트를 사용하여 가독성을 향상시킵니다',
      '텍스트 색상은 의미에 맞게 사용합니다 (에러는 빨간색, 경고는 노란색)',
      '긴 텍스트는 truncate를 사용하여 레이아웃을 깨지 않게 합니다',
      '텍스트 유틸리티는 일관되게 사용하여 디자인 시스템을 유지합니다',
      '접근성을 고려하여 색상만으로 정보를 전달하지 않습니다',
    ],
  },
  navigation: {
    title: 'Navigation Patterns',
    description: 'GNB, 뒤로가기 버튼, 타입 탭, 네비게이션 바 등 네비게이션 관련 패턴입니다.',
    icon: <Navigation2 size={32} />,
    color: 'var(--accent-cyan)',
    preview: (
      <div className="pattern-preview pattern-preview-nav">
        <div className="pattern-preview-nav-item" />
        <div className="pattern-preview-nav-item" />
        <div className="pattern-preview-nav-item active" />
      </div>
    ),
    examples: [
      {
        title: '뒤로가기 버튼',
        description: '상세 페이지에서 목록으로 돌아가는 버튼',
        code: `import { ChevronLeft } from 'lucide-react'

<button className="back-btn" onClick={handleBack}>
  <ChevronLeft size={14} />
  뒤로가기
</button>`,
        preview: (
          <Button variant="ghost" size="sm" icon={<ChevronLeft size={14} />}>
            뒤로가기
          </Button>
        ),
      },
      {
        title: 'Type Tabs',
        description: '타입 선택 탭 (DataExplorer 패턴)',
        code: `<div className="type-tabs">
  <button className="type-tab active">
    <Database size={14} />
    RawData
  </button>
  <button className="type-tab">
    <Layers size={14} />
    Slices
  </button>
  <button className="type-tab">
    <Eye size={14} />
    Views
  </button>
</div>`,
        preview: (
          <div style={{ display: 'flex', gap: '0.25rem', padding: '0.25rem', background: 'var(--bg-tertiary)', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)' }}>
            <button style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.5rem 1rem', background: 'var(--accent-cyan)', color: 'white', border: 'none', borderRadius: 'var(--radius-sm)', fontSize: '0.8rem' }}>
              <Database size={14} />
              RawData
            </button>
            <button style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.5rem 1rem', background: 'transparent', color: 'var(--text-muted)', border: 'none', borderRadius: 'var(--radius-sm)', fontSize: '0.8rem' }}>
              <Layers size={14} />
              Slices
            </button>
            <button style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.5rem 1rem', background: 'transparent', color: 'var(--text-muted)', border: 'none', borderRadius: 'var(--radius-sm)', fontSize: '0.8rem' }}>
              <Eye size={14} />
              Views
            </button>
          </div>
        ),
      },
      {
        title: '인라인 검색 바',
        description: 'GNB에 포함되는 인라인 검색 바',
        code: `<div className="search-bar-inline">
  <Search size={16} />
  <input
    type="text"
    placeholder="Entity ID로 검색..."
    value={query}
    onChange={(e) => setQuery(e.target.value)}
  />
</div>`,
        preview: (
          <Input placeholder="Entity ID로 검색..." leftIcon={<Search size={16} />} style={{ minWidth: '200px' }} />
        ),
      },
    ],
    bestPractices: [
      '뒤로가기 버튼은 항상 왼쪽 상단에 배치하여 일관성을 유지합니다',
      '타입 탭은 관련된 콘텐츠를 논리적으로 그룹화할 때 사용합니다',
      '활성 탭은 시각적으로 명확하게 구분합니다',
      '검색 바는 사용자가 쉽게 찾을 수 있는 위치에 배치합니다',
      '네비게이션은 키보드 접근성을 고려하여 구현합니다',
    ],
  },
  editors: {
    title: 'Editor Patterns',
    description: '에디터 헤더, 폼 그룹, JSON 에디터, 에디터 액션 등 에디터 관련 패턴입니다.',
    icon: <FileCode size={32} />,
    color: 'var(--accent-purple)',
    preview: (
      <div className="pattern-preview pattern-preview-editor">
        <div className="pattern-preview-editor-header" />
        <div className="pattern-preview-editor-content" />
      </div>
    ),
    examples: [
      {
        title: 'Editor Header',
        description: '에디터 상단 헤더 (아이콘, 제목, 액션)',
        code: `<div className="editor-header">
  <div className="editor-title">
    <Database size={18} />
    <h3>RawData Editor</h3>
  </div>
  <div className="editor-actions">
    <Button size="sm" variant="ghost">취소</Button>
    <Button size="sm" variant="primary">저장</Button>
  </div>
</div>`,
        preview: (
          <Card style={{ padding: 0, overflow: 'hidden' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '1rem 1.25rem', background: 'var(--bg-secondary)', borderBottom: '1px solid var(--border-color)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Database size={18} style={{ color: 'var(--accent-cyan)' }} />
                <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: 600 }}>RawData Editor</h3>
              </div>
              <div style={{ display: 'flex', gap: '0.5rem' }}>
                <Button size="sm" variant="ghost">취소</Button>
                <Button size="sm" variant="primary">저장</Button>
              </div>
            </div>
            <div style={{ padding: '1rem' }}>에디터 내용</div>
          </Card>
        ),
      },
      {
        title: 'Form Row/Group',
        description: '폼 필드를 행과 그룹으로 구성',
        code: `<div className="form-row">
  <div className="form-group">
    <Label>Tenant</Label>
    <Input value={tenant} onChange={(e) => setTenant(e.target.value)} />
  </div>
  <div className="form-group flex-2">
    <Label>Entity ID</Label>
    <Input value={entityId} onChange={(e) => setEntityId(e.target.value)} />
  </div>
</div>`,
        preview: (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div style={{ display: 'flex', gap: '1rem' }}>
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                <Label>Tenant</Label>
                <Input placeholder="oliveyoung" />
              </div>
              <div style={{ flex: 2, display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                <Label>Entity ID</Label>
                <Input placeholder="product-12345" />
              </div>
            </div>
          </div>
        ),
      },
      {
        title: 'JSON 에디터 헤더',
        description: 'JSON 필드에 액션 버튼이 있는 헤더',
        code: `<div className="form-group">
  <div className="json-header">
    <Label>JSON Data</Label>
    <div className="json-actions">
      <IconButton icon={<Wand2 size={12} />} aria-label="샘플 생성" />
      <IconButton icon={<Code2 size={12} />} aria-label="Format" />
      <IconButton icon={<Copy size={12} />} aria-label="Copy" />
    </div>
  </div>
  <TextArea rows={10} placeholder="JSON 데이터를 입력하세요" />
</div>`,
        preview: (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Label>JSON Data</Label>
              <div style={{ display: 'flex', gap: '0.25rem' }}>
                <IconButton icon={Wand2} aria-label="샘플 생성" size="sm" />
                <IconButton icon={Code2} aria-label="Format" size="sm" />
                <IconButton icon={Copy} aria-label="Copy" size="sm" />
              </div>
            </div>
            <TextArea rows={6} placeholder="JSON 데이터를 입력하세요" />
          </div>
        ),
      },
    ],
    bestPractices: [
      '에디터 헤더는 명확한 제목과 액션 버튼을 포함합니다',
      '폼 그룹은 관련된 필드를 논리적으로 그룹화합니다',
      'flex-2 클래스를 사용하여 필드 비율을 조정합니다',
      'JSON 에디터는 액션 버튼을 제공하여 사용자 편의성을 향상시킵니다',
      '에디터는 저장/취소 버튼을 명확하게 배치합니다',
    ],
  },
}

// ============================================================================
// Pattern Preview Card
// ============================================================================

interface PatternCardProps {
  id: string
  info: PatternInfo
}

function PatternCard({ id, info }: PatternCardProps) {
  const headerIcon = normalizeLucide(info.icon, { size: 18, className: 'pattern-card-icon__svg' })
  return (
    <motion.div
      whileHover={{ scale: 1.02, y: -2 }}
      whileTap={{ scale: 0.98 }}
      transition={{ type: 'spring', stiffness: 400, damping: 25 }}
    >
      <Link
        to={`/design-system/patterns/${id}`}
        className="pattern-card"
      >
        <div className="pattern-card-preview" style={{ borderColor: info.color }}>
          {info.preview}
        </div>
        <div className="pattern-card-content">
          <div className="pattern-card-header">
            <span className="pattern-card-icon" style={{ color: info.color }}>
              {headerIcon}
            </span>
            <h3 className="pattern-card-title">{info.title}</h3>
          </div>
          <p className="pattern-card-description">{info.description}</p>
          <div className="pattern-card-footer">
            <span className="pattern-card-link">
              자세히 보기
              <ArrowRight size={14} className="ui-icon ui-icon--muted pattern-card-link__arrow" />
            </span>
          </div>
        </div>
      </Link>
    </motion.div>
  )
}

// ============================================================================
// Code Block Component
// ============================================================================

function CodeBlock({ code }: { code: string }) {
  const [copied, setCopied] = useState(false)

  const handleCopy = async () => {
    await navigator.clipboard.writeText(code)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="pattern-code-block">
      <div className="pattern-code-header">
        <span className="pattern-code-label">Code</span>
        <button
          className="pattern-code-copy"
          onClick={handleCopy}
          aria-label="Copy code"
        >
          {copied ? <Check size={14} /> : <Copy size={14} />}
        </button>
      </div>
      <pre className="pattern-code-content">
        <code>{code}</code>
      </pre>
    </div>
  )
}

// ============================================================================
// Pattern Example Component
// ============================================================================

function PatternExampleCard({ example }: { example: PatternExample }) {
  return (
    <Card className="pattern-example-card">
      <div className="pattern-example-header">
        <h3 className="pattern-example-title">{example.title}</h3>
        <p className="pattern-example-description">{example.description}</p>
      </div>
      <div className="pattern-example-preview">
        {example.preview}
      </div>
      <CodeBlock code={example.code} />
    </Card>
  )
}

// ============================================================================
// All Patterns View
// ============================================================================

function AllPatternsView() {
  return (
    <div className="ds-section">
      <header className="ds-section-header">
        <div style={{ marginBottom: '0.5rem' }}>
          <h1 className="ds-section-title" style={{ margin: 0 }}>All Patterns</h1>
        </div>
        <p className="ds-section-description">
          재사용 가능한 UI 패턴과 베스트 프랙티스입니다. 각 패턴을 클릭하여 상세 가이드를 확인하세요.
        </p>
      </header>

      <div className="pattern-grid">
        {Object.entries(PATTERNS).map(([id, info], index) => (
          <motion.div
            key={id}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: index * 0.1 }}
          >
            <PatternCard id={id} info={info} />
          </motion.div>
        ))}
      </div>
    </div>
  )
}

// ============================================================================
// Single Pattern View
// ============================================================================

function SinglePatternView({ patternInfo }: { patternInfo: PatternInfo }) {
  return (
    <div className="ds-section">
      <header className="ds-section-header">
        <div style={{ marginBottom: '0.5rem' }}>
          <h1 className="ds-section-title" style={{ margin: 0 }}>{patternInfo.title}</h1>
        </div>
        <p className="ds-section-description">{patternInfo.description}</p>
      </header>

      {/* Examples Section */}
      <section className="pattern-section">
        <h2 className="pattern-section-title">Examples</h2>
        <div className="pattern-examples-grid">
          {patternInfo.examples.map((example, index) => (
            <motion.div
              key={index}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: index * 0.1 }}
            >
              <PatternExampleCard example={example} />
            </motion.div>
          ))}
        </div>
      </section>

      {/* Best Practices Section */}
      <section className="pattern-section">
        <h2 className="pattern-section-title">Best Practices</h2>
        <Card>
          <ul className="pattern-best-practices">
            {patternInfo.bestPractices.map((practice, index) => (
              <li key={index}>{practice}</li>
            ))}
          </ul>
        </Card>
      </section>
    </div>
  )
}

// ============================================================================
// Main Component
// ============================================================================

export function PatternGuide() {
  const { pattern } = useParams<{ pattern: string }>()

  // pattern 파라미터가 없으면 전체 패턴 목록 표시
  if (!pattern) {
    return <AllPatternsView />
  }

  const patternInfo = PATTERNS[pattern]

  // 존재하지 않는 패턴
  if (!patternInfo) {
    return (
      <div className="ds-placeholder">
        <AlertCircle size={48} className="ds-placeholder-icon" />
        <h2 className="ds-placeholder-title">패턴을 찾을 수 없습니다</h2>
        <p className="ds-placeholder-description">
          요청한 패턴 "{pattern}"을 찾을 수 없습니다.
        </p>
        <Link
          to="/design-system/patterns"
          style={{
            marginTop: '1rem',
            color: 'var(--accent-purple)',
            textDecoration: 'none',
          }}
        >
          ← 전체 패턴 목록으로
        </Link>
      </div>
    )
  }

  return <SinglePatternView patternInfo={patternInfo} />
}
