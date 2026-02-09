import { Tabs, TabsList, TabsTrigger } from '@/shared/ui'

export type RightPanelTab = 'meaning' | 'validation' | 'diff' | 'why' | 'graph' | 'simulate' | 'export'

interface RightPanelTabsProps {
  activeTab: RightPanelTab
  onTabChange: (tab: RightPanelTab) => void
  validationErrorCount?: number
  hasDiffBreaking?: boolean
  diffDisabled?: boolean
}

export function RightPanelTabs({
  activeTab,
  onTabChange,
  validationErrorCount,
  hasDiffBreaking,
  diffDisabled,
}: RightPanelTabsProps) {
  return (
    <Tabs value={activeTab} onValueChange={(v) => onTabChange(v as RightPanelTab)}>
      <TabsList className="contract-editor__tabs">
        <TabsTrigger value="meaning">Meaning</TabsTrigger>
        <TabsTrigger
          value="validation"
          badge={validationErrorCount && validationErrorCount > 0 ? validationErrorCount : undefined}
        >
          Validation
        </TabsTrigger>
        <TabsTrigger value="diff" disabled={diffDisabled} badge={hasDiffBreaking ? '!' : undefined}>
          Diff
        </TabsTrigger>
        <TabsTrigger value="why">Why?</TabsTrigger>
        <TabsTrigger value="graph">Graph</TabsTrigger>
        <TabsTrigger value="simulate">Simulate</TabsTrigger>
        <TabsTrigger value="export">Export</TabsTrigger>
      </TabsList>
    </Tabs>
  )
}
