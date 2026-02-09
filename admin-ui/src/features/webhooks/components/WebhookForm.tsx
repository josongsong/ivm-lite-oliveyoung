import { useState } from 'react'
import { Button, Modal } from '@/shared/ui'
import type { CreateWebhookRequest, UpdateWebhookRequest, Webhook } from '../types/webhooks'

interface WebhookFormProps {
  webhook: Webhook | null
  events: { name: string; description: string; category: string }[]
  onSubmit: (data: CreateWebhookRequest | UpdateWebhookRequest) => void
  onCancel: () => void
  loading: boolean
}

export function WebhookForm({ webhook, events, onSubmit, onCancel, loading }: WebhookFormProps) {
  const [name, setName] = useState(webhook?.name || '')
  const [url, setUrl] = useState(webhook?.url || '')
  const [selectedEvents, setSelectedEvents] = useState<string[]>(webhook?.events || [])
  const [secretToken, setSecretToken] = useState('')
  const [maxRetries, setMaxRetries] = useState(webhook?.retryPolicy.maxRetries || 5)

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    onSubmit({
      name,
      url,
      events: selectedEvents,
      retryPolicy: {
        maxRetries,
        initialDelayMs: 1000,
        maxDelayMs: 60000,
        multiplier: 2.0,
      },
      secretToken: secretToken || undefined,
    })
  }

  const toggleEvent = (eventName: string) => {
    setSelectedEvents((prev) =>
      prev.includes(eventName) ? prev.filter((e) => e !== eventName) : [...prev, eventName]
    )
  }

  const groupedEvents = events.reduce(
    (acc, event) => {
      if (!acc[event.category]) acc[event.category] = []
      acc[event.category].push(event)
      return acc
    },
    {} as Record<string, typeof events>
  )

  return (
    <Modal
      isOpen={true}
      onClose={onCancel}
      title={webhook ? '웹훅 수정' : '웹훅 추가'}
      size="md"
      className="webhook-form-modal"
      footer={
        <>
          <Button variant="secondary" onClick={onCancel} disabled={loading}>
            취소
          </Button>
          <Button
            type="submit"
            form="webhook-form"
            variant="primary"
            loading={loading}
            disabled={selectedEvents.length === 0}
          >
            {webhook ? '수정' : '생성'}
          </Button>
        </>
      }
    >
      <form id="webhook-form" onSubmit={handleSubmit} className="webhook-form">
        <div className="form-group">
          <label htmlFor="name">이름</label>
          <input
            id="name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Webhook Name"
            required
          />
        </div>

        <div className="form-group">
          <label htmlFor="url">URL</label>
          <input
            id="url"
            type="url"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://example.com/webhook"
            required
          />
        </div>

        <div className="form-group">
          <label>이벤트</label>
          <div className="events-selector">
            {Object.entries(groupedEvents).map(([category, categoryEvents]) => (
              <div key={category} className="event-category">
                <div className="category-label">{category}</div>
                <div className="category-events">
                  {categoryEvents.map((event) => (
                    <label key={event.name} className="event-checkbox">
                      <input
                        type="checkbox"
                        checked={selectedEvents.includes(event.name)}
                        onChange={() => toggleEvent(event.name)}
                      />
                      <span className="event-name">{event.name}</span>
                      <span className="event-desc">{event.description}</span>
                    </label>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="form-group">
          <label htmlFor="maxRetries">최대 재시도 횟수</label>
          <input
            id="maxRetries"
            type="number"
            value={maxRetries}
            onChange={(e) => setMaxRetries(parseInt(e.target.value))}
            min={0}
            max={10}
          />
        </div>

        <div className="form-group">
          <label htmlFor="secretToken">시크릿 토큰 (HMAC 서명용)</label>
          <input
            id="secretToken"
            type="password"
            value={secretToken}
            onChange={(e) => setSecretToken(e.target.value)}
            placeholder="선택사항"
          />
        </div>
      </form>
    </Modal>
  )
}
