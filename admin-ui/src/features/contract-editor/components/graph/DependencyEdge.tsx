/**
 * DependencyEdge Component (Phase 4: Impact Graph)
 *
 * Contract 간 의존성 엣지.
 */
import { memo } from 'react'
import {
  BaseEdge,
  EdgeLabelRenderer,
  type EdgeProps,
  getBezierPath,
} from '@xyflow/react'
import type { DependencyEdgeData } from './types'
import './DependencyEdge.css'

type DependencyEdgeProps = EdgeProps & {
  data?: DependencyEdgeData
}

export const DependencyEdge = memo(function DependencyEdge(props: DependencyEdgeProps) {
  const {
    id,
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
    data,
    selected,
  } = props
  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
  })

  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        className={`dependency-edge ${selected ? 'dependency-edge--selected' : ''}`}
      />
      {data?.label ? <EdgeLabelRenderer>
          <div
            className="dependency-edge__label"
            style={{
              transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
            }}
          >
            {data.label}
          </div>
        </EdgeLabelRenderer> : null}
    </>
  )
})
