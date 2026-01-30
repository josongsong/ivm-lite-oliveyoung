import { useState } from 'react'
import { motion } from 'framer-motion'
import {
  AlertTriangle,
  Check,
  ChevronDown,
  ChevronRight,
  Code,
  Copy,
  Database,
  Globe,
  Tag,
  X
} from 'lucide-react'
import type { SpanDetail } from '@/shared/types'
import { formatDuration, formatTime } from '@/shared/ui/formatters'
import './SpanDetails.css'

interface SpanDetailsProps {
  span: SpanDetail
  onClose?: () => void
}

export function SpanDetails({ span, onClose }: SpanDetailsProps) {
  const [expandedSections, setExpandedSections] = useState<Set<string>>(
    new Set(['basic', 'http', 'error'])
  )
  const [copiedField, setCopiedField] = useState<string | null>(null)

  const toggleSection = (section: string) => {
    setExpandedSections(prev => {
      const next = new Set(prev)
      if (next.has(section)) {
        next.delete(section)
      } else {
        next.add(section)
      }
      return next
    })
  }

  const copyToClipboard = async (value: string, field: string) => {
    await navigator.clipboard.writeText(value)
    setCopiedField(field)
    setTimeout(() => setCopiedField(null), 2000)
  }

  const isSectionExpanded = (section: string) => expandedSections.has(section)

  return (
    <motion.div
      className="span-details-panel"
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: 20 }}
    >
      {/* Header */}
      <div className="details-header">
        <div className="details-header-left">
          <span className={`status-dot ${span.hasError ? 'error' : 'success'}`} />
          <h3 className="details-title">{span.name}</h3>
        </div>
        <div className="details-header-right">
          <span className="details-duration">{formatDuration(span.duration)}</span>
          {onClose && (
            <button className="close-btn" onClick={onClose}>
              <X size={16} />
            </button>
          )}
        </div>
      </div>

      {/* 태그 바 */}
      <div className="details-tags">
        <span className="tag type-tag">{span.type}</span>
        <span className="tag service-tag">{span.service}</span>
        {span.hasError && <span className="tag error-tag">Error</span>}
      </div>

      {/* Content */}
      <div className="details-content">
        {/* 기본 정보 */}
        <CollapsibleSection
          title="기본 정보"
          icon={<Code size={14} />}
          isExpanded={isSectionExpanded('basic')}
          onToggle={() => toggleSection('basic')}
        >
          <div className="info-grid">
            <InfoRow 
              label="Span ID" 
              value={span.spanId} 
              mono 
              copyable
              onCopy={() => copyToClipboard(span.spanId, 'spanId')}
              copied={copiedField === 'spanId'}
            />
            {span.parentId && (
              <InfoRow 
                label="Parent ID" 
                value={span.parentId} 
                mono 
                copyable
                onCopy={() => copyToClipboard(span.parentId!, 'parentId')}
                copied={copiedField === 'parentId'}
              />
            )}
            <InfoRow label="Service" value={span.service} />
            <InfoRow label="Type" value={span.type} />
            <InfoRow 
              label="Duration" 
              value={formatDuration(span.duration)} 
              highlight 
            />
            <InfoRow 
              label="Start Time" 
              value={formatTime(new Date(span.startTime * 1000).toISOString())} 
              mono 
            />
            <InfoRow 
              label="End Time" 
              value={formatTime(new Date(span.endTime * 1000).toISOString())} 
              mono 
            />
          </div>
        </CollapsibleSection>

        {/* HTTP 정보 */}
        {span.http && (
          <CollapsibleSection
            title="HTTP"
            icon={<Globe size={14} />}
            isExpanded={isSectionExpanded('http')}
            onToggle={() => toggleSection('http')}
          >
            <div className="info-grid">
              {span.http.method && (
                <InfoRow 
                  label="Method" 
                  value={span.http.method}
                  badge
                  badgeColor={getMethodColor(span.http.method)}
                />
              )}
              {span.http.url && (
                <InfoRow 
                  label="URL" 
                  value={span.http.url} 
                  mono 
                  copyable
                  onCopy={() => copyToClipboard(span.http!.url!, 'url')}
                  copied={copiedField === 'url'}
                />
              )}
              {span.http.status && (
                <InfoRow 
                  label="Status" 
                  value={span.http.status.toString()}
                  badge
                  badgeColor={getStatusColor(span.http.status)}
                />
              )}
            </div>
          </CollapsibleSection>
        )}

        {/* 에러 정보 */}
        {span.hasError && (
          <CollapsibleSection
            title="Error"
            icon={<AlertTriangle size={14} />}
            isExpanded={isSectionExpanded('error')}
            onToggle={() => toggleSection('error')}
            error
          >
            <div className="error-content">
              {span.errorMessage ? (
                <pre className="error-message">{span.errorMessage}</pre>
              ) : (
                <p className="error-placeholder">에러 메시지가 없습니다</p>
              )}
            </div>
          </CollapsibleSection>
        )}

        {/* Annotations */}
        {span.annotations && Object.keys(span.annotations).length > 0 && (
          <CollapsibleSection
            title="Annotations"
            icon={<Tag size={14} />}
            isExpanded={isSectionExpanded('annotations')}
            onToggle={() => toggleSection('annotations')}
            count={Object.keys(span.annotations).length}
          >
            <div className="kv-list">
              {Object.entries(span.annotations).map(([key, value]) => (
                <div key={key} className="kv-item">
                  <span className="kv-key">{key}</span>
                  <span className="kv-value">{value}</span>
                </div>
              ))}
            </div>
          </CollapsibleSection>
        )}

        {/* Metadata */}
        {span.metadata && Object.keys(span.metadata).length > 0 && (
          <CollapsibleSection
            title="Metadata"
            icon={<Database size={14} />}
            isExpanded={isSectionExpanded('metadata')}
            onToggle={() => toggleSection('metadata')}
            count={Object.keys(span.metadata).length}
          >
            <div className="kv-list">
              {Object.entries(span.metadata).map(([key, value]) => (
                <div key={key} className="kv-item">
                  <span className="kv-key">{key}</span>
                  <span className="kv-value mono">{value}</span>
                </div>
              ))}
            </div>
          </CollapsibleSection>
        )}
      </div>
    </motion.div>
  )
}

