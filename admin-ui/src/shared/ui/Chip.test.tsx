/**
 * Chip Component Smoke Tests
 */
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Chip } from './Chip'

describe('Chip', () => {
  it('renders with text', () => {
    render(<Chip>Tag</Chip>)
    expect(screen.getByText('Tag')).toBeInTheDocument()
  })

  it('applies variant class', () => {
    const { container } = render(<Chip variant="primary">Tag</Chip>)
    const chip = container.querySelector('.chip')
    expect(chip).toHaveClass('chip-primary')
  })

  it('calls onClick when clicked', async () => {
    const handleClick = vi.fn()
    const user = userEvent.setup()
    render(<Chip onClick={handleClick}>Clickable</Chip>)

    const chip = screen.getByText('Clickable')
    await user.click(chip)

    expect(handleClick).toHaveBeenCalledTimes(1)
  })

  it('calls onRemove when remove button clicked', async () => {
    const handleRemove = vi.fn()
    const user = userEvent.setup()
    render(<Chip onRemove={handleRemove}>Removable</Chip>)

    const removeButton = screen.getByLabelText('Remove')
    await user.click(removeButton)

    expect(handleRemove).toHaveBeenCalledTimes(1)
  })

  it('applies selected class when selected', () => {
    const { container } = render(<Chip selected>Selected</Chip>)
    const chip = container.querySelector('.chip')
    expect(chip).toHaveClass('selected')
  })

  it('is disabled when disabled prop is true', () => {
    render(<Chip disabled onClick={() => {}}>Disabled</Chip>)
    const chip = screen.getByText('Disabled')
    expect(chip).toBeDisabled()
  })
})
