import { Link, useLocation } from 'react-router-dom'
import { motion } from 'framer-motion'
import {
  Activity,
  Bell,
  Database,
  FileCode2,
  GitBranch,
  GitMerge,
  HeartPulse,
  Inbox,
  LayoutDashboard,
  Palette,
  Play,
  RotateCcw,
  Search,
  Settings,
  Zap
} from 'lucide-react'
import { APP_INFO } from '@/shared/config'
import { EnvironmentSelector, ThemeCycleButton } from '@/shared/ui'
import './Layout.css'

interface LayoutProps {
  children: React.ReactNode
}

const navItems = [
  { path: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { path: '/explorer', label: 'Explorer', icon: Database },
  { path: '/contracts', label: 'Contracts', icon: FileCode2 },
  { path: '/pipeline', label: 'Pipeline', icon: GitBranch },
  { path: '/workflow', label: 'Workflow', icon: GitMerge },
  { path: '/outbox', label: 'Outbox', icon: Inbox },
  { path: '/playground', label: 'Playground', icon: Play },
  { path: '/design-system', label: 'Design System', icon: Palette },
]

const opsNavItems = [
  { path: '/environment', label: 'Environment', icon: Settings },
  { path: '/health', label: 'Health', icon: HeartPulse },
  { path: '/observability', label: 'Observability', icon: Activity },
  { path: '/traces', label: 'Traces', icon: Search },
  { path: '/alerts', label: 'Alerts', icon: Bell },
  { path: '/webhooks', label: 'Webhooks', icon: Zap },
  { path: '/backfill', label: 'Backfill', icon: RotateCcw },
]

export function Layout({ children }: LayoutProps) {
  const location = useLocation()

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="sidebar-header">
          <motion.div
            className="header-row"
            initial={{ scale: 0.8, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={{ duration: 0.5, type: 'spring' }}
          >
            <span className="logo-title">IVM Lite</span>
            <EnvironmentSelector />
          </motion.div>
        </div>

        <nav className="sidebar-nav">
          {navItems.map((item, index) => {
            const isActive = location.pathname.startsWith(item.path)
            const Icon = item.icon

            return (
              <motion.div
                key={item.path}
                initial={{ x: -20, opacity: 0 }}
                animate={{ x: 0, opacity: 1 }}
                transition={{ delay: index * 0.1 }}
              >
                <Link
                  to={item.path}
                  className={`nav-item ${isActive ? 'active' : ''}`}
                >
                  <Icon size={20} />
                  <span>{item.label}</span>
                  {isActive && (
                    <motion.div
                      className="nav-indicator"
                      layoutId="nav-indicator"
                      transition={{ type: 'spring', stiffness: 500, damping: 30 }}
                    />
                  )}
                </Link>
              </motion.div>
            )
          })}

          <div className="nav-divider" />
          <span className="nav-section-label">Operations</span>

          {opsNavItems.map((item, index) => {
            const isActive = location.pathname.startsWith(item.path)
            const Icon = item.icon

            return (
              <motion.div
                key={item.path}
                initial={{ x: -20, opacity: 0 }}
                animate={{ x: 0, opacity: 1 }}
                transition={{ delay: (navItems.length + index) * 0.1 }}
              >
                <Link
                  to={item.path}
                  className={`nav-item ${isActive ? 'active' : ''}`}
                >
                  <Icon size={20} />
                  <span>{item.label}</span>
                  {isActive && (
                    <motion.div
                      className="nav-indicator"
                      layoutId="nav-indicator"
                      transition={{ type: 'spring', stiffness: 500, damping: 30 }}
                    />
                  )}
                </Link>
              </motion.div>
            )
          })}
        </nav>

        <div className="sidebar-footer">
          <ThemeCycleButton />
          <div className="version-info">
            <span className="version-label">Version</span>
            <span className="version-value">{APP_INFO.VERSION}</span>
          </div>
        </div>
      </aside>

      <main className="main-content">
        <motion.div
          key={location.pathname}
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -20 }}
          transition={{ duration: 0.3 }}
        >
          {children}
        </motion.div>
      </main>
    </div>
  )
}
