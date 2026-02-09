/**
 * OutboxHeader - 페이지 헤더 컴포넌트
 */
import { motion } from 'framer-motion'

export function OutboxHeader() {
  return (
    <div className="page-header">
      <motion.h1
        className="page-title"
        initial={{ opacity: 0, x: -20 }}
        animate={{ opacity: 1, x: 0 }}
      >
        Outbox
      </motion.h1>
      <motion.p
        className="page-subtitle"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.1 }}
      >
        메시지 처리 상태를 모니터링하고 DLQ, 재처리를 관리합니다
      </motion.p>
    </div>
  )
}
