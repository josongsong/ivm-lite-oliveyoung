import { GitMerge, RefreshCw } from 'lucide-react'
import { Button, EmptyState, PageHeader } from '@/shared/ui'

interface WorkflowEmptyProps {
  onRetry: () => void
}

export function WorkflowEmpty({ onRetry }: WorkflowEmptyProps) {
  return (
    <div className="workflow-page">
      <PageHeader title="Workflow Canvas" subtitle="데이터 파이프라인을 시각적으로 탐색합니다" />
      <EmptyState
        icon={<GitMerge size={48} />}
        title="백엔드 연결 대기 중"
        description="Admin 서버가 실행되면 워크플로우가 표시됩니다"
        variant="card"
        action={
          <Button variant="primary" onClick={onRetry} icon={<RefreshCw size={14} />}>
            다시 시도
          </Button>
        }
      />
    </div>
  )
}
