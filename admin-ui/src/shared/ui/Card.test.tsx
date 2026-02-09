/**
 * Card Component Smoke Tests
 */
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Card, StatsCard } from './Card'

describe('Card', () => {
  it('renders with children', () => {
    render(<Card>Card content</Card>)
    expect(screen.getByText('Card content')).toBeInTheDocument()
  })

  it('applies variant class', () => {
    const { container } = render(<Card variant="elevated">Content</Card>)
    const card = container.querySelector('.ui-card')
    expect(card).toHaveClass('ui-card--elevated')
  })

  it('applies hoverable class when hoverable prop is true', () => {
    const { container } = render(<Card hoverable>Content</Card>)
    const card = container.querySelector('.ui-card')
    expect(card).toHaveClass('ui-card--hoverable')
  })

  it('applies clickable class when onClick is provided', () => {
    const { container } = render(<Card onClick={() => {}}>Content</Card>)
    const card = container.querySelector('.ui-card')
    expect(card).toHaveClass('ui-card--clickable')
  })
})

describe('StatsCard', () => {
  it('renders with title and value', () => {
    render(<StatsCard title="Total" value={100} />)
    expect(screen.getByText('Total')).toBeInTheDocument()
    expect(screen.getByText('100')).toBeInTheDocument()
  })

  it('renders with icon', () => {
    const { container } = render(
      <StatsCard title="Total" value={100} icon={<span data-testid="icon">📊</span>} />
    )
    expect(container.querySelector('[data-testid="icon"]')).toBeInTheDocument()
  })
})
