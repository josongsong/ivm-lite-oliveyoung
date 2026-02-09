/**
 * Component Detail - 개별 컴포넌트 상세 페이지
 *
 * Phase 3: 실제 컴포넌트 전시 구현
 * 컴포넌트 이름에 따라 해당 Showcase 컴포넌트 렌더링
 */

import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Construction } from 'lucide-react'
import {
  AccordionShowcase,
  AlertShowcase,
  ButtonShowcase,
  CardShowcase,
  ChipShowcase,
  IconButtonShowcase,
  InputShowcase,
  LabelShowcase,
  LoadingShowcase,
  ModalShowcase,
  PaginationShowcase,
  SelectShowcase,
  StatusBadgeShowcase,
  SwitchShowcase,
  TableShowcase,
  TabsShowcase,
  TextAreaShowcase,
  TooltipShowcase,
  FormShowcase,
  EmptyStateShowcase,
  SkeletonShowcase,
  JsonViewerShowcase,
  DiffViewerShowcase,
  PageHeaderShowcase,
  InfoRowShowcase,
  SearchBarShowcase,
  FileUploadShowcase,
  SchemaSelectorShowcase,
  SearchFilterShowcase,
  TableHeaderShowcase,
  PanelHeaderShowcase,
  ActionCardShowcase,
  StatCardShowcase,
  LineageGraphShowcase,
  SectionShowcase,
  ToastShowcase,
} from '../components/showcase'

// ============================================================================
// Showcase Component Registry
// ============================================================================

const SHOWCASE_REGISTRY: Record<string, React.ComponentType> = {
  // Actions
  button: ButtonShowcase,
  iconbutton: IconButtonShowcase,
  // Inputs
  input: InputShowcase,
  textarea: TextAreaShowcase,
  select: SelectShowcase,
  switch: SwitchShowcase,
  togglegroup: SwitchShowcase, // ToggleGroup은 Switch의 변형
  fileupload: FileUploadShowcase,
  schemaselector: SchemaSelectorShowcase,
  searchfilter: SearchFilterShowcase,
  // Form Components
  form: FormShowcase,
  formrow: FormShowcase,
  formgroup: FormShowcase,
  forminput: FormShowcase,
  formtextarea: FormShowcase,
  // Feedback
  loading: LoadingShowcase,
  modal: ModalShowcase,
  alert: AlertShowcase,
  banner: AlertShowcase, // Banner는 Alert의 변형이므로 AlertShowcase 사용
  inlinealert: AlertShowcase, // InlineAlert도 Alert의 변형
  tooltip: TooltipShowcase,
  emptystate: EmptyStateShowcase,
  noresults: EmptyStateShowcase, // NoResults는 EmptyState의 변형
  nodata: EmptyStateShowcase, // NoData도 EmptyState의 변형
  errorstate: EmptyStateShowcase, // ErrorState도 EmptyState의 변형
  loadingstate: LoadingShowcase, // LoadingState는 Loading의 변형
  skeleton: SkeletonShowcase,
  toast: ToastShowcase,
  // Layout
  card: CardShowcase,
  statscard: CardShowcase, // StatsCard는 Card의 변형
  statsgrid: CardShowcase, // StatsGrid도 Card의 변형
  breakdownitem: CardShowcase, // BreakdownItem도 Card의 변형
  accordion: AccordionShowcase,
  collapsiblesection: SectionShowcase, // CollapsibleSection은 Section 컴포넌트
  pageheader: PageHeaderShowcase,
  panelheader: PanelHeaderShowcase,
  tableheader: TableHeaderShowcase,
  section: SectionShowcase, // Section 컴포넌트
  sectionheader: SectionShowcase, // SectionHeader는 Section 컴포넌트
  grouppanel: SectionShowcase, // GroupPanel은 Section 컴포넌트
  actioncard: ActionCardShowcase,
  divider: SectionShowcase, // Divider는 Section 컴포넌트
  // Data Display
  table: TableShowcase,
  statusbadge: StatusBadgeShowcase,
  chip: ChipShowcase,
  chipgroup: ChipShowcase, // ChipGroup은 Chip의 변형
  label: LabelShowcase,
  jsonviewer: JsonViewerShowcase,
  diffviewer: DiffViewerShowcase,
  yamlviewer: JsonViewerShowcase, // YamlViewer는 JsonViewer와 유사
  searchbar: SearchBarShowcase,
  lineagegraph: LineageGraphShowcase,
  statcard: StatCardShowcase,
  inforow: InfoRowShowcase,
  // Navigation
  tabs: TabsShowcase,
  pagination: PaginationShowcase,
}

// ============================================================================
// Fallback Placeholder
// ============================================================================

function PlaceholderShowcase({ componentName }: { componentName: string }) {
  return (
    <div className="ds-placeholder" style={{ minHeight: '400px' }}>
      <Construction size={48} className="ds-placeholder-icon" />
      <h2 className="ds-placeholder-title">{componentName} Showcase</h2>
      <p className="ds-placeholder-description">
        이 컴포넌트의 Showcase는 아직 구현되지 않았습니다.
        Phase 3에서 순차적으로 구현될 예정입니다.
      </p>
    </div>
  )
}

// ============================================================================
// Main Component
// ============================================================================

export function ComponentDetail() {
  const { category, name } = useParams<{ category: string; name: string }>()

  const componentName = name ? name.charAt(0).toUpperCase() + name.slice(1) : ''
  const ShowcaseComponent = name ? SHOWCASE_REGISTRY[name.toLowerCase()] : null

  return (
    <div className="ds-section">
      {/* Breadcrumb */}
      <nav style={{ marginBottom: '1.5rem' }}>
        <Link
          to={`/design-system/components/${category}`}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '0.5rem',
            color: 'var(--text-secondary)',
            textDecoration: 'none',
            fontSize: '0.875rem',
          }}
        >
          <ArrowLeft size={16} />
          <span>Back to {category}</span>
        </Link>
      </nav>

      {/* Render Showcase or Placeholder */}
      {ShowcaseComponent ? (
        <ShowcaseComponent />
      ) : (
        <PlaceholderShowcase componentName={componentName} />
      )}
    </div>
  )
}
