import { AlertTriangle, CheckCircle2, Clock, Loader2 } from 'lucide-react'

interface StatusBadgeProps {
  status: string
  showIcon?: boolean
}

export function StatusBadge({ status, showIcon = true }: StatusBadgeProps) {
  const statusClass = status.toLowerCase()
  const icon = showIcon ? {
    pending: <Clock size={12} />,
    processing: <Loader2 size={12} className="spin" />,
    processed: <CheckCircle2 size={12} />,
    completed: <CheckCircle2 size={12} />,
    failed: <AlertTriangle size={12} />,
    running: <Loader2 size={12} className="spin" />,
    paused: <Clock size={12} />,
    cancelled: <AlertTriangle size={12} />,
  }[statusClass] || null : null

  return (
    <span className={`badge badge-${statusClass}`}>
      {icon}
      {status}
    </span>
  )
}
