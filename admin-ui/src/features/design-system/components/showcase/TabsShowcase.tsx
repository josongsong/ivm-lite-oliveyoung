/**
 * Tabs Showcase
 */
import { useState } from 'react'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function TabsShowcase() {
  const [activeTab, setActiveTab] = useState('tab1')

  return (
    <div className="showcase">
      <PageHeader
        title="Tabs"
        description="탭 기반 네비게이션 컴포넌트입니다."
        stability="stable"
      />

      <LivePreview>
        <Tabs value={activeTab} onValueChange={setActiveTab}>
          <TabsList>
            <TabsTrigger value="tab1">Overview</TabsTrigger>
            <TabsTrigger value="tab2">Features</TabsTrigger>
            <TabsTrigger value="tab3">Settings</TabsTrigger>
          </TabsList>
          <TabsContent value="tab1">
            <div style={{ padding: '1rem' }}>
              <h4 style={{ marginBottom: '0.5rem' }}>Overview</h4>
              <p style={{ color: 'var(--text-secondary)', margin: 0 }}>
                This is the overview tab content. Tabs help organize content into separate views.
              </p>
            </div>
          </TabsContent>
          <TabsContent value="tab2">
            <div style={{ padding: '1rem' }}>
              <h4 style={{ marginBottom: '0.5rem' }}>Features</h4>
              <ul style={{ color: 'var(--text-secondary)', margin: 0, paddingLeft: '1.5rem' }}>
                <li>Keyboard navigation support</li>
                <li>Accessible by default</li>
                <li>Customizable styling</li>
              </ul>
            </div>
          </TabsContent>
          <TabsContent value="tab3">
            <div style={{ padding: '1rem' }}>
              <h4 style={{ marginBottom: '0.5rem' }}>Settings</h4>
              <p style={{ color: 'var(--text-secondary)', margin: 0 }}>
                Configure your preferences here.
              </p>
            </div>
          </TabsContent>
        </Tabs>
      </LivePreview>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Many Tabs (Scrollable)</h3>
        <LivePreview>
          <Tabs value={activeTab} onValueChange={setActiveTab}>
            <TabsList scrollable showScrollIndicators>
              <TabsTrigger value="tab1">Overview</TabsTrigger>
              <TabsTrigger value="tab2">Features</TabsTrigger>
              <TabsTrigger value="tab3">Settings</TabsTrigger>
              <TabsTrigger value="tab4">Analytics</TabsTrigger>
              <TabsTrigger value="tab5">Reports</TabsTrigger>
              <TabsTrigger value="tab6">Users</TabsTrigger>
              <TabsTrigger value="tab7">Permissions</TabsTrigger>
              <TabsTrigger value="tab8">Integrations</TabsTrigger>
            </TabsList>
            <TabsContent value={activeTab}>
              <div style={{ padding: '1rem' }}>
                <p style={{ color: 'var(--text-secondary)', margin: 0 }}>
                  많은 탭이 있을 때 자동으로 가로 스크롤이 활성화됩니다.
                </p>
              </div>
            </TabsContent>
          </Tabs>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Responsive Behavior</h3>
        <div style={{ 
          background: 'var(--bg-tertiary)', 
          padding: '1rem', 
          borderRadius: '8px',
          fontSize: '0.875rem',
          color: 'var(--text-secondary)'
        }}>
          <p style={{ margin: '0 0 0.5rem 0' }}>
            <strong>작은 화면 (640px 이하):</strong>
          </p>
          <ul style={{ margin: '0 0 1rem 0', paddingLeft: '1.5rem' }}>
            <li>탭 패딩이 줄어듭니다 (0.5rem 0.75rem)</li>
            <li>폰트 크기가 작아집니다 (0.8125rem)</li>
            <li>긴 탭 레이블은 ellipsis로 처리됩니다</li>
            <li>가로 스크롤이 자동으로 활성화됩니다</li>
          </ul>
          <p style={{ margin: 0 }}>
            브라우저 창 크기를 조절하여 반응형 동작을 확인하세요.
          </p>
        </div>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`const [tab, setTab] = useState('tab1')

<Tabs value={tab} onValueChange={setTab}>
  <TabsList scrollable showScrollIndicators>
    <TabsTrigger value="tab1">Tab 1</TabsTrigger>
    <TabsTrigger value="tab2">Tab 2</TabsTrigger>
  </TabsList>
  <TabsContent value="tab1">
    Content for tab 1
  </TabsContent>
  <TabsContent value="tab2">
    Content for tab 2
  </TabsContent>
</Tabs>`}
        </pre>
      </section>
    </div>
  )
}
