import { useMemo } from 'react'
import {
  Area,
  AreaChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { TrendingUp } from 'lucide-react'
import { fetchApi } from '@/shared/api'
import { CHART_CONFIG, QUERY_CONFIG } from '@/shared/config'
import type { HourlyStatsResponse } from '@/shared/types'
import './HourlyChart.css'

export function HourlyChart() {
  const { data, isLoading } = useQuery({
    queryKey: ['outbox-hourly-stats'],
    queryFn: () => fetchApi<HourlyStatsResponse>(`/outbox/stats/hourly?hours=${CHART_CONFIG.HOURLY_STATS_HOURS}`),
    refetchInterval: QUERY_CONFIG.CHART_INTERVAL,
  })

  const chartData = useMemo(() => {
    if (!data?.items) return []
    return data.items.map((item) => ({
      ...item,
      hour: new Date(item.hour).toLocaleTimeString('ko-KR', {
        hour: '2-digit',
        minute: '2-digit',
      }),
      errorRate: item.total > 0 ? (item.failed / item.total) * 100 : 0,
    }))
  }, [data])

  if (isLoading) {
    return (
      <motion.div
        className="hourly-chart loading"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <div className="chart-loading">
          <div className="loading-spinner" />
          <span>차트 로딩 중...</span>
        </div>
      </motion.div>
    )
  }

  // 데이터가 없으면 차트 표시 안함
  if (!chartData.length) {
    return null
  }

  return (
    <motion.div
      className="hourly-chart"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.2 }}
    >
      <div className="chart-header">
        <div className="chart-title">
          <TrendingUp size={20} />
          <h3>시간대별 처리량 (최근 24시간)</h3>
        </div>
      </div>
      <div className="chart-container">
        <ResponsiveContainer width="100%" height={280}>
          <AreaChart data={chartData} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
            <defs>
              <linearGradient id="colorProcessed" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#10b981" stopOpacity={0.8} />
                <stop offset="95%" stopColor="#10b981" stopOpacity={0.1} />
              </linearGradient>
              <linearGradient id="colorFailed" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#ef4444" stopOpacity={0.8} />
                <stop offset="95%" stopColor="#ef4444" stopOpacity={0.1} />
              </linearGradient>
              <linearGradient id="colorPending" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#f59e0b" stopOpacity={0.8} />
                <stop offset="95%" stopColor="#f59e0b" stopOpacity={0.1} />
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
            <XAxis
              dataKey="hour"
              stroke="var(--color-text-secondary)"
              tick={{ fill: 'var(--color-text-secondary)', fontSize: 11 }}
              tickLine={{ stroke: 'var(--color-border)' }}
            />
            <YAxis
              stroke="var(--color-text-secondary)"
              tick={{ fill: 'var(--color-text-secondary)', fontSize: 11 }}
              tickLine={{ stroke: 'var(--color-border)' }}
            />
            <Tooltip
              contentStyle={{
                backgroundColor: 'var(--color-surface)',
                border: '1px solid var(--color-border)',
                borderRadius: '8px',
                boxShadow: '0 4px 12px rgba(0,0,0,0.3)',
              }}
              labelStyle={{ color: 'var(--color-text)', fontWeight: 600 }}
              itemStyle={{ color: 'var(--color-text-secondary)' }}
            />
            <Legend
              wrapperStyle={{ paddingTop: '10px' }}
              iconType="circle"
            />
            <Area
              type="monotone"
              dataKey="processed"
              name="Processed"
              stroke="#10b981"
              strokeWidth={2}
              fillOpacity={1}
              fill="url(#colorProcessed)"
            />
            <Area
              type="monotone"
              dataKey="failed"
              name="Failed"
              stroke="#ef4444"
              strokeWidth={2}
              fillOpacity={1}
              fill="url(#colorFailed)"
            />
            <Area
              type="monotone"
              dataKey="pending"
              name="Pending"
              stroke="#f59e0b"
              strokeWidth={2}
              fillOpacity={1}
              fill="url(#colorPending)"
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </motion.div>
  )
}
