/**
 * Input Component
 *
 * SOTA-level input with:
 * - forwardRef support for composition
 * - Controlled and uncontrolled modes
 * - Multiple sizes (sm, md, lg)
 * - Error state with optional message
 * - Helper text support
 * - Label integration
 * - Proper accessibility
 *
 * @example
 * ```tsx
 * // Controlled mode (권장)
 * const [value, setValue] = useState('')
 * <Input value={value} onChange={(e) => setValue(e.target.value)} />
 *
 * // Uncontrolled mode
 * <Input defaultValue="initial" />
 *
 * // With error message
 * <Input error errorMessage="This field is required" />
 *
 * // With helper text
 * <Input helperText="Enter your full name" />
 *
 * // Different sizes
 * <Input size="sm" />
 * <Input size="lg" />
 * ```
 */
import { cloneElement, forwardRef, type InputHTMLAttributes, isValidElement, type ReactNode } from 'react'
import './Input.css'

export interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> {
  /** Size variant */
  size?: 'sm' | 'md' | 'lg'
  /** Shows error state */
  error?: boolean
  /** Error message to display */
  errorMessage?: string
  /** Helper text displayed below input */
  helperText?: string
  /** Left icon/addon */
  leftIcon?: ReactNode
  /** Right icon/addon */
  rightIcon?: ReactNode
  // Note: value, defaultValue, onChange는 HTMLInputElement에서 상속됨
}

function normalizeInputIcon(icon: ReactNode): ReactNode {
  if (!isValidElement(icon)) return icon

  const props = icon.props as {
    className?: string
    strokeWidth?: number
    absoluteStrokeWidth?: boolean
  }

  const nextProps: {
    className?: string
    strokeWidth?: number
    absoluteStrokeWidth?: boolean
  } = {}

  // 입력 아이콘은 기본을 살짝 얇게(세련된 톤) + 포커스 시 강조는 CSS로 처리
  if (props.strokeWidth == null) nextProps.strokeWidth = 1.6
  if (props.absoluteStrokeWidth == null) nextProps.absoluteStrokeWidth = true
  nextProps.className = [props.className, 'ui-icon', 'ui-icon--muted'].filter(Boolean).join(' ')

  return cloneElement(icon, nextProps)
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  (
    {
      className = '',
      size = 'md',
      error,
      errorMessage,
      helperText,
      leftIcon,
      rightIcon,
      id,
      ...props
    },
    ref
  ) => {
    const showMessage = error && errorMessage ? errorMessage : helperText
    const messageId = showMessage && id ? `${id}-message` : undefined
    const normalizedLeftIcon = leftIcon ? normalizeInputIcon(leftIcon) : null
    const normalizedRightIcon = rightIcon ? normalizeInputIcon(rightIcon) : null

    return (
      <div className={`ui-input-wrapper ui-input--${size}`}>
        <div className={`ui-input-container ${leftIcon ? 'has-left-icon' : ''} ${rightIcon ? 'has-right-icon' : ''}`}>
          {normalizedLeftIcon ? (
            <span className="ui-input-icon ui-input-icon--left">{normalizedLeftIcon}</span>
          ) : null}
          <input
            ref={ref}
            id={id}
            className={`ui-input ui-input--${size} ${error ? 'error' : ''} ${className}`}
            aria-invalid={error}
            aria-describedby={messageId}
            {...props}
          />
          {normalizedRightIcon ? (
            <span className="ui-input-icon ui-input-icon--right">{normalizedRightIcon}</span>
          ) : null}
        </div>
        {showMessage ? (
          <span
            id={messageId}
            className={`ui-input-message ${error ? 'ui-input-message--error' : ''}`}
          >
            {showMessage}
          </span>
        ) : null}
      </div>
    )
  }
)

Input.displayName = 'Input'
