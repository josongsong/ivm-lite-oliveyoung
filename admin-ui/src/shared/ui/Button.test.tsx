/**
 * Button Component Smoke Tests
 */
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Button } from './Button'

describe('Button', () => {
  it('renders with text', () => {
    render(<Button>Click me</Button>)
    expect(screen.getByText('Click me')).toBeInTheDocument()
  })

  it('calls onClick when clicked', async () => {
    const handleClick = vi.fn()
    const user = userEvent.setup()
    render(<Button onClick={handleClick}>Click me</Button>)

    const button = screen.getByText('Click me')
    await user.click(button)

    expect(handleClick).toHaveBeenCalledTimes(1)
  })

  it('applies variant class', () => {
    const { container } = render(<Button variant="primary">Click me</Button>)
    const button = container.querySelector('button')
    expect(button).toHaveClass('ui-button--primary')
  })

  it('applies size class', () => {
    const { container } = render(<Button size="sm">Click me</Button>)
    const button = container.querySelector('button')
    expect(button).toHaveClass('ui-button--sm')
  })

  it('is disabled when disabled prop is true', () => {
    render(<Button disabled>Click me</Button>)
    const button = screen.getByText('Click me')
    expect(button).toBeDisabled()
  })
})
