import { Activity, CheckCircle, Clock, Send, Webhook } from 'lucide-react'
import type { WebhookStats } from '../types/webhooks'
import { StatsCard, StatsGrid } from '@/shared/ui'

interface WebhookStatsSectionProps {
  stats: WebhookStats
}

export function WebhookStatsSection({ stats }: WebhookStatsSectionProps) {
  return (
    <StatsGrid columns={5}>
      <StatsCard
        icon={<Webhook size={18} />}
        title="전체 웹훅"
        value={stats.webhooks.total}
        subtitle={`활성 ${stats.webhooks.active} / 비활성 ${stats.webhooks.inactive}`}
        valueSize="md"
      />
      <StatsCard
        icon={<Send size={18} />}
        title="오늘 전송"
        value={stats.deliveries.today}
        valueSize="md"
      />
      <StatsCard
        icon={<CheckCircle size={18} />}
        title="성공률"
        value={`${stats.deliveries.successRate.toFixed(1)}%`}
        formatValue={false}
        valueSize="md"
        status={stats.deliveries.successRate >= 95 ? 'success' : stats.deliveries.successRate >= 80 ? 'warning' : 'error'}
      />
      <StatsCard
        icon={<Clock size={18} />}
        title="평균 지연"
        value={stats.deliveries.avgLatencyMs ? `${stats.deliveries.avgLatencyMs.toFixed(0)}ms` : '-'}
        formatValue={false}
        valueSize="md"
      />
      <StatsCard
        icon={<Activity size={18} />}
        title="핸들러 상태"
        value={stats.handler.isRunning ? 'Running' : 'Stopped'}
        formatValue={false}
        valueSize="md"
        status={stats.handler.isRunning ? 'success' : 'error'}
      />
    </StatsGrid>
  )
}
