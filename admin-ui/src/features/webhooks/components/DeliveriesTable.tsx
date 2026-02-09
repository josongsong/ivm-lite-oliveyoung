import type { WebhookDelivery } from '../types/webhooks'
import { NoData } from '@/shared/ui'

interface DeliveriesTableProps {
  deliveries: WebhookDelivery[]
}

export function DeliveriesTable({ deliveries }: DeliveriesTableProps) {
  if (deliveries.length === 0) {
    return <NoData message="전송 기록이 없습니다" variant="compact" />
  }

  return (
    <table className="deliveries-table">
      <thead>
        <tr>
          <th>시간</th>
          <th>이벤트</th>
          <th>상태</th>
          <th>응답 코드</th>
          <th>지연</th>
        </tr>
      </thead>
      <tbody>
        {deliveries.map((delivery) => (
          <tr key={delivery.id} className={`status-${delivery.status.toLowerCase()}`}>
            <td>{new Date(delivery.createdAt).toLocaleString()}</td>
            <td>{delivery.eventType}</td>
            <td>
              <span className={`status-badge ${delivery.status.toLowerCase()}`}>
                {delivery.status}
              </span>
            </td>
            <td>{delivery.responseStatus || '-'}</td>
            <td>{delivery.latencyMs ? `${delivery.latencyMs}ms` : '-'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
