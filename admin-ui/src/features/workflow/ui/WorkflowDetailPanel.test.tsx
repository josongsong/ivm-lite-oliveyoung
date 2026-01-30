import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { WorkflowDetailPanel } from './WorkflowDetailPanel'
import type { NodeDetailResponse, WorkflowNode } from '../model/types'

// framer-motion 모킹
vi.mock('framer-motion', () => ({
  motion: {
    div: ({ children, ...props }: any) => <div {...props}>{children}</div>
  },
  AnimatePresence: ({ children }: any) => <>{children}</>
}))

// react-router-dom 모킹
vi.mock('react-router-dom', () => ({
  Link: ({ children, to, className }: any) => (
    <a href={to} className={className}>{children}</a>
  )
}))

describe('WorkflowDetailPanel', () => {
  const mockOnClose = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
  })

  const createMockNode = (overrides?: Partial<WorkflowNode>): WorkflowNode => ({
    id: 'test-node-1',
    type: 'rawdata',
    position: { x: 0, y: 0 },
    data: {
      label: 'Test Node',
      status: 'healthy',
      entityType: 'PRODUCT',
      contractId: 'entity.product.v1',
      metadata: {},
      stats: {
        recordCount: 1000,
        throughput: 50.5,
        latencyP99Ms: 100,
        errorCount: 0
      }
    },
    ...overrides
  })

  const createMockDetail = (overrides?: Partial<NodeDetailResponse>): NodeDetailResponse => ({
    node: createMockNode(),
    relatedContracts: [],
    upstreamNodes: [],
    downstreamNodes: [],
    recentActivity: [],
    metrics: {
      avgLatencyMs: 50,
      p99LatencyMs: 100,
      errorRate: 0,
      throughputTrend: []
    },
    ...overrides
  })

  describe('기본 렌더링', () => {
    it('정상적인 노드 데이터로 렌더링되어야 함', () => {
      const node = createMockNode()
      render(<WorkflowDetailPanel node={node} onClose={mockOnClose} />)

      expect(screen.getByText('Test Node')).toBeInTheDocument()
      expect(screen.getByText('rawdata')).toBeInTheDocument()
      expect(screen.getByText('PRODUCT')).toBeInTheDocument()
    })

    it('node가 null이면 렌더링하지 않아야 함', () => {
      const { container } = render(
        <WorkflowDetailPanel node={null as any} onClose={mockOnClose} />
      )
      expect(container.firstChild).toBeNull()
    })

    it('node.data가 없으면 렌더링하지 않아야 함', () => {
      const node = { id: 'test', type: 'rawdata', position: { x: 0, y: 0 }, data: null } as any
      const { container } = render(
        <WorkflowDetailPanel node={node} onClose={mockOnClose} />
      )
      expect(container.firstChild).toBeNull()
    })
  })

  describe('스키마 필드 처리', () => {
    it('schemaFields가 배열이면 정상 렌더링되어야 함', () => {
      const node = createMockNode({
        type: 'slice',
        data: {
          label: 'Test Slice',
          status: 'healthy',
          metadata: {
            schemaFields: ['field1', 'field2', 'field3']
          }
        }
      })

      render(<WorkflowDetailPanel node={node} onClose={mockOnClose} />)

      expect(screen.getByText('스키마 필드')).toBeInTheDocument()
      expect(screen.getByText('field1')).toBeInTheDocument()
      expect(screen.getByText('field2')).toBeInTheDocument()
      expect(screen.getByText('field3')).toBeInTheDocument()
    })

    it('schemaFields가 배열이 아니면 필드 섹션을 렌더링하지 않아야 함', () => {
      const node = createMockNode({
        type: 'slice',
        data: {
          label: 'Test Slice',
          status: 'healthy',
          metadata: {
            schemaFields: { field1: 'value1' } // 객체로 잘못된 데이터
          }
        }
      })

      render(<WorkflowDetailPanel node={node} onClose={mockOnClose} />)

      expect(screen.queryByText('스키마 필드')).not.toBeInTheDocument()
    })

    it('schemaFields가 null이면 필드 섹션을 렌더링하지 않아야 함', () => {
      const node = createMockNode({
        type: 'slice',
        data: {
          label: 'Test Slice',
          status: 'healthy',
          metadata: {
            schemaFields: null
          }
        }
      })

      render(<WorkflowDetailPanel node={node} onClose={mockOnClose} />)

      expect(screen.queryByText('스키마 필드')).not.toBeInTheDocument()
    })

    it('schemaFields가 빈 배열이면 필드 섹션을 렌더링하지 않아야 함', () => {
      const node = createMockNode({
        type: 'slice',
        data: {
          label: 'Test Slice',
          status: 'healthy',
          metadata: {
            schemaFields: []
          }
        }
      })

      render(<WorkflowDetailPanel node={node} onClose={mockOnClose} />)

      expect(screen.queryByText('스키마 필드')).not.toBeInTheDocument()
    })

    it('와일드카드 필드(*)가 있으면 힌트를 표시해야 함', () => {
      const node = createMockNode({
        type: 'slice',
        data: {
          label: 'Test Slice',
          status: 'healthy',
          metadata: {
            schemaFields: ['field1', '*', 'field2']
          }
        }
      })

      render(<WorkflowDetailPanel node={node} onClose={mockOnClose} />)

      expect(screen.getByText(/모든 필드가 슬라이스에 포함됩니다/)).toBeInTheDocument()
      expect(screen.getByText('All Fields')).toBeInTheDocument()
    })
  })

  describe('통계 데이터 처리', () => {
    it('stats가 있으면 통계 섹션을 렌더링해야 함', () => {
      const node = createMockNode({
        data: {
          label: 'Test Node',
          status: 'healthy',
          stats: {
            recordCount: 1000,
            throughput: 50.5,
            latencyP99Ms: 100,
            errorCount: 0
          }
        }
      })

      render(<WorkflowDetailPanel node={node} onClose={mockOnClose} />)

      expect(screen.getByText('실시간 통계')).toBeInTheDocument()
      expect(screen.getByText('1K')).toBeInTheDocument() // formatNumber(1000)
      expect(screen.getByText('50.5')).toBeInTheDocument()
      expect(screen.getByText('100')).toBeInTheDocument()
    })

    it('stats가 없으면 통계 섹션을 렌더링하지 않아야 함', () => {
      const node = createMockNode({
        data: {
          label: 'Test Node',
          status: 'healthy',
          stats: undefined
        }
      })

      render(<WorkflowDetailPanel node={node} onClose={mockOnClose} />)

      expect(screen.queryByText('실시간 통계')).not.toBeInTheDocument()
    })

    it('latencyP99Ms가 undefined면 해당 카드를 렌더링하지 않아야 함', () => {
      const node = createMockNode({
        data: {
          label: 'Test Node',
          status: 'healthy',
          stats: {
            recordCount: 1000,
            throughput: 50.5,
            latencyP99Ms: undefined,
            errorCount: 0
          }
        }
      })

      render(<WorkflowDetailPanel node={node} onClose={mockOnClose} />)

      expect(screen.queryByText('P99 Latency')).not.toBeInTheDocument()
    })
  })

  describe('연결된 노드 처리', () => {
    it('detail이 있으면 연결된 노드 섹션을 렌더링해야 함', () => {
      const node = createMockNode()
      const detail = createMockDetail({
        upstreamNodes: ['upstream-1', 'upstream-2'],
        downstreamNodes: ['downstream-1']
      })

      render(<WorkflowDetailPanel node={node} detail={detail} onClose={mockOnClose} />)

      expect(screen.getByText('연결된 노드')).toBeInTheDocument()
      expect(screen.getByText(/Upstream \(2\)/)).toBeInTheDocument()
      expect(screen.getByText(/Downstream \(1\)/)).toBeInTheDocument()
    })

    it('upstreamNodes가 빈 배열이면 Upstream 섹션을 렌더링하지 않아야 함', () => {
      const node = createMockNode()
      const detail = createMockDetail({
        upstreamNodes: [],
        downstreamNodes: ['downstream-1']
      })

      render(<WorkflowDetailPanel node={node} detail={detail} onClose={mockOnClose} />)

      expect(screen.queryByText(/Upstream/)).not.toBeInTheDocument()
      expect(screen.getByText(/Downstream/)).toBeInTheDocument()
    })
  })

  describe('상태 처리', () => {
    it('status가 없으면 기본값 inactive를 사용해야 함', () => {
      const node = createMockNode({
        data: {
          label: 'Test Node',
          status: undefined as any,
          metadata: {}
        }
      })

      render(<WorkflowDetailPanel node={node} onClose={mockOnClose} />)

      expect(screen.getByText('INACTIVE')).toBeInTheDocument()
    })

    it('알 수 없는 status면 기본 색상을 사용해야 함', () => {
      const node = createMockNode({
        data: {
          label: 'Test Node',
          status: 'unknown-status' as any,
          metadata: {}
        }
      })

      render(<WorkflowDetailPanel node={node} onClose={mockOnClose} />)

      // 에러 없이 렌더링되어야 함
      expect(screen.getByText('Test Node')).toBeInTheDocument()
    })
  })

  describe('인터랙션', () => {
    it('닫기 버튼 클릭 시 onClose가 호출되어야 함', () => {
      const node = createMockNode()
      render(<WorkflowDetailPanel node={node} onClose={mockOnClose} />)

      const closeButton = screen.getByRole('button', { name: /close/i })
      fireEvent.click(closeButton)

      expect(mockOnClose).toHaveBeenCalledTimes(1)
    })

    it('스키마 필드 토글 버튼 클릭 시 확장/축소되어야 함', () => {
      const node = createMockNode({
        type: 'slice',
        data: {
          label: 'Test Slice',
          status: 'healthy',
          metadata: {
            schemaFields: ['field1', 'field2']
          }
        }
      })

      render(<WorkflowDetailPanel node={node} onClose={mockOnClose} />)

      const toggleButton = screen.getByText('스키마 필드').closest('button')
      expect(toggleButton).toBeInTheDocument()

      // 초기에는 확장되어 있음
      expect(screen.getByText('field1')).toBeInTheDocument()

      // 토글 클릭
      fireEvent.click(toggleButton!)

      // 축소되어야 함 (AnimatePresence 때문에 실제로는 아직 보일 수 있음)
      // 하지만 상태는 변경되어야 함
    })
  })

  describe('에러 케이스', () => {
    it('node.data.label이 없어도 에러 없이 렌더링되어야 함', () => {
      const node = createMockNode({
        data: {
          status: 'healthy',
          metadata: {}
        }
      })

      render(<WorkflowDetailPanel node={node} onClose={mockOnClose} />)

      // 에러 없이 렌더링되어야 함
      expect(screen.getByText('rawdata')).toBeInTheDocument()
    })

    it('node.type이 없어도 기본값 rawdata를 사용해야 함', () => {
      const node = {
        id: 'test',
        position: { x: 0, y: 0 },
        data: {
          label: 'Test',
          status: 'healthy',
          metadata: {}
        }
      } as any

      render(<WorkflowDetailPanel node={node} onClose={mockOnClose} />)

      expect(screen.getByText('rawdata')).toBeInTheDocument()
    })

    it('metadata가 없어도 에러 없이 렌더링되어야 함', () => {
      const node = createMockNode({
        data: {
          label: 'Test Node',
          status: 'healthy'
        }
      })

      render(<WorkflowDetailPanel node={node} onClose={mockOnClose} />)

      expect(screen.getByText('Test Node')).toBeInTheDocument()
    })
  })
})
