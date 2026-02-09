import { motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import { ArrowRight, FileCode2, Layers, Play, Search } from 'lucide-react'

interface ActionsPanelProps {
  worker: {
    processed: number
    failed: number
    polls: number
  }
}

export function ActionsPanel({ worker }: ActionsPanelProps) {
  return (
    <motion.div
      className="actions-panel"
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ delay: 0.2 }}
    >
      <div className="panel-header">
        <Play size={18} />
        <h3>Quick Actions</h3>
      </div>

      <div className="quick-actions">
        <Link to="/explorer" className="action-card">
          <Search size={24} />
          <div>
            <span className="action-title">Data Explorer</span>
            <span className="action-desc">RawData / Slice 검색</span>
          </div>
          <ArrowRight size={16} />
        </Link>

        <Link to="/playground" className="action-card">
          <Play size={24} />
          <div>
            <span className="action-title">Playground</span>
            <span className="action-desc">데이터 즉시 테스트</span>
          </div>
          <ArrowRight size={16} />
        </Link>

        <Link to="/contracts" className="action-card">
          <FileCode2 size={24} />
          <div>
            <span className="action-title">Contracts</span>
            <span className="action-desc">스키마/룰셋 관리</span>
          </div>
          <ArrowRight size={16} />
        </Link>

        <Link to="/pipeline" className="action-card">
          <Layers size={24} />
          <div>
            <span className="action-title">Pipeline</span>
            <span className="action-desc">데이터 흐름 확인</span>
          </div>
          <ArrowRight size={16} />
        </Link>
      </div>

      <div className="worker-details">
        <div className="worker-detail">
          <span className="detail-label">처리 완료</span>
          <span className="detail-value">{worker.processed.toLocaleString()}</span>
        </div>
        <div className="worker-detail">
          <span className="detail-label">실패</span>
          <span className="detail-value error">{worker.failed.toLocaleString()}</span>
        </div>
        <div className="worker-detail">
          <span className="detail-label">Polls</span>
          <span className="detail-value">{worker.polls.toLocaleString()}</span>
        </div>
      </div>
    </motion.div>
  )
}
