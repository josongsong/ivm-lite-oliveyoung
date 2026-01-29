import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@/test/utils'
import { FailedTable } from './FailedTable'
import type { FailedOutboxItem } from '@/shared/types'

const mockItems: FailedOutboxItem[] = [
  {
    id: '323e4567-e89b-12d3-a456-426614174002',
    aggregateType: 'ORDER',
    aggregateId: 'ORDER:ORD-001',
    eventType: 'ORDER_FAILED',
    createdAt: '2024-01-15T09:00:00Z',
    retryCount: 3,
    failureReason: 'Connection timeout',
  },
  {
    id: '423e4567-e89b-12d3-a456-426614174003',
    aggregateType: 'PAYMENT',
    aggregateId: 'PAYMENT:PAY-001',
    eventType: 'PAYMENT_FAILED',
    createdAt: '2024-01-15T10:00:00Z',
    retryCount: 5,
    failureReason: null,
  },
]

describe('FailedTable', () => {
  it('실패한 아이템 목록을 테이블로 렌더링한다', () => {
    const onViewDetail = vi.fn()
    const onRetry = vi.fn()
    render(
      <FailedTable
        items={mockItems}
        onViewDetail={onViewDetail}
        onRetry={onRetry}
        retryingId={null}
      />
    )

    expect(screen.getByText('ORDER')).toBeInTheDocument()
    expect(screen.getByText('PAYMENT')).toBeInTheDocument()
    expect(screen.getByText('Connection timeout')).toBeInTheDocument()
  })

  it('retryCount를 표시한다', () => {
    const onViewDetail = vi.fn()
    const onRetry = vi.fn()
    render(
      <FailedTable
        items={mockItems}
        onViewDetail={onViewDetail}
        onRetry={onRetry}
        retryingId={null}
      />
    )

    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText('5')).toBeInTheDocument()
  })

  it('Retry 버튼 클릭 시 onRetry를 호출한다', () => {
    const onViewDetail = vi.fn()
    const onRetry = vi.fn()
    render(
      <FailedTable
        items={mockItems}
        onViewDetail={onViewDetail}
        onRetry={onRetry}
        retryingId={null}
      />
    )

    const retryButtons = screen.getAllByTitle('Retry')
    fireEvent.click(retryButtons[0])

    expect(onRetry).toHaveBeenCalledWith('323e4567-e89b-12d3-a456-426614174002')
  })

  it('retryingId가 일치하면 Retry 버튼이 비활성화된다', () => {
    const onViewDetail = vi.fn()
    const onRetry = vi.fn()
    render(
      <FailedTable
        items={mockItems}
        onViewDetail={onViewDetail}
        onRetry={onRetry}
        retryingId="323e4567-e89b-12d3-a456-426614174002"
      />
    )

    const retryButtons = screen.getAllByTitle('Retry')
    expect(retryButtons[0]).toBeDisabled()
    expect(retryButtons[1]).not.toBeDisabled()
  })

  it('failureReason이 null일 때 "-"를 표시한다', () => {
    const onViewDetail = vi.fn()
    const onRetry = vi.fn()
    render(
      <FailedTable
        items={mockItems}
        onViewDetail={onViewDetail}
        onRetry={onRetry}
        retryingId={null}
      />
    )

    const dashes = screen.getAllByText('-')
    expect(dashes.length).toBeGreaterThan(0)
  })

  it('빈 목록일 때 "실패한 엔트리가 없습니다" 메시지를 표시한다', () => {
    const onViewDetail = vi.fn()
    const onRetry = vi.fn()
    render(
      <FailedTable
        items={[]}
        onViewDetail={onViewDetail}
        onRetry={onRetry}
        retryingId={null}
      />
    )

    expect(screen.getByText('실패한 엔트리가 없습니다')).toBeInTheDocument()
  })
})
