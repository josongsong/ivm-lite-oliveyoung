/**
 * Section Showcase - Section 컴포넌트 전시
 */
import { useState } from 'react'
import { SectionHeader, CollapsibleSection, GroupPanel, Divider } from '@/shared/ui'
import { Info, Database, Settings } from 'lucide-react'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function SectionShowcase() {
  const [expanded, setExpanded] = useState(true)

  return (
    <div className="showcase">
      <PageHeader
        title="Section"
        description="섹션 헤더, 접을 수 있는 섹션, 그룹 패널 등 레이아웃 컴포넌트입니다."
        stability="stable"
      />

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>SectionHeader</h3>
        <LivePreview>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <SectionHeader title="Basic Info" />
            <SectionHeader title="Metadata" icon={<Info size={16} />} count={5} />
            <SectionHeader
              title="Settings"
              subtitle="Configure your preferences"
              icon={<Settings size={16} />}
              actions={<button style={{ fontSize: '0.875rem', color: 'var(--accent-cyan)' }}>Edit</button>}
            />
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>CollapsibleSection</h3>
        <LivePreview>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <CollapsibleSection
              title="Basic Info"
              icon={<Info size={14} />}
              expanded={expanded}
              onExpandedChange={setExpanded}
              count={3}
            >
              <div style={{ padding: '0.5rem 0', fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                Collapsible section content here
              </div>
            </CollapsibleSection>
            <CollapsibleSection
              title="Metadata"
              icon={<Database size={14} />}
              defaultExpanded={false}
              count={12}
            >
              <div style={{ padding: '0.5rem 0', fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                Metadata content here
              </div>
            </CollapsibleSection>
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>GroupPanel</h3>
        <LivePreview>
          <GroupPanel title="Settings" subtitle="Configure your preferences">
            <div style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
              Group panel content here
            </div>
          </GroupPanel>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Divider</h3>
        <LivePreview>
          <div style={{ padding: '1rem', background: 'var(--bg-card)', borderRadius: '8px' }}>
            <div style={{ fontSize: '0.875rem' }}>Content above</div>
            <Divider />
            <div style={{ fontSize: '0.875rem' }}>Content below</div>
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`import { SectionHeader, CollapsibleSection, GroupPanel, Divider } from '@/shared/ui'
import { Info } from 'lucide-react'

<SectionHeader
  title="Settings"
  subtitle="Configure your preferences"
  icon={<Info size={16} />}
  count={5}
/>

<CollapsibleSection
  title="Basic Info"
  icon={<Info size={14} />}
  defaultExpanded={true}
  onToggle={handleToggle}
  count={3}
>
  Content here
</CollapsibleSection>

<GroupPanel title="Settings" subtitle="Configure your preferences">
  Content
</GroupPanel>

<Divider />`}
        </pre>
      </section>
    </div>
  )
}
