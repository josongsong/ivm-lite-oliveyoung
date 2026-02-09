import type { NodeDetailResponse } from '../../model/types'
import { formatTime } from './utils'

interface RecentActivitySectionProps {
  activities: NonNullable<NodeDetailResponse['recentActivity']>
}

export function RecentActivitySection({ activities }: RecentActivitySectionProps) {
  if (activities.length === 0) return null

  return (
    <section className="panel-section">
      <h4 className="section-title">최근 활동</h4>
      <div className="activity-list">
        {activities.slice(0, 5).map((activity) => {
          if (!activity) return null
          return (
            <div key={`activity-${activity.timestamp}-${activity.action}`} className="activity-item">
              <span className="activity-time">
                {activity.timestamp ? formatTime(activity.timestamp) : 'N/A'}
              </span>
              <span className="activity-action">{activity.action || 'Unknown'}</span>
              <span className="activity-details">{activity.details || ''}</span>
            </div>
          )
        })}
      </div>
    </section>
  )
}
