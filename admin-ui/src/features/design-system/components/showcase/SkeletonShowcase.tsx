/**
 * Skeleton Showcase - Skeleton 컴포넌트 전시
 */
import { Skeleton, SkeletonCard, SkeletonList, SkeletonAvatar } from '@/shared/ui'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function SkeletonShowcase() {
  return (
    <div className="showcase">
      <PageHeader
        title="Skeleton"
        description="로딩 중 콘텐츠 구조를 미리 보여주는 스켈레톤 컴포넌트입니다."
        stability="stable"
      />

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>기본 Skeleton</h3>
        <LivePreview>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', width: '100%' }}>
            <Skeleton width="100%" height={20} />
            <Skeleton width="80%" height={20} />
            <Skeleton width="60%" height={20} />
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Circle Variant</h3>
        <LivePreview>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
            <Skeleton variant="circle" size={40} />
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', flex: 1 }}>
              <Skeleton width="60%" height={16} />
              <Skeleton width="40%" height={14} />
            </div>
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Text Variant</h3>
        <LivePreview>
          <Skeleton variant="text" lines={3} />
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>SkeletonCard</h3>
        <LivePreview>
          <SkeletonCard />
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>SkeletonList</h3>
        <LivePreview>
          <SkeletonList items={3} />
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>SkeletonAvatar</h3>
        <LivePreview>
          <div style={{ display: 'flex', gap: '1rem' }}>
            <SkeletonAvatar size="sm" />
            <SkeletonAvatar size="md" />
            <SkeletonAvatar size="lg" />
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`import { Skeleton, SkeletonCard, SkeletonList, SkeletonAvatar } from '@/shared/ui'

<Skeleton width="100%" height={20} />
<Skeleton variant="circle" size={40} />
<Skeleton variant="text" lines={3} />
<SkeletonCard />
<SkeletonList items={3} />
<SkeletonAvatar size="md" />`}
        </pre>
      </section>
    </div>
  )
}
