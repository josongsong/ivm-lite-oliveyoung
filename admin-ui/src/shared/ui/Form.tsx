/**
 * Form Components
 *
 * 재사용 가능한 폼 컴포넌트들:
 * - Form: 폼 컨테이너
 * - FormRow: 폼 행 (여러 필드 가로 배치)
 * - FormGroup: 폼 그룹 (라벨 + 입력 + 에러 메시지)
 * - FormInput: 폼용 입력 필드
 * - FormTextArea: 폼용 텍스트 영역
 *
 * @example
 * ```tsx
 * <Form onSubmit={(e) => { e.preventDefault(); console.log('submit'); }}>
 *   <FormRow>
 *     <FormGroup label="Name" htmlFor="name" error={errors.name}>
 *       <FormInput id="name" name="name" />
 *     </FormGroup>
 *     <FormGroup label="Email" htmlFor="email" flex={2}>
 *       <FormInput id="email" type="email" />
 *     </FormGroup>
 *   </FormRow>
 * </Form>
 * ```
 */

import { forwardRef, type ReactNode } from 'react'
import './Form.css'

export interface FormProps {
  children: ReactNode
  className?: string
  onSubmit?: (e: React.FormEvent<HTMLFormElement>) => void
}

export function Form({ children, className = '', onSubmit }: FormProps) {
  return (
    <form className={`ui-form ${className}`} onSubmit={onSubmit}>
      {children}
    </form>
  )
}

export interface FormRowProps {
  children: ReactNode
  className?: string
}

export function FormRow({ children, className = '' }: FormRowProps) {
  return <div className={`ui-form-row ${className}`}>{children}</div>
}

export interface FormGroupProps {
  children: ReactNode
  label?: string
  htmlFor?: string
  className?: string
  flex?: 1 | 2 | 3 | 4
  error?: string
  helperText?: string
}

export function FormGroup({
  children,
  label,
  htmlFor,
  className = '',
  flex,
  error,
  helperText,
}: FormGroupProps) {
  const flexClass = flex ? `ui-form-group--flex-${flex}` : ''
  const classes = ['ui-form-group', flexClass, className].filter(Boolean).join(' ')

  return (
    <div className={classes}>
      {label && (
        <label htmlFor={htmlFor} className="ui-form-label">
          {label}
        </label>
      )}
      {children}
      {error && <span className="ui-form-error">{error}</span>}
      {helperText && !error && <span className="ui-form-helper">{helperText}</span>}
    </div>
  )
}

export interface FormInputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  /** Shows error state */
  error?: boolean
}

/**
 * FormInput - 폼용 입력 필드 (forwardRef 지원)
 * 
 * React Hook Form과 통합 가능:
 * ```tsx
 * const { register } = useForm()
 * <FormInput {...register('name')} />
 * ```
 */
export const FormInput = forwardRef<HTMLInputElement, FormInputProps>(
  ({ className = '', error, ...props }, ref) => {
    const classes = ['ui-form-input', error ? 'ui-form-input--error' : '', className]
      .filter(Boolean)
      .join(' ')

    return <input ref={ref} className={classes} {...props} />
  }
)

FormInput.displayName = 'FormInput'

export interface FormTextAreaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  /** Shows error state */
  error?: boolean
}

/**
 * FormTextArea - 폼용 텍스트 영역 (forwardRef 지원)
 * 
 * React Hook Form과 통합 가능:
 * ```tsx
 * const { register } = useForm()
 * <FormTextArea {...register('description')} />
 * ```
 */
export const FormTextArea = forwardRef<HTMLTextAreaElement, FormTextAreaProps>(
  ({ className = '', error, ...props }, ref) => {
    const classes = ['ui-form-textarea', error ? 'ui-form-textarea--error' : '', className]
      .filter(Boolean)
      .join(' ')

    return <textarea ref={ref} className={classes} {...props} />
  }
)

FormTextArea.displayName = 'FormTextArea'
