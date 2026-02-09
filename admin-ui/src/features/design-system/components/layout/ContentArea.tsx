/**
 * Design System ContentArea Component
 *
 * 메인 콘텐츠 영역을 제공합니다.
 * Outlet을 통해 중첩 라우팅 콘텐츠를 렌더링합니다.
 */
import { type ReactNode } from 'react'
import { motion } from 'framer-motion'
import { useLocation } from 'react-router-dom'
import './ContentArea.css'

interface ContentAreaProps {
  children: ReactNode
}

export function ContentArea({ children }: ContentAreaProps) {
  const location = useLocation()

  return (
    <main className="ds-content-area">
      <motion.div
        key={location.pathname}
        className="ds-content-inner"
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -12 }}
        transition={{ duration: 0.2 }}
      >
        {children}
      </motion.div>
    </main>
  )
}
