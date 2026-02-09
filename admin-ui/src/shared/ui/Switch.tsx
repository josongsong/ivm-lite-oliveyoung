/**
 * Switch Component
 *
 * SOTA-level toggle switch with:
 * - Controlled and uncontrolled modes
 * - Size variants
 * - Label support
 * - Disabled state
 * - Accessible (proper ARIA)
 *
 * @example
 * ```tsx
 * <Switch checked={enabled} onChange={setEnabled} />
 * <Switch label="Enable notifications" />
 * <Switch size="lg" checked={true} disabled />
 * ```
 */
import { forwardRef, type InputHTMLAttributes, type ReactNode, useState } from 'react'
import './Switch.css'

export interface SwitchProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size' | 'onChange'> {
  /** Controlled checked state */
  checked?: boolean
  /** Default checked for uncontrolled mode */
  defaultChecked?: boolean
  /** Callback when state changes */
  onChange?: (checked: boolean) => void
  /** Size variant */
  size?: 'sm' | 'md' | 'lg'
  /** Label text (right side) */
  label?: ReactNode
  /** Description text */
  description?: string
  /** Label position */
  labelPosition?: 'left' | 'right'
}

export const Switch = forwardRef<HTMLInputElement, SwitchProps>(
  (
    {
      checked: controlledChecked,
      defaultChecked = false,
      onChange,
      size = 'md',
      label,
      description,
      labelPosition = 'right',
      disabled = false,
      className = '',
      id,
      ...props
    },
    ref
  ) => {
    const [internalChecked, setInternalChecked] = useState(defaultChecked)
    const isControlled = controlledChecked !== undefined
    const checked = isControlled ? controlledChecked : internalChecked

    const handleChange = () => {
      if (disabled) return
      const newChecked = !checked
      if (!isControlled) {
        setInternalChecked(newChecked)
      }
      onChange?.(newChecked)
    }

    const switchId = id || `switch-${Math.random().toString(36).slice(2, 9)}`

    const switchElement = (
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-labelledby={label ? `${switchId}-label` : undefined}
        className={`ui-switch ui-switch--${size} ${checked ? 'ui-switch--checked' : ''} ${disabled ? 'ui-switch--disabled' : ''}`}
        onClick={handleChange}
        disabled={disabled}
      >
        <span className="ui-switch__track">
          <span className="ui-switch__thumb" />
        </span>
        <input
          ref={ref}
          type="checkbox"
          id={switchId}
          checked={checked}
          onChange={() => {}}
          disabled={disabled}
          className="ui-switch__input"
          {...props}
        />
      </button>
    )

    if (!label) {
      return switchElement
    }

    return (
      <div className={`ui-switch-wrapper ui-switch-wrapper--${labelPosition} ${className}`}>
        {labelPosition === 'left' && (
          <LabelContent id={switchId} label={label} description={description} />
        )}
        {switchElement}
        {labelPosition === 'right' && (
          <LabelContent id={switchId} label={label} description={description} />
        )}
      </div>
    )
  }
)

Switch.displayName = 'Switch'

function LabelContent({
  id,
  label,
  description,
}: {
  id: string
  label: ReactNode
  description?: string
}) {
  return (
    <div className="ui-switch__label-wrapper">
      <label id={`${id}-label`} htmlFor={id} className="ui-switch__label">
        {label}
      </label>
      {description ? <span className="ui-switch__description">{description}</span> : null}
    </div>
  )
}

// =============================================================================
// Toggle Group (radio-like switches)
// =============================================================================

export interface ToggleOption {
  value: string
  label: string
  disabled?: boolean
}

export interface ToggleGroupProps {
  options: ToggleOption[]
  value: string
  onChange: (value: string) => void
  size?: 'sm' | 'md' | 'lg'
  className?: string
}

export function ToggleGroup({
  options,
  value,
  onChange,
  size = 'md',
  className = '',
}: ToggleGroupProps) {
  return (
    <div className={`ui-toggle-group ui-toggle-group--${size} ${className}`} role="radiogroup">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="radio"
          aria-checked={value === option.value}
          className={`ui-toggle-group__item ${value === option.value ? 'ui-toggle-group__item--active' : ''}`}
          onClick={() => onChange(option.value)}
          disabled={option.disabled}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
