import { memo } from 'react'
import {
  BaseEdge,
  EdgeLabelRenderer,
  type EdgeProps,
  getSmoothStepPath
} from '@xyflow/react'

/**
 * 라벨을 안전하게 문자열로 변환
 */
function getLabelString(label: unknown): string {
  if (typeof label === 'string') return label
  if (typeof label === 'number') return String(label)
  return ''
}

/**
 * SOTA DX/UX 커스텀 엣지 컴포넌트
 * 
 * 엣지 중앙에 라벨을 표시하고, 호버 시 강조 효과 제공
 * 라벨 배경에 그라데이션과 글로우 효과 적용
 */
export const LabeledEdge = memo(function LabeledEdge({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  label,
  data,
  style,
  markerEnd,
  selected
}: EdgeProps) {
  // 라벨 문자열 변환
  const labelStr = getLabelString(label)
  const hasLabel = labelStr.length > 0

  // smoothstep 경로 계산
  const [edgePath, labelX, labelY] = getSmoothStepPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
    borderRadius: 16
  })

  // 엣지 스타일 타입에 따른 스타일링
  const edgeStyleType = (data?.edgeStyle as string) || 'DEFAULT'
  const isDashed = edgeStyleType === 'DASHED'
  const isError = edgeStyleType === 'ERROR'

  // 라벨 스타일 결정 (타입에 따라)
  const getLabelType = (): string => {
    const lowerLabel = labelStr.toLowerCase()
    if (lowerLabel.includes('fanout')) return 'fanout'
    if (lowerLabel.includes('join') || lowerLabel.includes('brand')) return 'join'
    if (lowerLabel.includes('ref') || lowerLabel.includes('index')) return 'ref'
    return 'default'
  }

  const labelType = getLabelType()

  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        markerEnd={markerEnd}
        style={{
          stroke: isError ? '#ff4444' : isDashed ? '#8855ff' : '#4a5568',
          strokeWidth: selected ? 3 : 2,
          strokeDasharray: isDashed ? '8 4' : undefined,
          filter: selected ? 'drop-shadow(0 0 6px rgba(0, 212, 255, 0.6))' : undefined,
          transition: 'stroke 0.2s, stroke-width 0.2s, filter 0.2s',
          ...(typeof style === 'object' ? style : {})
        }}
      />
      {hasLabel && (
        <EdgeLabelRenderer>
          <div
            className={`edge-label edge-label--${labelType}`}
            style={{
              position: 'absolute',
              transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
              pointerEvents: 'all'
            }}
          >
            <div className="edge-label__content">
              <span className="edge-label__icon">{getLabelIcon(labelType)}</span>
              <span className="edge-label__text">{labelStr}</span>
            </div>
            <div className="edge-label__glow" />
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  )
})

/**
 * 라벨 타입에 따른 아이콘 반환
 */
function getLabelIcon(type: string): string {
  switch (type) {
    case 'fanout':
      return '⚡'
    case 'join':
      return '🔗'
    case 'ref':
      return '📍'
    default:
      return '→'
  }
}
