import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { showToast, toast, ToastContainer } from './Toast'

describe('Toast', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('showToast', () => {
    it('success 토스트를 표시한다', async () => {
      render(<ToastContainer />)

      act(() => {
        toast.success('Operation successful')
      })

      expect(screen.getByText('Operation successful')).toBeInTheDocument()
    })

    it('error 토스트를 표시한다', async () => {
      render(<ToastContainer />)

      act(() => {
        toast.error('Something went wrong')
      })

      expect(screen.getByText('Something went wrong')).toBeInTheDocument()
    })

    it('warning 토스트를 표시한다', async () => {
      render(<ToastContainer />)

      act(() => {
        toast.warning('Please be careful')
      })

      expect(screen.getByText('Please be careful')).toBeInTheDocument()
    })

    it('info 토스트를 표시한다', async () => {
      render(<ToastContainer />)

      act(() => {
        toast.info('Here is some info')
      })

      expect(screen.getByText('Here is some info')).toBeInTheDocument()
    })
  })

  describe('자동 dismiss', () => {
    it('duration 후 자동으로 사라진다', async () => {
      render(<ToastContainer />)

      act(() => {
        showToast('info', 'Auto dismiss test', 1000)
      })

      expect(screen.getByText('Auto dismiss test')).toBeInTheDocument()

      act(() => {
        vi.advanceTimersByTime(1000)
      })

      await waitFor(() => {
        expect(screen.queryByText('Auto dismiss test')).not.toBeInTheDocument()
      })
    })
  })

  describe('여러 토스트', () => {
    it('여러 토스트를 동시에 표시할 수 있다', async () => {
      render(<ToastContainer />)

      act(() => {
        toast.success('First message')
        toast.error('Second message')
        toast.info('Third message')
      })

      expect(screen.getByText('First message')).toBeInTheDocument()
      expect(screen.getByText('Second message')).toBeInTheDocument()
      expect(screen.getByText('Third message')).toBeInTheDocument()
    })
  })

  describe('수동 dismiss', () => {
    it('dismiss 버튼 클릭 시 토스트가 사라진다', async () => {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
      render(<ToastContainer />)

      act(() => {
        toast.info('Dismissable toast')
      })

      expect(screen.getByText('Dismissable toast')).toBeInTheDocument()

      const dismissButton = screen.getByRole('button')
      await user.click(dismissButton)

      await waitFor(() => {
        expect(screen.queryByText('Dismissable toast')).not.toBeInTheDocument()
      })
    })
  })
})
