import { describe, expect, it } from 'vitest'
import { render, screen } from '@/test/utils'
import { StatusBadge } from './StatusBadge'

describe('StatusBadge', () => {
  describe('일반 상태 렌더링', () => {
    it('PENDING 상태를 올바르게 렌더링한다', () => {
      render(<StatusBadge status="PENDING" />)

      expect(screen.getByText('PENDING')).toBeInTheDocument()
      expect(screen.getByText('PENDING').closest('span')).toHaveClass('badge-pending')
    })

    it('PROCESSING 상태를 올바르게 렌더링한다', () => {
      render(<StatusBadge status="PROCESSING" />)

      expect(screen.getByText('PROCESSING')).toBeInTheDocument()
      expect(screen.getByText('PROCESSING').closest('span')).toHaveClass('badge-processing')
    })

    it('COMPLETED 상태를 올바르게 렌더링한다', () => {
      render(<StatusBadge status="COMPLETED" />)

      expect(screen.getByText('COMPLETED')).toBeInTheDocument()
      expect(screen.getByText('COMPLETED').closest('span')).toHaveClass('badge-completed')
    })

    it('FAILED 상태를 올바르게 렌더링한다', () => {
      render(<StatusBadge status="FAILED" />)

      expect(screen.getByText('FAILED')).toBeInTheDocument()
      expect(screen.getByText('FAILED').closest('span')).toHaveClass('badge-failed')
    })
  })

  describe('Health 상태 렌더링', () => {
    it('HEALTHY 상태를 success 클래스로 렌더링한다', () => {
      render(<StatusBadge status="HEALTHY" />)

      expect(screen.getByText('HEALTHY')).toBeInTheDocument()
      expect(screen.getByText('HEALTHY').closest('span')).toHaveClass('badge-success')
    })

    it('DEGRADED 상태를 warning 클래스로 렌더링한다', () => {
      render(<StatusBadge status="DEGRADED" />)

      expect(screen.getByText('DEGRADED')).toBeInTheDocument()
      expect(screen.getByText('DEGRADED').closest('span')).toHaveClass('badge-warning')
    })

    it('UNHEALTHY 상태를 error 클래스로 렌더링한다', () => {
      render(<StatusBadge status="UNHEALTHY" />)

      expect(screen.getByText('UNHEALTHY')).toBeInTheDocument()
      expect(screen.getByText('UNHEALTHY').closest('span')).toHaveClass('badge-error')
    })
  })

  describe('아이콘 표시 옵션', () => {
    it('showIcon=false일 때 아이콘을 표시하지 않는다', () => {
      const { container } = render(<StatusBadge status="PENDING" showIcon={false} />)

      expect(container.querySelector('svg')).not.toBeInTheDocument()
    })

    it('showIcon=true(기본값)일 때 아이콘을 표시한다', () => {
      const { container } = render(<StatusBadge status="PENDING" />)

      expect(container.querySelector('svg')).toBeInTheDocument()
    })
  })

  describe('대소문자 처리', () => {
    it('소문자 상태값도 올바르게 처리한다', () => {
      render(<StatusBadge status="pending" />)

      expect(screen.getByText('pending')).toBeInTheDocument()
      expect(screen.getByText('pending').closest('span')).toHaveClass('badge-pending')
    })

    it('알 수 없는 상태는 그대로 클래스로 사용한다', () => {
      render(<StatusBadge status="CUSTOM_STATUS" />)

      expect(screen.getByText('CUSTOM_STATUS')).toBeInTheDocument()
      expect(screen.getByText('CUSTOM_STATUS').closest('span')).toHaveClass('badge-custom_status')
    })
  })
})
