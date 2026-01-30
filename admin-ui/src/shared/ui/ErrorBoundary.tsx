import { Component, type ReactNode } from 'react'
import { AlertTriangle, RefreshCw } from 'lucide-react'
import './ErrorBoundary.css'

interface ErrorBoundaryProps {
  children: ReactNode
  fallback?: ReactNode
  onError?: (error: Error, errorInfo: React.ErrorInfo) => void
  resetOnPropsChange?: boolean
  resetKeys?: Array<string | number>
}

interface ErrorBoundaryState {
  hasError: boolean
  error: Error | null
  errorInfo: React.ErrorInfo | null
  retryCount: number
}

/**
 * React Error Boundary 컴포넌트
 * 
 * 자식 컴포넌트에서 발생한 에러를 캐치하고, 
 * 사용자 친화적인 에러 UI를 표시합니다.
 * 
 * @example
 * ```tsx
 * <ErrorBoundary>
 *   <WorkflowDetailPanel node={node} />
 * </ErrorBoundary>
 * ```
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  private resetTimeoutId: ReturnType<typeof setTimeout> | null = null

  constructor(props: ErrorBoundaryProps) {
    super(props)
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
      retryCount: 0
    }
  }

  static getDerivedStateFromError(error: Error): Partial<ErrorBoundaryState> {
    return {
      hasError: true,
      error
    }
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    // 에러 로깅
    console.error('ErrorBoundary caught an error:', error, errorInfo)
    
    // 콜백 호출
    this.props.onError?.(error, errorInfo)

    this.setState({
      error,
      errorInfo
    })
  }

  componentDidUpdate(prevProps: ErrorBoundaryProps) {
    const { resetKeys, resetOnPropsChange } = this.props
    const { hasError } = this.state

    // resetKeys가 변경되면 에러 상태 리셋
    if (hasError && resetKeys && prevProps.resetKeys) {
      const hasResetKeyChanged = resetKeys.some((key, index) => 
        prevProps.resetKeys?.[index] !== key
      )
      
      if (hasResetKeyChanged) {
        this.resetErrorBoundary()
      }
    }

    // resetOnPropsChange가 true면 props 변경 시 리셋
    if (hasError && resetOnPropsChange && prevProps.children !== this.props.children) {
      this.resetErrorBoundary()
    }
  }

  componentWillUnmount() {
    if (this.resetTimeoutId) {
      clearTimeout(this.resetTimeoutId)
    }
  }

  resetErrorBoundary = () => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
      retryCount: this.state.retryCount + 1
    })
  }

  handleRetry = () => {
    this.resetErrorBoundary()
  }

  render() {
    if (this.state.hasError) {
      // 커스텀 fallback이 있으면 사용
      if (this.props.fallback) {
        return this.props.fallback
      }

      // 기본 에러 UI
      return (
        <div className="error-boundary">
          <div className="error-boundary__content">
            <div className="error-boundary__icon">
              <AlertTriangle size={32} />
            </div>
            <h3 className="error-boundary__title">오류가 발생했습니다</h3>
            <p className="error-boundary__message">
              컴포넌트를 렌더링하는 중 문제가 발생했습니다.
            </p>
            
            {import.meta.env.DEV && this.state.error && (
              <details className="error-boundary__details">
                <summary className="error-boundary__summary">에러 상세 정보</summary>
                <pre className="error-boundary__stack">
                  {this.state.error.toString()}
                  {this.state.errorInfo?.componentStack}
                </pre>
              </details>
            )}

            <button 
              className="error-boundary__retry"
              onClick={this.handleRetry}
            >
              <RefreshCw size={14} />
              다시 시도
            </button>
          </div>
        </div>
      )
    }

    return this.props.children
  }
}
