/**
 * Design System Sidebar Component
 *
 * 좌측 네비게이션 메뉴를 제공합니다.
 * - Foundations (Colors, Typography, Spacing, Shadows, Motion)
 * - Components (Actions, Inputs, Navigation, Feedback, Layout, Data Display)
 * - Patterns
 * - Resources
 */
import { Link, useLocation } from 'react-router-dom'
import { motion } from 'framer-motion'
import {
  BookOpen,
  ChevronDown,
  ChevronRight,
  FileCode,
  FileCode2,
  FormInput,
  Layers,
  LayoutGrid,
  MessageSquare,
  MousePointer2,
  Navigation2,
  Palette,
  Repeat,
  Ruler,
  Search,
  Sparkles,
  Table2,
  Type,
  Zap,
  AlertCircle,
  Loader2,
  BarChart3,
  Grid3x3,
  // Features icons
  Package,
  Bell,
  FileEdit,
  ScrollText,
  LayoutDashboard,
  Globe,
  FolderSearch,
  Heart,
  Activity,
  Inbox,
  GitBranch,
  Play,
  Network,
  Webhook,
  Workflow,
} from 'lucide-react'
import { useMemo, useState } from 'react'
import { Button, Input, ThemeSwitcherPanel } from '@/shared/ui'
import './Sidebar.css'

interface NavSection {
  id: string
  label: string
  items: NavItem[]
}

interface NavItem {
  path: string
  label: string
  icon: React.ElementType
  keywords?: string[]
}

const foundations: NavItem[] = [
  { path: '/design-system/foundations/colors', label: 'Colors', icon: Palette, keywords: ['color', 'palette', '색상'] },
  { path: '/design-system/foundations/typography', label: 'Typography', icon: Type, keywords: ['font', 'text', '폰트', '타이포'] },
  { path: '/design-system/foundations/spacing', label: 'Spacing', icon: Ruler, keywords: ['space', 'margin', 'padding', '간격'] },
  { path: '/design-system/foundations/shadows', label: 'Shadows', icon: Layers, keywords: ['shadow', 'elevation', '그림자'] },
  { path: '/design-system/foundations/motion', label: 'Motion', icon: Zap, keywords: ['animation', 'transition', '애니메이션'] },
]

const componentCategories: NavItem[] = [
  { path: '/design-system/components/actions', label: 'Actions', icon: MousePointer2, keywords: ['button', 'action', '버튼'] },
  { path: '/design-system/components/inputs', label: 'Inputs', icon: FormInput, keywords: ['input', 'form', 'select', '입력'] },
  { path: '/design-system/components/navigation', label: 'Navigation', icon: Navigation2, keywords: ['nav', 'tabs', 'menu', '네비게이션'] },
  { path: '/design-system/components/feedback', label: 'Feedback', icon: MessageSquare, keywords: ['modal', 'toast', 'alert', '피드백'] },
  { path: '/design-system/components/layout', label: 'Layout', icon: LayoutGrid, keywords: ['card', 'section', 'grid', '레이아웃'] },
  { path: '/design-system/components/data-display', label: 'Data Display', icon: Table2, keywords: ['table', 'list', 'data', '데이터'] },
]

const patterns: NavItem[] = [
  { path: '/design-system/patterns', label: 'All Patterns', icon: Repeat, keywords: ['pattern', 'guide', '패턴', 'all'] },
  { path: '/design-system/patterns/forms', label: 'Form Patterns', icon: FormInput, keywords: ['form', 'input', 'validation', '폼', '입력'] },
  { path: '/design-system/patterns/errors', label: 'Error Handling', icon: AlertCircle, keywords: ['error', 'handling', '에러', '처리'] },
  { path: '/design-system/patterns/loading', label: 'Loading States', icon: Loader2, keywords: ['loading', 'skeleton', 'spinner', '로딩'] },
  { path: '/design-system/patterns/tables', label: 'Table Patterns', icon: Table2, keywords: ['table', 'data', 'grid', '테이블'] },
  { path: '/design-system/patterns/search', label: 'Search & Filter', icon: Search, keywords: ['search', 'filter', '검색', '필터'] },
  { path: '/design-system/patterns/actions', label: 'Action Buttons', icon: MousePointer2, keywords: ['action', 'button', '액션', '버튼'] },
  { path: '/design-system/patterns/cards', label: 'Card & Panel', icon: Grid3x3, keywords: ['card', 'panel', '카드', '패널'] },
  { path: '/design-system/patterns/stats', label: 'Stats & Metrics', icon: BarChart3, keywords: ['stats', 'metrics', 'statistics', '통계'] },
  { path: '/design-system/patterns/cells', label: 'Table Cell Patterns', icon: Table2, keywords: ['cell', 'table', 'entity', 'version', '셀'] },
  { path: '/design-system/patterns/layouts', label: 'Layout Patterns', icon: LayoutGrid, keywords: ['layout', 'grid', 'container', 'gnb', '레이아웃'] },
  { path: '/design-system/patterns/text-utilities', label: 'Text Utilities', icon: Type, keywords: ['text', 'utility', 'mono', 'truncate', '텍스트'] },
  { path: '/design-system/patterns/navigation', label: 'Navigation Patterns', icon: Navigation2, keywords: ['navigation', 'nav', 'back', 'tabs', '네비게이션'] },
  { path: '/design-system/patterns/editors', label: 'Editor Patterns', icon: FileCode, keywords: ['editor', 'form', 'json', '에디터'] },
]

