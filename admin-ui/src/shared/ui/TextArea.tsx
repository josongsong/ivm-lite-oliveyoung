/**
 * TextArea Component
 *
 * SOTA-level textarea with:
 * - forwardRef support for composition
 * - Multiple sizes (sm, md, lg)
 * - Error state with optional message
 * - Helper text support
 * - Monospace font option
 * - Proper accessibility
 *
 * @example
 * ```tsx
 * // Controlled mode (권장)
 * const [value, setValue] = useState('')
 * <TextArea value={value} onChange={(e) => setValue(e.target.value)} />
 *
 * // Uncontrolled mode
 * <TextArea defaultValue="initial" />
 *
 * // With error message
 * <TextArea error errorMessage="Description is required" />
 *
 * // Monospace for code
 * <TextArea mono placeholder="Enter JSON" />
 *
 * // Different sizes
 * <TextArea size="sm" />
 * <TextArea size="lg" />
 * ```
 */
import { forwardRef, type TextareaHTMLAttributes } from 'react'
import './TextArea.css'

export interface TextAreaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  /** Size variant */
  size?: 'sm' | 'md' | 'lg'
  /** Shows error state */
  error?: boolean
  /** Error message to display */
  errorMessage?: string
  /** Helper text displayed below textarea */
  helperText?: string
  /** Use monospace font (for code/JSON) */
  mono?: boolean
}

export const TextArea = forwardRef<HTMLTextAreaElement, TextAreaProps>(
  (
    {
      className = '',
      size = 'md',
      error,
      errorMessage,
      helperText,
      mono,
      id,
      ...props
    },
    ref
  ) => {
    const showMessage = error && errorMessage ? errorMessage : helperText
    const messageId = showMessage && id ? `${id}-message` : undefined

    return (
      <div className={`ui-textarea-wrapper ui-textarea-wrapper--${size}`}>
        <textarea
          ref={ref}
          id={id}
          className={`ui-textarea ui-textarea--${size} ${error ? 'error' : ''} ${mono ? 'mono' : ''} ${className}`}
          aria-invalid={error}
          aria-describedby={messageId}
          {...props}
        />
        {showMessage ? (
          <span
            id={messageId}
            className={`ui-textarea-message ${error ? 'ui-textarea-message--error' : ''}`}
          >
            {showMessage}
          </span>
        ) : null}
      </div>
    )
  }
)

TextArea.displayName = 'TextArea'
