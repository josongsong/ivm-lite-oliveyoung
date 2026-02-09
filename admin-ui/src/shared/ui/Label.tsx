/**
 * Label Component
 *
 * 폼 레이블 컴포넌트:
 * - 필수 필드 표시 (required)
 * - HTML label 요소 확장
 * - 접근성 고려 (htmlFor 연결)
 *
 * @example
 * ```tsx
 * // 기본 사용
 * <Label htmlFor="name">Name</Label>
 * <Input id="name" />
 *
 * // 필수 필드
 * <Label htmlFor="email" required>Email</Label>
 * <Input id="email" required />
 * ```
 */
import { forwardRef, type LabelHTMLAttributes } from 'react'
import './Label.css'

export interface LabelProps extends LabelHTMLAttributes<HTMLLabelElement> {
  /** Show required indicator (*) */
  required?: boolean
}

export const Label = forwardRef<HTMLLabelElement, LabelProps>(
  ({ children, required, className = '', ...props }, ref) => {
    return (
      <label ref={ref} className={`ui-label ${className}`} {...props}>
        {children}
        {required ? <span className="required-mark" aria-label="required">*</span> : null}
      </label>
    )
  }
)

Label.displayName = 'Label'
