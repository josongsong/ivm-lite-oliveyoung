/**
 * StatusBadge Component
 *
 * 상태 배지 컴포넌트:
 * - 다양한 상태값 지원 (healthy, pending, failed 등)
 * - 상태별 아이콘 자동 표시
 * - 색상 자동 매핑
 *
 * @example
 * ```tsx
 * <StatusBadge status="healthy" />
 * <StatusBadge status="pending" />
 * <StatusBadge status="failed" showIcon={false} />
 * ```
 */
import { AlertTriangle, CheckCircle2, Clock, Loader2, XCircle } from 'lucide-react'

export interface StatusBadgeProps {
  /** Status value */
  status: string
  /** Show status icon */
  showIcon?: boolean
}

// 상태값을 badge 클래스로 매핑
const statusClassMap: Record<string, string> = {
  // Health 상태
  healthy: 'success',
  degraded: 'warning',
  unhealthy: 'error',
  unknown: 'warning',
  // 일반 상태
  pending: 'pending',
  processing: 'processing',
  processed: 'processed',
  completed: 'completed',
  failed: 'failed',
  running: 'running',
  paused: 'paused',
  cancelled: 'cancelled',
}

export function StatusBadge({ status, showIcon = true }: StatusBadgeProps) {
  const statusLower = status.toLowerCase()
  const badgeClass = statusClassMap[statusLower] || statusLower

  const icon = showIcon ? {
    // Health 상태 아이콘
    healthy: <CheckCircle2 size={12} />,
    degraded: <AlertTriangle size={12} />,
    unhealthy: <XCircle size={12} />,
    unknown: <AlertTriangle size={12} />,
    // 일반 상태 아이콘
    pending: <Clock size={12} />,
    processing: <Loader2 size={12} className="spin" />,
    processed: <CheckCircle2 size={12} />,
    completed: <CheckCircle2 size={12} />,
    failed: <AlertTriangle size={12} />,
    running: <Loader2 size={12} className="spin" />,
    paused: <Clock size={12} />,
    cancelled: <AlertTriangle size={12} />,
  }[statusLower] || null : null

  return (
    <span className={`badge badge-${badgeClass}`}>
      {icon}
      {status}
    </span>
  )
}
