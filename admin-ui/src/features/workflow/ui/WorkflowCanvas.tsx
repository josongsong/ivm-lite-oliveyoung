import { useCallback, useEffect, useMemo } from 'react'
import {
  Background,
  BackgroundVariant,
  Controls,
  type Edge,
  MiniMap,
  type Node,
  Panel,
  ReactFlow,
  useEdgesState,
  useNodesState
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { nodeTypes } from '../components/nodes'
import { NODE_COLORS, STATUS_COLORS } from '../model/constants'
import type { WorkflowEdge, WorkflowNode } from '../model/types'
import './WorkflowCanvas.css'

interface WorkflowCanvasProps {
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
  onNodeClick: (node: WorkflowNode) => void
  selectedNodeId?: string
}

export function WorkflowCanvas({
  nodes: initialNodes,
  edges: initialEdges,
  onNodeClick,
  selectedNodeId
}: WorkflowCanvasProps) {
  // React Flow 기본 타입 사용
  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes as Node[])
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges as Edge[])

  // Sync with parent props
  useEffect(() => {
    setNodes(initialNodes as Node[])
  }, [initialNodes, setNodes])

  useEffect(() => {
    setEdges(initialEdges as Edge[])
  }, [initialEdges, setEdges])

  // 노드 클릭 핸들러
  const handleNodeClick = useCallback((_: React.MouseEvent, node: Node) => {
    onNodeClick(node as WorkflowNode)
  }, [onNodeClick])

  // 선택된 노드 하이라이트
  const styledNodes = useMemo(() => {
    return nodes.map(node => ({
      ...node,
      selected: node.id === selectedNodeId
    }))
  }, [nodes, selectedNodeId])

  // 커스텀 엣지 스타일
  const styledEdges = useMemo(() => {
    return edges.map(edge => ({
      ...edge,
      style: {
        stroke: edge.animated ? '#00d4ff' : '#333',
        strokeWidth: 2
      },
      animated: edge.animated
    }))
  }, [edges])

  // 미니맵 노드 색상
  const minimapNodeColor = useCallback((node: Node) => {
    const colors = NODE_COLORS[node.type || 'rawdata']
    return colors?.border || '#666'
  }, [])

  return (
    <div className="workflow-canvas">
      <ReactFlow
        nodes={styledNodes}
        edges={styledEdges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onNodeClick={handleNodeClick}
        nodeTypes={nodeTypes}
        fitView
        fitViewOptions={{ padding: 0.2 }}
        minZoom={0.1}
        maxZoom={2}
        defaultViewport={{ x: 0, y: 0, zoom: 0.8 }}
        proOptions={{ hideAttribution: true }}
      >
        <Background
          variant={BackgroundVariant.Dots}
          gap={20}
          size={1}
          color="rgba(255, 255, 255, 0.05)"
        />

        <Controls
          className="workflow-controls"
          showInteractive={false}
        />

        <MiniMap
          className="workflow-minimap"
          nodeColor={minimapNodeColor}
          maskColor="rgba(0, 0, 0, 0.8)"
          pannable
          zoomable
        />

        <Panel position="bottom-left" className="workflow-legend">
          <div className="legend-section">
            <div className="legend-title">Node Types</div>
            <div className="legend-items">
              {[
                { type: 'rawdata', label: 'RawData' },
                { type: 'slice', label: 'Slice' },
                { type: 'view', label: 'View' },
                { type: 'sink', label: 'Sink' },
                { type: 'ruleset', label: 'Rule' }
              ].map(({ type, label }) => (
                <div key={type} className="legend-item">
                  <span
                    className="legend-dot"
                    style={{ backgroundColor: NODE_COLORS[type]?.border }}
                  />
                  <span className="legend-label">{label}</span>
                </div>
              ))}
            </div>
          </div>
          <div className="legend-section">
            <div className="legend-title">Status</div>
            <div className="legend-items">
              {Object.entries(STATUS_COLORS).map(([status, color]) => (
                <div key={status} className="legend-item">
                  <span className="legend-ring" style={{ borderColor: color }} />
                  <span className="legend-label">{status}</span>
                </div>
              ))}
            </div>
          </div>
        </Panel>
      </ReactFlow>
    </div>
  )
}