// Collapsible Section Component
interface CollapsibleSectionProps {
  title: string
  icon: React.ReactNode
  isExpanded: boolean
  onToggle: () => void
  children: React.ReactNode
  count?: number
  error?: boolean
}

function CollapsibleSection({
  title,
  icon,
  isExpanded,
  onToggle,
  children,
  count,
  error
}: CollapsibleSectionProps) {
  return (
    <div className={`collapsible-section ${error ? 'error' : ''}`}>
      <button className="section-header" onClick={onToggle}>
        <span className="section-icon">{icon}</span>
        <span className="section-title">{title}</span>
        {count !== undefined && (
          <span className="section-count">{count}</span>
        )}
        <span className="section-chevron">
          {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
        </span>
      </button>
      {isExpanded && (
        <motion.div 
          className="section-content"
          initial={{ opacity: 0, height: 0 }}
          animate={{ opacity: 1, height: 'auto' }}
          exit={{ opacity: 0, height: 0 }}
        >
          {children}
        </motion.div>
      )}
    </div>
  )
}

// Info Row Component
interface InfoRowProps {
  label: string
  value: string
  mono?: boolean
  highlight?: boolean
  badge?: boolean
  badgeColor?: string
  copyable?: boolean
  onCopy?: () => void
  copied?: boolean
}

function InfoRow({ 
  label, 
  value, 
  mono, 
  highlight,
  badge,
  badgeColor,
  copyable,
  onCopy,
  copied
}: InfoRowProps) {
  return (
    <div className="info-row">
      <span className="info-label">{label}</span>
      <div className="info-value-wrapper">
        {badge ? (
          <span 
            className="info-badge" 
            style={{ backgroundColor: badgeColor }}
          >
            {value}
          </span>
        ) : (
          <span className={`info-value ${mono ? 'mono' : ''} ${highlight ? 'highlight' : ''}`}>
            {value}
          </span>
        )}
        {copyable && onCopy && (
          <button className="copy-btn" onClick={onCopy}>
            {copied ? <Check size={12} /> : <Copy size={12} />}
          </button>
        )}
      </div>
    </div>
  )
}

// Helper functions
function getMethodColor(method: string): string {
  switch (method.toUpperCase()) {
    case 'GET': return 'var(--accent-green)'
    case 'POST': return 'var(--accent-cyan)'
    case 'PUT': return 'var(--accent-orange)'
    case 'DELETE': return 'var(--status-error)'
    case 'PATCH': return 'var(--accent-purple)'
    default: return 'var(--text-muted)'
  }
}

function getStatusColor(status: number): string {
  if (status >= 500) return 'var(--status-error)'
  if (status >= 400) return 'var(--accent-orange)'
  if (status >= 300) return 'var(--accent-cyan)'
  if (status >= 200) return 'var(--accent-green)'
  return 'var(--text-muted)'
}
