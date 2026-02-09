import { Edit2, Pause, Play, TestTube, Trash2 } from 'lucide-react'
import { IconButton, InfoRow } from '@/shared/ui'
import type { Webhook } from '../types/webhooks'

interface WebhookCardProps {
  webhook: Webhook
  isSelected: boolean
  onSelect: () => void
  onToggleActive: () => void
  onTest: () => void
  onEdit: () => void
  onDelete: () => void
}

export function WebhookCard({
  webhook,
  isSelected,
  onSelect,
  onToggleActive,
  onTest,
  onEdit,
  onDelete,
}: WebhookCardProps) {
  return (
    <div
      role="button"
      tabIndex={0}
      className={`webhook-card ${webhook.isActive ? 'active' : 'inactive'}`}
      onClick={onSelect}
      onKeyDown={(e) => e.key === 'Enter' && onSelect?.()}
    >
      <div className="webhook-header">
        <span className={`status-dot ${webhook.isActive ? 'active' : 'inactive'}`} />
        <h3>{webhook.name}</h3>
      </div>
      <div className="webhook-url">{webhook.url}</div>
      <div className="webhook-events">
        {webhook.events.slice(0, 3).map((event) => (
          <span key={event} className="event-tag">
            {event}
          </span>
        ))}
        {webhook.events.length > 3 && (
          <span className="event-tag more">+{webhook.events.length - 3}</span>
        )}
      </div>
      <div className="webhook-actions">
        <IconButton
          icon={webhook.isActive ? Pause : Play}
          onClick={(e) => {
            e.stopPropagation()
            onToggleActive()
          }}
          tooltip={webhook.isActive ? '비활성화' : '활성화'}
          variant={webhook.isActive ? 'warning' : 'success'}
          size="sm"
        />
        <IconButton
          icon={TestTube}
          onClick={(e) => {
            e.stopPropagation()
            onTest()
          }}
          tooltip="테스트 전송"
          variant="primary"
          size="sm"
        />
        <IconButton
          icon={Edit2}
          onClick={(e) => {
            e.stopPropagation()
            onEdit()
          }}
          tooltip="수정"
          size="sm"
        />
        <IconButton
          icon={Trash2}
          onClick={(e) => {
            e.stopPropagation()
            onDelete()
          }}
          tooltip="삭제"
          variant="danger"
          size="sm"
        />
      </div>
      {isSelected ? (
        <div className="webhook-details">
          <InfoRow label="ID" value={webhook.id} mono copyable />
          <InfoRow
            label="재시도 정책"
            value={`최대 ${webhook.retryPolicy.maxRetries}회, 초기 지연 ${webhook.retryPolicy.initialDelayMs}ms`}
          />
          <InfoRow label="시크릿" value={webhook.secretToken || '없음'} />
          <InfoRow label="생성일" value={new Date(webhook.createdAt).toLocaleString()} />
        </div>
      ) : null}
    </div>
  )
}
