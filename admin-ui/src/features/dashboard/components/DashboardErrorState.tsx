/**
 * DashboardErrorState Component
 *
 * Dashboard 에러 상태 표시
 */
import { ApiError, PageHeader } from '@/shared/ui'

interface DashboardErrorStateProps {
  onRetry: () => void
}

export function DashboardErrorState({ onRetry }: DashboardErrorStateProps) {
  return (
    <div className="page-container">
      <PageHeader title="Dashboard" subtitle="IVM Lite 시스템 상태 실시간 모니터링" />
      <ApiError
        title="백엔드 연결 대기 중"
        message="Admin 서버가 실행되면 대시보드가 표시됩니다"
        onRetry={onRetry}
      />
    </div>
  )
}
