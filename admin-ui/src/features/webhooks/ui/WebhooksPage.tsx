/**
 * Webhooks Page
 *
 * 웹훅 관리 메인 페이지
 */

import { useState } from 'react'
import {
  useRecentDeliveries,
  useSupportedEvents,
  useWebhookMutations,
  useWebhooks,
  useWebhookStats,
} from '../hooks/useWebhooks'
import type { CreateWebhookRequest, TestResult, UpdateWebhookRequest, Webhook } from '../types/webhooks'
import './WebhooksPage.css'

export function WebhooksPage() {
  const { webhooks, loading, error, refetch } = useWebhooks()
  const { stats, refetch: refetchStats } = useWebhookStats()
  const { events } = useSupportedEvents()
  const { deliveries, refetch: refetchDeliveries } = useRecentDeliveries(20)
  const mutations = useWebhookMutations()

  const [showForm, setShowForm] = useState(false)
  const [editingWebhook, setEditingWebhook] = useState<Webhook | null>(null)
  const [testResult, setTestResult] = useState<TestResult | null>(null)
  const [selectedWebhook, setSelectedWebhook] = useState<string | null>(null)

  const handleCreate = () => {
    setEditingWebhook(null)
    setShowForm(true)
  }

  const handleEdit = (webhook: Webhook) => {
    setEditingWebhook(webhook)
    setShowForm(true)
  }

  const handleDelete = async (id: string) => {
    if (!confirm('정말 삭제하시겠습니까?')) return
    const success = await mutations.remove(id)
    if (success) {
      refetch()
      refetchStats()
    }
  }

  const handleTest = async (id: string) => {
    const result = await mutations.test(id)
    setTestResult(result)
    refetchDeliveries()
  }

  const handleSubmit = async (data: CreateWebhookRequest | UpdateWebhookRequest) => {
    if (editingWebhook) {
      const updated = await mutations.update(editingWebhook.id, data as UpdateWebhookRequest)
      if (updated) {
        setShowForm(false)
        refetch()
      }
    } else {
      const created = await mutations.create(data as CreateWebhookRequest)
      if (created) {
        setShowForm(false)
        refetch()
        refetchStats()
      }
    }
  }

  const handleToggleActive = async (webhook: Webhook) => {
    await mutations.update(webhook.id, { isActive: !webhook.isActive })
    refetch()
    refetchStats()
  }

  if (loading) {
    return <div className="webhooks-loading">로딩 중...</div>
  }

  if (error) {
    return <div className="webhooks-error">에러: {error}</div>
  }

  return (
    <div className="webhooks-page">
      <header className="webhooks-header">
        <h1>Webhooks</h1>
        <button className="btn-primary" onClick={handleCreate}>
          + 웹훅 추가
        </button>
      </header>

      {/* Stats Section */}
      {stats && (
        <div className="webhooks-stats">
          <div className="stat-card">
            <div className="stat-value">{stats.webhooks.total}</div>
            <div className="stat-label">전체 웹훅</div>
            <div className="stat-detail">
              활성 {stats.webhooks.active} / 비활성 {stats.webhooks.inactive}
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-value">{stats.deliveries.today}</div>
            <div className="stat-label">오늘 전송</div>
          </div>
          <div className="stat-card">
            <div className="stat-value">{stats.deliveries.successRate.toFixed(1)}%</div>
            <div className="stat-label">성공률</div>
          </div>
          <div className="stat-card">
            <div className="stat-value">
              {stats.deliveries.avgLatencyMs ? `${stats.deliveries.avgLatencyMs.toFixed(0)}ms` : '-'}
            </div>
            <div className="stat-label">평균 지연</div>
          </div>
          <div className="stat-card">
            <div className={`stat-value ${stats.handler.isRunning ? 'running' : 'stopped'}`}>
              {stats.handler.isRunning ? 'Running' : 'Stopped'}
            </div>
            <div className="stat-label">핸들러 상태</div>
          </div>
        </div>
      )}

      {/* Webhook List */}
      <section className="webhooks-list-section">
        <h2>웹훅 목록 ({webhooks.length})</h2>
        {webhooks.length === 0 ? (
          <div className="empty-state">
            등록된 웹훅이 없습니다. 위의 버튼을 눌러 웹훅을 추가하세요.
          </div>
        ) : (
          <div className="webhooks-grid">
            {webhooks.map((webhook) => (
              <div
                key={webhook.id}
                className={`webhook-card ${webhook.isActive ? 'active' : 'inactive'}`}
                onClick={() => setSelectedWebhook(selectedWebhook === webhook.id ? null : webhook.id)}
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
                  <button
                    className="btn-icon"
                    onClick={(e) => {
                      e.stopPropagation()
                      handleToggleActive(webhook)
                    }}
                    title={webhook.isActive ? '비활성화' : '활성화'}
                  >
                    {webhook.isActive ? '⏸' : '▶'}
                  </button>
                  <button
                    className="btn-icon"
                    onClick={(e) => {
                      e.stopPropagation()
                      handleTest(webhook.id)
                    }}
                    title="테스트 전송"
                  >
                    🧪
                  </button>
                  <button
                    className="btn-icon"
                    onClick={(e) => {
                      e.stopPropagation()
                      handleEdit(webhook)
                    }}
                    title="수정"
                  >
                    ✏️
                  </button>
                  <button
                    className="btn-icon danger"
                    onClick={(e) => {
                      e.stopPropagation()
                      handleDelete(webhook.id)
                    }}
                    title="삭제"
                  >
                    🗑
                  </button>
                </div>
                {selectedWebhook === webhook.id && (
                  <div className="webhook-details">
                    <div className="detail-row">
                      <span className="label">ID:</span>
                      <span className="value">{webhook.id}</span>
                    </div>
                    <div className="detail-row">
                      <span className="label">재시도 정책:</span>
                      <span className="value">
                        최대 {webhook.retryPolicy.maxRetries}회, 초기 지연 {webhook.retryPolicy.initialDelayMs}ms
                      </span>
                    </div>
                    <div className="detail-row">
                      <span className="label">시크릿:</span>
                      <span className="value">{webhook.secretToken || '없음'}</span>
                    </div>
                    <div className="detail-row">
                      <span className="label">생성일:</span>
                      <span className="value">{new Date(webhook.createdAt).toLocaleString()}</span>
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Recent Deliveries */}
      <section className="deliveries-section">
        <h2>최근 전송 기록</h2>
        {deliveries.length === 0 ? (
          <div className="empty-state">전송 기록이 없습니다.</div>
        ) : (
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
        )}
      </section>

      {/* Test Result Modal */}
      {testResult && (
        <div className="modal-overlay" onClick={() => setTestResult(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h3>테스트 결과</h3>
            <div className={`test-result ${testResult.success ? 'success' : 'failed'}`}>
              <div className="result-status">
                {testResult.success ? '✅ 성공' : '❌ 실패'}
              </div>
              {testResult.statusCode && (
                <div className="result-detail">
                  <span className="label">응답 코드:</span> {testResult.statusCode}
                </div>
              )}
              {testResult.latencyMs && (
                <div className="result-detail">
                  <span className="label">지연:</span> {testResult.latencyMs}ms
                </div>
              )}
              {testResult.errorMessage && (
                <div className="result-detail error">
                  <span className="label">에러:</span> {testResult.errorMessage}
                </div>
              )}
            </div>
            <button className="btn-secondary" onClick={() => setTestResult(null)}>
              닫기
            </button>
          </div>
        </div>
      )}

      {/* Create/Edit Form Modal */}
      {showForm && (
        <WebhookForm
          webhook={editingWebhook}
          events={events}
          onSubmit={handleSubmit}
          onCancel={() => setShowForm(false)}
          loading={mutations.loading}
        />
      )}
    </div>
  )
}

// ===== Webhook Form Component =====

interface WebhookFormProps {
  webhook: Webhook | null
  events: { name: string; description: string; category: string }[]
  onSubmit: (data: CreateWebhookRequest | UpdateWebhookRequest) => void
  onCancel: () => void
  loading: boolean
}

function WebhookForm({ webhook, events, onSubmit, onCancel, loading }: WebhookFormProps) {
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
    <div className="modal-overlay" onClick={onCancel}>
      <div className="modal-content webhook-form" onClick={(e) => e.stopPropagation()}>
        <h3>{webhook ? '웹훅 수정' : '웹훅 추가'}</h3>
        <form onSubmit={handleSubmit}>
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

          <div className="form-actions">
            <button type="button" className="btn-secondary" onClick={onCancel} disabled={loading}>
              취소
            </button>
            <button type="submit" className="btn-primary" disabled={loading || selectedEvents.length === 0}>
              {loading ? '저장 중...' : webhook ? '수정' : '생성'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