const resources: NavItem[] = [
  { path: '/design-system/resources/getting-started', label: 'Getting Started', icon: BookOpen, keywords: ['start', 'guide', '시작'] },
  { path: '/design-system/resources/changelog', label: 'Changelog', icon: FileCode2, keywords: ['change', 'version', '변경'] },
]

const features: NavItem[] = [
  { path: '/design-system/features', label: 'All Features', icon: Package, keywords: ['feature', 'all', '전체'] },
  { path: '/design-system/features/alerts', label: 'Alerts', icon: Bell, keywords: ['alert', 'notification', '알림'] },
  { path: '/design-system/features/contract-editor', label: 'Contract Editor', icon: FileEdit, keywords: ['contract', 'editor', '계약', '에디터'] },
  { path: '/design-system/features/contracts', label: 'Contracts', icon: ScrollText, keywords: ['contract', '계약'] },
  { path: '/design-system/features/dashboard', label: 'Dashboard', icon: LayoutDashboard, keywords: ['dashboard', '대시보드'] },
  { path: '/design-system/features/environment', label: 'Environment', icon: Globe, keywords: ['environment', 'env', '환경'] },
  { path: '/design-system/features/explorer', label: 'Explorer', icon: FolderSearch, keywords: ['explorer', 'search', '탐색'] },
  { path: '/design-system/features/health', label: 'Health', icon: Heart, keywords: ['health', 'status', '헬스'] },
  { path: '/design-system/features/observability', label: 'Observability', icon: Activity, keywords: ['observability', 'monitoring', '모니터링'] },
  { path: '/design-system/features/outbox', label: 'Outbox', icon: Inbox, keywords: ['outbox', 'queue', '아웃박스'] },
  { path: '/design-system/features/pipeline', label: 'Pipeline', icon: GitBranch, keywords: ['pipeline', 'flow', '파이프라인'] },
  { path: '/design-system/features/playground', label: 'Playground', icon: Play, keywords: ['playground', 'test', '플레이그라운드'] },
  { path: '/design-system/features/traces', label: 'Traces', icon: Network, keywords: ['trace', 'tracing', '트레이스'] },
  { path: '/design-system/features/webhooks', label: 'Webhooks', icon: Webhook, keywords: ['webhook', '웹훅'] },
  { path: '/design-system/features/workflow', label: 'Workflow', icon: Workflow, keywords: ['workflow', 'canvas', '워크플로우'] },
]

const navSections: NavSection[] = [
  { id: 'foundations', label: 'Foundations', items: foundations },
  { id: 'components', label: 'Components', items: componentCategories },
  { id: 'patterns', label: 'Patterns', items: patterns },
  { id: 'features', label: 'Features', items: features },
  { id: 'resources', label: 'Resources', items: resources },
]

export function Sidebar() {
  const location = useLocation()
  const [expandedSections, setExpandedSections] = useState<Set<string>>(
    new Set(['foundations', 'components', 'patterns'])
  )
  const [searchQuery, setSearchQuery] = useState('')

  const toggleSection = (sectionId: string) => {
    setExpandedSections((prev) => {
      const next = new Set(prev)
      if (next.has(sectionId)) {
        next.delete(sectionId)
      } else {
        next.add(sectionId)
      }
      return next
    })
  }

  const filteredSections = useMemo(() => {
    if (!searchQuery.trim()) return navSections

    const query = searchQuery.toLowerCase()
    return navSections
      .map((section) => ({
        ...section,
        items: section.items.filter(
          (item) =>
            item.label.toLowerCase().includes(query) ||
            item.keywords?.some((kw) => kw.toLowerCase().includes(query))
        ),
      }))
      .filter((section) => section.items.length > 0)
  }, [searchQuery])

  return (
    <aside className="ds-sidebar">
      <div className="ds-sidebar-header">
        <Link to="/design-system" className="ds-sidebar-logo">
          <Sparkles size={20} className="ds-logo-icon" />
          <span className="ds-logo-title">Design System</span>
        </Link>
      </div>

      <div className="ds-sidebar-search">
        <Input
          type="text"
          placeholder="Search..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          leftIcon={<Search size={16} />}
          size="sm"
        />
      </div>

      <nav className="ds-sidebar-nav">
        {filteredSections.map((section) => {
          const isExpanded = expandedSections.has(section.id) || searchQuery.trim() !== ''
          return (
            <div key={section.id} className="ds-nav-section">
              <Button
                variant="ghost"
                className="ds-nav-section-header"
                onClick={() => toggleSection(section.id)}
                aria-expanded={isExpanded}
              >
                <span className="ds-nav-section-label">{section.label}</span>
                {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
              </Button>

              {isExpanded ? <motion.div
                  className="ds-nav-section-items"
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  exit={{ opacity: 0, height: 0 }}
                  transition={{ duration: 0.2 }}
                >
                  {section.items.map((item) => {
                    const isActive = location.pathname === item.path ||
                      location.pathname.startsWith(item.path + '/')
                    const Icon = item.icon

                    return (
                      <Link
                        key={item.path}
                        to={item.path}
                        className={`ds-nav-item ${isActive ? 'active' : ''}`}
                      >
                        <Icon size={16} />
                        <span>{item.label}</span>
                        {isActive ? <motion.div
                            className="ds-nav-indicator"
                            layoutId="ds-nav-indicator"
                            transition={{ type: 'spring', stiffness: 500, damping: 30 }}
                          /> : null}
                      </Link>
                    )
                  })}
                </motion.div> : null}
            </div>
          )
        })}
      </nav>

      <div className="ds-sidebar-footer">
        <ThemeSwitcherPanel />
        <Link to="/dashboard" className="ds-back-link">
          ← Back to Admin
        </Link>
      </div>
    </aside>
  )
}
