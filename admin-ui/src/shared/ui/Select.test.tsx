/**
 * Select Component Smoke Tests
 */
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Select } from './Select'

const options = [
  { value: 'a', label: 'Option A' },
  { value: 'b', label: 'Option B' },
  { value: 'c', label: 'Option C' },
]

describe('Select', () => {
  it('renders with selected value', () => {
    render(<Select value="a" onChange={() => {}} options={options} />)

    expect(screen.getByText('Option A')).toBeInTheDocument()
  })

  it('shows placeholder when no value selected', () => {
    render(<Select value="" onChange={() => {}} options={options} placeholder="Select..." />)

    expect(screen.getByText('Select...')).toBeInTheDocument()
  })

  it('opens dropdown when clicked', async () => {
    const user = userEvent.setup()
    render(<Select value="" onChange={() => {}} options={options} />)

    const trigger = screen.getByRole('button')
    await user.click(trigger)

    expect(screen.getByText('Option A')).toBeInTheDocument()
    expect(screen.getByText('Option B')).toBeInTheDocument()
    expect(screen.getByText('Option C')).toBeInTheDocument()
  })

  it('calls onChange when option is selected', async () => {
    const handleChange = vi.fn()
    const user = userEvent.setup()
    render(<Select value="" onChange={handleChange} options={options} />)

    const trigger = screen.getByRole('button')
    await user.click(trigger)

    const optionB = screen.getByText('Option B')
    await user.click(optionB)

    expect(handleChange).toHaveBeenCalledWith('b')
  })

  it('supports uncontrolled mode with defaultValue', () => {
    const handleChange = vi.fn()
    render(<Select defaultValue="a" onChange={handleChange} options={options} />)

    expect(screen.getByText('Option A')).toBeInTheDocument()
  })
})
