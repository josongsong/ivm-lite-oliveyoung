/**
 * Input Component Smoke Tests
 */
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Input } from './Input'

describe('Input', () => {
  it('renders with placeholder', () => {
    render(<Input placeholder="Enter name" />)
    expect(screen.getByPlaceholderText('Enter name')).toBeInTheDocument()
  })

  it('supports controlled mode', async () => {
    const { rerender } = render(<Input value="initial" onChange={() => {}} />)
    const input = screen.getByDisplayValue('initial') as HTMLInputElement

    expect(input.value).toBe('initial')

    rerender(<Input value="updated" onChange={() => {}} />)
    expect(input.value).toBe('updated')
  })

  it('supports uncontrolled mode with defaultValue', () => {
    render(<Input defaultValue="default" />)
    const input = screen.getByDisplayValue('default') as HTMLInputElement
    expect(input.value).toBe('default')
  })

  it('displays error message', () => {
    render(<Input error errorMessage="This field is required" />)
    expect(screen.getByText('This field is required')).toBeInTheDocument()
  })

  it('displays helper text', () => {
    render(<Input helperText="Enter your full name" />)
    expect(screen.getByText('Enter your full name')).toBeInTheDocument()
  })

  it('calls onChange when typing', async () => {
    const handleChange = vi.fn()
    const user = userEvent.setup()
    render(<Input onChange={handleChange} />)

    const input = screen.getByRole('textbox')
    await user.type(input, 'test')

    expect(handleChange).toHaveBeenCalled()
  })
})
