/**
 * Loading Component
 *
 * 로딩 인디케이터 컴포넌트:
 * - 다양한 크기 지원
 * - 텍스트 옵션
 * - 전체 페이지 또는 인라인 모드
 *
 * @example
 * ```tsx
 * // 전체 페이지 로딩
 * <Loading text="Loading data..." />
 *
 * // 인라인 로딩
 * <Loading fullPage={false} size="sm" />
 * ```
 */
import './Loading.css'

export interface LoadingProps {
  /** Spinner size */
  size?: 'sm' | 'md' | 'lg'
  /** Text to display below spinner */
  text?: string
  /** Use full page container */
  fullPage?: boolean
}

export function Loading({ size = 'md', text, fullPage = true }: LoadingProps) {
  const content = (
    <div className={`loading loading--${size}`} role="status" aria-live="polite">
      <div className="loading-spinner" aria-hidden="true" />
      {text ? <span className="loading-text">{text}</span> : null}
    </div>
  )

  if (fullPage) {
    return <div className="page-container">{content}</div>
  }

  return content
}
