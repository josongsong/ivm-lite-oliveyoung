import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@/test/utils'
import { RecentTable } from './RecentTable'
import type { OutboxItem } from '@/shared/types'

const mockItems: OutboxItem[] = [
  {
    id: '123e4567-e89b-12d3-a456-426614174000',
    aggregateType: 'PRODUCT',
    aggregateId: 'PRODUCT:SKU-001',
    eventType: 'PRODUCT_UPDATED',
    status: 'PROCESSED',
    createdAt: '2024-01-15T10:30:00Z',
    processedAt: '2024-01-15T10:30:05Z',
    retryCount: 0,
  },
  {
    id: '223e4567-e89b-12d3-a456-426614174001',
    aggregateType: 'ORDER',
    aggregateId: 'ORDER:ORD-001',
    eventType: 'ORDER_CREATED',
    status: 'PENDING',
    createdAt: '2024-01-15T10:35:00Z',
    processedAt: null,
    retryCount: 0,
  },
]

describe('RecentTable', () => {
  it('아이템 목록을 테이블로 렌더링한다', () => {
    const onViewDetail = vi.fn()
    render(<RecentTable items={mockItems} onViewDetail={onViewDetail} />)

    // 테이블 헤더 확인
    expect(screen.getByText('ID')).toBeInTheDocument()
    expect(screen.getByText('Type')).toBeInTheDocument()
    expect(screen.getByText('Status')).toBeInTheDocument()

    // 데이터 행 확인
    expect(screen.getByText('PRODUCT')).toBeInTheDocument()
    expect(screen.getByText('ORDER')).toBeInTheDocument()
    expect(screen.getByText('PRODUCT_UPDATED')).toBeInTheDocument()
    expect(screen.getByText('ORDER_CREATED')).toBeInTheDocument()
  })

  it('상태 배지를 올바르게 표시한다', () => {
    const onViewDetail = vi.fn()
    render(<RecentTable items={mockItems} onViewDetail={onViewDetail} />)

    expect(screen.getByText('PROCESSED')).toBeInTheDocument()
    expect(screen.getByText('PENDING')).toBeInTheDocument()
  })

  it('View Detail 버튼 클릭 시 onViewDetail을 호출한다', () => {
    const onViewDetail = vi.fn()
    render(<RecentTable items={mockItems} onViewDetail={onViewDetail} />)

    const buttons = screen.getAllByTitle('View Detail')
    fireEvent.click(buttons[0])

    expect(onViewDetail).toHaveBeenCalledWith('123e4567-e89b-12d3-a456-426614174000')
  })

  it('빈 목록일 때 "데이터가 없습니다" 메시지를 표시한다', () => {
    const onViewDetail = vi.fn()
    render(<RecentTable items={[]} onViewDetail={onViewDetail} />)

    expect(screen.getByText('데이터가 없습니다')).toBeInTheDocument()
  })

  it('ID를 8자로 truncate하여 표시한다', () => {
    const onViewDetail = vi.fn()
    render(<RecentTable items={mockItems} onViewDetail={onViewDetail} />)

    // UUID 앞 8자 + "..."
    expect(screen.getByText('123e4567...')).toBeInTheDocument()
  })

  it('processedAt이 null일 때 "-"를 표시한다', () => {
    const onViewDetail = vi.fn()
    render(<RecentTable items={mockItems} onViewDetail={onViewDetail} />)

    // PENDING 상태의 processedAt은 null이므로 "-" 표시
    const cells = screen.getAllByText('-')
    expect(cells.length).toBeGreaterThan(0)
  })
})
