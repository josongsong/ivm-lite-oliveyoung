import { useCallback, useMemo, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { 
  AlertCircle, 
  ChevronDown, 
  ChevronRight, 
  Clock, 
  Database, 
  Globe,
  Layers,
  Server,
  Zap
} from 'lucide-react'
import type { SpanDetail, TraceDetail } from '@/shared/types'
import { formatDuration } from '@/shared/ui/formatters'
import './WaterfallTimeline.css'

interface WaterfallTimelineProps {
  trace: TraceDetail
  selectedSpanId: string | null
  onSpanSelect: (spanId: string) => void
}

interface TreeNode {
  span: SpanDetail & { relativeStart: number; relativeEnd: number }
  children: TreeNode[]
  depth: number
  isExpanded: boolean
}

// 서비스/타입별 색상 - Datadog 스타일
const SERVICE_COLORS: Record<string, { bg: string; border: string; text: string }> = {
  'http': { bg: 'rgba(99, 179, 237, 0.3)', border: '#63b3ed', text: '#63b3ed' },
  'database': { bg: 'rgba(72, 187, 120, 0.3)', border: '#48bb78', text: '#48bb78' },
  'aws': { bg: 'rgba(246, 173, 85, 0.3)', border: '#f6ad55', text: '#f6ad55' },
  'remote': { bg: 'rgba(237, 100, 166, 0.3)', border: '#ed64a6', text: '#ed64a6' },
  'segment': { bg: 'rgba(160, 174, 192, 0.3)', border: '#a0aec0', text: '#a0aec0' },
  'subsegment': { bg: 'rgba(129, 140, 248, 0.3)', border: '#818cf8', text: '#818cf8' },
  'error': { bg: 'rgba(245, 101, 101, 0.3)', border: '#f56565', text: '#f56565' },
}

const getSpanColor = (span: SpanDetail) => {
  if (span.hasError) return SERVICE_COLORS.error
  const type = span.type?.toLowerCase() || 'segment'
  return SERVICE_COLORS[type] || SERVICE_COLORS.segment
}

const getSpanIcon = (span: SpanDetail) => {
  if (span.hasError) return <AlertCircle size={12} />
  const type = span.type?.toLowerCase() || ''
  switch (type) {
    case 'database': return <Database size={12} />
    case 'http': return <Globe size={12} />
    case 'aws': return <Server size={12} />
    case 'remote': return <Zap size={12} />
    default: return <Layers size={12} />
  }
}

export function WaterfallTimeline({
  trace,
  selectedSpanId,
  onSpanSelect,
}: WaterfallTimelineProps) {
  const [expandedNodes, setExpandedNodes] = useState<Set<string>>(new Set())
  const [hoveredSpanId, setHoveredSpanId] = useState<string | null>(null)

  // 트레이스 시작 시간 기준으로 정규화
  const { normalizedSpans, totalDuration } = useMemo(() => {
    if (!trace.segments?.length) {
      return { normalizedSpans: [], totalDuration: 0 }
    }

    const minT = Math.min(...trace.segments.map(s => s.startTime))
    const maxT = Math.max(...trace.segments.map(s => s.endTime))
    const totalDuration = (maxT - minT) * 1000 // ms

    const normalized = trace.segments.map((span) => ({
      ...span,
      relativeStart: (span.startTime - minT) * 1000,
      relativeEnd: (span.endTime - minT) * 1000,
    }))

    return { normalizedSpans: normalized, totalDuration }
  }, [trace])

  // 트리 구조 생성
  const spanTree = useMemo(() => {
    if (!normalizedSpans.length) return []
    
    const _spanMap = new Map(normalizedSpans.map(s => [s.spanId, s])); void _spanMap;
    const rootNodes: TreeNode[] = []
    const nodeMap = new Map<string, TreeNode>()
    
    // 모든 노드 생성
    normalizedSpans.forEach(span => {
      nodeMap.set(span.spanId, {
        span,
        children: [],
        depth: 0,
        isExpanded: true
      })
    })
    
    // 부모-자식 관계 설정
    normalizedSpans.forEach(span => {
      const node = nodeMap.get(span.spanId)!
      if (span.parentId && nodeMap.has(span.parentId)) {
        const parentNode = nodeMap.get(span.parentId)!
        parentNode.children.push(node)
        node.depth = parentNode.depth + 1
      } else {
        rootNodes.push(node)
      }
    })
    
    // 깊이 재계산 (재귀)
    const calculateDepth = (node: TreeNode, depth: number) => {
      node.depth = depth
      node.children.forEach(child => calculateDepth(child, depth + 1))
    }
    rootNodes.forEach(node => calculateDepth(node, 0))
    
    // 시작 시간순 정렬
    const sortNodes = (nodes: TreeNode[]) => {
      nodes.sort((a, b) => a.span.relativeStart - b.span.relativeStart)
      nodes.forEach(node => sortNodes(node.children))
    }
    sortNodes(rootNodes)
    
    return rootNodes
  }, [normalizedSpans])

  // 플랫 리스트로 변환 (확장된 노드만)
  const flattenedSpans = useMemo(() => {
    const result: Array<{ node: TreeNode; hasChildren: boolean }> = []
    
    const flatten = (nodes: TreeNode[]) => {
      nodes.forEach(node => {
        const hasChildren = node.children.length > 0
        result.push({ node, hasChildren })
        
        if (hasChildren && (expandedNodes.size === 0 || expandedNodes.has(node.span.spanId))) {
          flatten(node.children)
        }
      })
    }
    
    flatten(spanTree)
    return result
  }, [spanTree, expandedNodes])

  // 노드 확장/축소 토글
  const toggleNode = useCallback((spanId: string, e: React.MouseEvent) => {
    e.stopPropagation()
    setExpandedNodes(prev => {
      const next = new Set(prev)
      if (next.has(spanId)) {
        next.delete(spanId)
      } else {
        next.add(spanId)
      }
      return next
    })
  }, [])

  // 전체 확장/축소
  const expandAll = useCallback(() => {
    const allIds = new Set(normalizedSpans.map(s => s.spanId))
    setExpandedNodes(allIds)
  }, [normalizedSpans])

  const collapseAll = useCallback(() => {
    setExpandedNodes(new Set())
  }, [])

  // 타임라인 스케일 마커
  const scaleMarkers = useMemo(() => {
    if (totalDuration <= 0) return []
    
    const markerCount = 5
    return Array.from({ length: markerCount + 1 }, (_, i) => {
      const percent = (i / markerCount) * 100
      const time = (totalDuration * i) / markerCount
      return { percent, time }
    })
  }, [totalDuration])

  if (!trace.segments?.length) {
    return (
      <div className="waterfall-empty">
        <Clock size={32} className="empty-icon" />
        <p>스팬 데이터가 없습니다</p>
      </div>
    )
  }

  return (
    <div className="waterfall-container">
      {/* 헤더: 통계 + 컨트롤 */}
      <div className="waterfall-header">
        <div className="waterfall-stats">
          <div className="stat-item">
            <span className="stat-label">총 스팬</span>
            <span className="stat-value">{trace.segments.length}</span>
          </div>
          <div className="stat-item">
            <span className="stat-label">총 시간</span>
            <span className="stat-value">{formatDuration(totalDuration)}</span>
          </div>
          <div className="stat-item error">
            <span className="stat-label">에러</span>
            <span className="stat-value">
              {trace.segments.filter(s => s.hasError).length}
            </span>
          </div>
        </div>
        <div className="waterfall-controls">
          <button className="control-btn" onClick={expandAll}>
            모두 펼치기
          </button>
          <button className="control-btn" onClick={collapseAll}>
            모두 접기
          </button>
        </div>
      </div>

      {/* 타임라인 스케일 */}
      <div className="timeline-scale">
        <div className="scale-label-area"></div>
        <div className="scale-track">
          {scaleMarkers.map(({ percent, time }) => (
            <div 
              key={percent} 
              className="scale-marker"
              style={{ left: `${percent}%` }}
            >
              <span className="scale-time">{formatDuration(time)}</span>
              <div className="scale-line" />
            </div>
          ))}
        </div>
      </div>

      {/* 스팬 목록 */}
      <div className="waterfall-content">
        <AnimatePresence mode="popLayout">
          {flattenedSpans.map(({ node, hasChildren }) => {
            const { span } = node
            const color = getSpanColor(span)
            const isSelected = selectedSpanId === span.spanId
            const isHovered = hoveredSpanId === span.spanId
            const isExpanded = expandedNodes.size === 0 || expandedNodes.has(span.spanId)
            
            // 바 위치/크기 계산
            const barWidth = Math.max(
              ((span.relativeEnd - span.relativeStart) / totalDuration) * 100,
              0.5 // 최소 너비
            )
            const barLeft = (span.relativeStart / totalDuration) * 100

            return (
              <motion.div
                key={span.spanId}
                className={`span-row ${isSelected ? 'selected' : ''} ${span.hasError ? 'error' : ''}`}
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
                transition={{ duration: 0.15 }}
                onClick={() => onSpanSelect(span.spanId)}
                onMouseEnter={() => setHoveredSpanId(span.spanId)}
                onMouseLeave={() => setHoveredSpanId(null)}
              >
                {/* 스팬 레이블 */}
                <div 
                  className="span-label"
                  style={{ paddingLeft: `${node.depth * 16 + 8}px` }}
                >
                  {/* 확장/축소 버튼 */}
                  <button 
                    className={`expand-btn ${hasChildren ? 'visible' : ''}`}
                    onClick={(e) => toggleNode(span.spanId, e)}
                  >
                    {hasChildren && (
                      isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />
                    )}
                  </button>
                  
                  {/* 아이콘 */}
                  <span className="span-icon" style={{ color: color.text }}>
                    {getSpanIcon(span)}
                  </span>
                  
                  {/* 이름 */}
                  <span className="span-name" title={span.name}>
                    {span.name}
                  </span>
                  
                  {/* 서비스 태그 */}
                  <span 
                    className="span-service-tag"
                    style={{ 
                      backgroundColor: color.bg,
                      borderColor: color.border,
                      color: color.text
                    }}
                  >
                    {span.type}
                  </span>
                </div>

                {/* 타임라인 바 */}
                <div className="span-timeline">
                  <div 
                    className="span-bar"
                    style={{
                      left: `${barLeft}%`,
                      width: `${barWidth}%`,
                      backgroundColor: color.bg,
                      borderColor: color.border,
                      boxShadow: isSelected || isHovered 
                        ? `0 0 12px ${color.border}` 
                        : 'none'
                    }}
                  >
                    <span className="span-duration">
                      {formatDuration(span.duration)}
                    </span>
                  </div>
                </div>
              </motion.div>
            )
          })}
        </AnimatePresence>
      </div>

      {/* 미니맵 (선택적) */}
      <div className="waterfall-minimap">
        {normalizedSpans.map(span => {
          const color = getSpanColor(span)
          const left = (span.relativeStart / totalDuration) * 100
          const width = Math.max(((span.relativeEnd - span.relativeStart) / totalDuration) * 100, 0.5)
          
          return (
            <div
              key={span.spanId}
              className={`minimap-bar ${selectedSpanId === span.spanId ? 'selected' : ''}`}
              style={{
                left: `${left}%`,
                width: `${width}%`,
                backgroundColor: color.border
              }}
              onClick={() => onSpanSelect(span.spanId)}
            />
          )
        })}
      </div>
    </div>
  )
}
