/**
 * Design System Page - Main Layout
 *
 * Design System Catalog의 메인 레이아웃 컴포넌트입니다.
 * Sidebar + ContentArea 구조로 구성됩니다.
 */
import { Outlet } from 'react-router-dom'
import { AnimatePresence } from 'framer-motion'
import { Sidebar } from '../components/layout/Sidebar'
import { ContentArea } from '../components/layout/ContentArea'
import './DesignSystemPage.css'

export function DesignSystemPage() {
  return (
    <div className="design-system-page">
      <Sidebar />
      <ContentArea>
        <AnimatePresence mode="wait">
          <Outlet />
        </AnimatePresence>
      </ContentArea>
    </div>
  )
}
