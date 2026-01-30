import { AlertTriangle, RefreshCw, Server, Wifi, WifiOff } from 'lucide-react'
import { motion } from 'framer-motion'
import './ApiError.css'

interface ApiErrorProps {
  title?: string
  message?: string
  onRetry?: () => void
  variant?: 'default' | 'compact' | 'inline'
  icon?: 'server' | 'network' | 'warning'
}

/**
 * API 에러 표시 컴포넌트
 *
 * 백엔드 연결 실패, 네트워크 오류 등을 표시합니다.
 *
 * @example
 * ```tsx
 * if (isError) {
 *   return <ApiError onRetry={refetch} />
 * }
 * ```
 */
export function ApiError({
  title = '연결 대기 중',
  message = '백엔드 서버가 실행되면 데이터가 표시됩니다',
  onRetry,
  variant = 'default',
  icon = 'server'
}: ApiErrorProps) {
  const IconComponent = {
    server: Server,
    network: WifiOff,
    warning: AlertTriangle
  }[icon]

  if (variant === 'inline') {
    return (
      <div className="api-error-inline">
        <IconComponent size={14} />
        <span>{message}</span>
        {onRetry && (
          <button onClick={onRetry} className="api-error-inline__retry">
            <RefreshCw size={12} />
          </button>
        )}
      </div>
    )
  }

  if (variant === 'compact') {
    return (
      <div className="api-error-compact">
        <IconComponent size={20} />
        <div className="api-error-compact__text">
          <span className="api-error-compact__title">{title}</span>
          <span className="api-error-compact__message">{message}</span>
        </div>
        {onRetry && (
          <button onClick={onRetry} className="api-error-compact__retry">
            <RefreshCw size={14} />
          </button>
        )}
      </div>
    )
  }

  return (
    <motion.div
      className="api-error"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
    >
      <div className="api-error__icon">
        <motion.div
          animate={{
            scale: [1, 1.1, 1],
            opacity: [0.5, 1, 0.5]
          }}
          transition={{
            repeat: Infinity,
            duration: 2,
            ease: 'easeInOut'
          }}
        >
          <IconComponent size={48} />
        </motion.div>
      </div>
      <h3 className="api-error__title">{title}</h3>
      <p className="api-error__message">{message}</p>
      {onRetry && (
        <button onClick={onRetry} className="api-error__retry">
          <RefreshCw size={14} />
          다시 시도
        </button>
      )}
      <div className="api-error__hint">
        <Wifi size={12} />
        <span>서버 상태를 확인해주세요</span>
      </div>
    </motion.div>
  )
}
