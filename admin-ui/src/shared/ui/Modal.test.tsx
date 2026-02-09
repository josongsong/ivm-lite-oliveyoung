/**
 * Modal Component Smoke Tests
 *
 * 최소한의 테스트로 컴포넌트가 정상적으로 렌더링되고 기본 동작을 수행하는지 확인합니다.
 */
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Modal } from './Modal'

describe('Modal', () => {
  it('renders when isOpen is true', () => {
    render(
      <Modal isOpen={true} onClose={() => {}} title="Test Modal">
        <p>Modal content</p>
      </Modal>
    )

    expect(screen.getByText('Test Modal')).toBeInTheDocument()
    expect(screen.getByText('Modal content')).toBeInTheDocument()
  })

  it('does not render when isOpen is false', () => {
    const { container } = render(
      <Modal isOpen={false} onClose={() => {}} title="Test Modal">
        <p>Modal content</p>
      </Modal>
    )

    expect(container.querySelector('.modal-overlay')).not.toBeInTheDocument()
  })

  it('calls onClose when close button is clicked', () => {
    const handleClose = vi.fn()
    render(
      <Modal isOpen={true} onClose={handleClose} title="Test Modal">
        <p>Modal content</p>
      </Modal>
    )

    const closeButton = screen.getByLabelText('Close modal')
    closeButton.click()

    expect(handleClose).toHaveBeenCalledTimes(1)
  })

  it('renders footer when provided', () => {
    render(
      <Modal
        isOpen={true}
        onClose={() => {}}
        title="Test Modal"
        footer={<button>Save</button>}
      >
        <p>Modal content</p>
      </Modal>
    )

    expect(screen.getByText('Save')).toBeInTheDocument()
  })

  it('applies size variant class', () => {
    const { container } = render(
      <Modal isOpen={true} onClose={() => {}} title="Test Modal" size="lg">
        <p>Modal content</p>
      </Modal>
    )

    const content = container.querySelector('.modal-content')
    expect(content).toHaveClass('modal-content--lg')
  })
})
