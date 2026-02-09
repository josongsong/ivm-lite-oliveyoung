/**
 * Alert Component Smoke Tests
 */
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Alert } from './Alert'

describe('Alert', () => {
  it('renders with message', () => {
    render(<Alert variant="info">Info message</Alert>)
    expect(screen.getByText('Info message')).toBeInTheDocument()
  })

  it('renders with title', () => {
    render(
      <Alert variant="success" title="Success!">
        Operation completed
      </Alert>
    )
    expect(screen.getByText('Success!')).toBeInTheDocument()
    expect(screen.getByText('Operation completed')).toBeInTheDocument()
  })

  it('calls onDismiss when dismissible and close button clicked', async () => {
    const handleDismiss = vi.fn()
    const user = userEvent.setup()
    render(
      <Alert variant="info" dismissible onDismiss={handleDismiss}>
        Dismissible alert
      </Alert>
    )

    const closeButton = screen.getByLabelText('Dismiss')
    await user.click(closeButton)

    expect(handleDismiss).toHaveBeenCalledTimes(1)
  })

  it('applies variant class', () => {
    const { container } = render(<Alert variant="error">Error message</Alert>)
    const alert = container.querySelector('.ui-alert')
    expect(alert).toHaveClass('ui-alert--error')
  })

  it('applies size class', () => {
    const { container } = render(<Alert variant="info" size="sm">Message</Alert>)
    const alert = container.querySelector('.ui-alert')
    expect(alert).toHaveClass('ui-alert--sm')
  })
})
