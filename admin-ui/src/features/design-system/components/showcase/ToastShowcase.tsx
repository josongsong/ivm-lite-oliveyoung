/**
 * Toast Showcase - Toast 컴포넌트 전시
 */
import { toast, ToastContainer, Button } from '@/shared/ui'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function ToastShowcase() {
  const showToast = (variant: 'success' | 'error' | 'info' | 'warning') => {
    switch (variant) {
      case 'success':
        toast.success('작업이 성공적으로 완료되었습니다!')
        break
      case 'error':
        toast.error('오류가 발생했습니다. 다시 시도해주세요.')
        break
      case 'info':
        toast.info('새로운 업데이트가 있습니다.')
        break
      case 'warning':
        toast.warning('주의: 이 작업은 되돌릴 수 없습니다.')
        break
    }
  }

  return (
    <div className="showcase">
      <PageHeader
        title="Toast"
        description="일시적인 알림을 표시하는 토스트 컴포넌트입니다."
        stability="stable"
      />

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Toast Variants</h3>
        <LivePreview>
          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            <Button variant="primary" size="sm" onClick={() => showToast('success')}>
              Success Toast
            </Button>
            <Button variant="danger" size="sm" onClick={() => showToast('error')}>
              Error Toast
            </Button>
            <Button variant="secondary" size="sm" onClick={() => showToast('info')}>
              Info Toast
            </Button>
            <Button variant="secondary" size="sm" onClick={() => showToast('warning')}>
              Warning Toast
            </Button>
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>ToastContainer</h3>
        <LivePreview>
          <div style={{ padding: '1rem', background: 'var(--bg-secondary)', borderRadius: '8px' }}>
            <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', marginBottom: '1rem' }}>
              ToastContainer는 앱의 루트 레벨에 한 번만 배치합니다.
            </p>
            <Button variant="primary" size="sm" onClick={() => showToast('success')}>
              Show Toast
            </Button>
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`import { toast, ToastContainer } from '@/shared/ui'

// 앱 루트에 ToastContainer 배치
function App() {
  return (
    <>
      <YourApp />
      <ToastContainer />
    </>
  )
}

// 사용 예시
toast.success('작업이 성공적으로 완료되었습니다!')
toast.error('오류가 발생했습니다.')
toast.info('새로운 업데이트가 있습니다.')
toast.warning('주의: 이 작업은 되돌릴 수 없습니다.')`}
        </pre>
      </section>

      {/* ToastContainer는 실제로 렌더링 */}
      <ToastContainer />
    </div>
  )
}
