/**
 * Switch Component Smoke Tests
 */
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Switch } from './Switch'

describe('Switch', () => {
  it('renders as unchecked by default', () => {
    render(<Switch />)
    const switchInput = screen.getByRole('switch')
    expect(switchInput).not.toBeChecked()
  })

  it('renders as checked when checked prop is true', () => {
    render(<Switch checked={true} onChange={() => {}} />)
    const switchInput = screen.getByRole('switch')
    expect(switchInput).toBeChecked()
  })

  it('calls onChange when clicked', async () => {
    const handleChange = vi.fn()
    const user = userEvent.setup()
    render(<Switch checked={false} onChange={handleChange} />)

    const switchInput = screen.getByRole('switch')
    await user.click(switchInput)

    expect(handleChange).toHaveBeenCalledWith(true)
  })

  it('renders with label', () => {
    render(<Switch label="Enable notifications" />)
    expect(screen.getByText('Enable notifications')).toBeInTheDocument()
  })

  it('is disabled when disabled prop is true', () => {
    render(<Switch disabled />)
    const switchInput = screen.getByRole('switch')
    expect(switchInput).toBeDisabled()
  })

  it('supports uncontrolled mode with defaultChecked', () => {
    render(<Switch defaultChecked={true} />)
    const switchInput = screen.getByRole('switch')
    expect(switchInput).toBeChecked()
  })

  it('applies size class', () => {
    const { container } = render(<Switch size="lg" />)
    const switchWrapper = container.querySelector('.ui-switch')
    expect(switchWrapper).toHaveClass('ui-switch--lg')
  })
})
