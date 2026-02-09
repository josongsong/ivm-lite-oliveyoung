import { motion } from 'framer-motion'
import { Activity, AlertTriangle, Clock, TrendingUp, Zap } from 'lucide-react'
import { StatsCard, StatsGrid } from '@/shared/ui'

interface TraceStats {
  totalCount: number
  errorCount: number
  throttleCount: number
  avgDuration: number
  p99Duration: number
  errorRate: number
}

interface TracesStatsCardsProps {
  stats: TraceStats
}

export function TracesStatsCards({ stats }: TracesStatsCardsProps) {
  return (
    <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}>
      <StatsGrid columns={5}>
        <StatsCard
          icon={<Activity size={18} />}
          title="Total Traces"
          value={stats.totalCount}
          valueSize="md"
          animate={false}
        />
        <StatsCard
          icon={<AlertTriangle size={18} />}
          title="Errors"
          value={stats.errorCount}
          subtitle={`${stats.errorRate.toFixed(1)}% error rate`}
          valueSize="md"
          status="error"
          animate={false}
        />
        <StatsCard
          icon={<Zap size={18} />}
          title="Throttled"
          value={stats.throttleCount}
          valueSize="md"
          status="warning"
          animate={false}
        />
        <StatsCard
          icon={<Clock size={18} />}
          title="Avg Duration"
          value={`${stats.avgDuration.toFixed(1)}ms`}
          formatValue={false}
          valueSize="md"
          animate={false}
        />
        <StatsCard
          icon={<TrendingUp size={18} />}
          title="P99 Latency"
          value={`${stats.p99Duration.toFixed(1)}ms`}
          formatValue={false}
          valueSize="md"
          status="info"
          animate={false}
        />
      </StatsGrid>
    </motion.div>
  )
}
