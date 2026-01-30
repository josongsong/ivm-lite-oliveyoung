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
import { edgeTypes } from '../components/edges'
import { NODE_COLORS, STATUS_COLORS } from '../model/constants'
import type { WorkflowEdge, WorkflowNode } from '../model/types'
import '../components/edges/LabeledEdge.css'
import './WorkflowCanvas.css'

interface WorkflowCanvasProps {
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
  onNodeClick: (node: WorkflowNode) => void
  selectedNodeId?: string
}

// 선택된 노드와 연결된 모든 노드 ID 찾기 (upstream + downstream)
function getConnectedNodeIds(
  selectedId: string,
  edges: Edge[]
): Set<string> {
  const connected = new Set<string>([selectedId])
  const edgeList = edges || []
  
  // BFS로 downstream 노드 찾기
  const findDownstream = (nodeId: string) => {
    edgeList.forEach(edge => {
      if (edge.source === nodeId && !connected.has(edge.target)) {
        connected.add(edge.target)
        findDownstream(edge.target)
      }
    })
  }
  
  // BFS로 upstream 노드 찾기
  const findUpstream = (nodeId: string) => {
    edgeList.forEach(edge => {
      if (edge.target === nodeId && !connected.has(edge.source)) {
        connected.add(edge.source)
        findUpstream(edge.source)
      }
    })
  }
  
  findDownstream(selectedId)
  findUpstream(selectedId)
  
  return connected
}

export function WorkflowCanvas({
  nodes: initialNodes = [],
  edges: initialEdges = [],
  onNodeClick,
  selectedNodeId
}: WorkflowCanvasProps) {
  // React Flow 기본 타입 사용 (빈 배열 기본값)
  const [nodes, setNodes, onNodesChange] = useNodesState((initialNodes || []) as Node[])
  const [edges, setEdges, onEdgesChange] = useEdgesState((initialEdges || []) as Edge[])

  // Sync with parent props
  useEffect(() => {
    setNodes((initialNodes || []) as Node[])
  }, [initialNodes, setNodes])

  useEffect(() => {
    setEdges((initialEdges || []) as Edge[])
  }, [initialEdges, setEdges])

  // 노드 클릭 핸들러
  const handleNodeClick = useCallback((_: React.MouseEvent, node: Node) => {
    onNodeClick(node as WorkflowNode)
  }, [onNodeClick])

  // 연결된 노드 ID 집합
  const connectedNodeIds = useMemo(() => {
    if (!selectedNodeId) return null
    return getConnectedNodeIds(selectedNodeId, edges)
  }, [selectedNodeId, edges])

  // 선택된 노드 하이라이트 + 연결되지 않은 노드 dimmed
  const styledNodes = useMemo(() => {
    if (!Array.isArray(nodes)) return []
    return nodes.map(node => ({
      ...node,
      selected: node.id === selectedNodeId,
      className: connectedNodeIds && !connectedNodeIds.has(node.id) ? 'dimmed' : ''
    }))
  }, [nodes, selectedNodeId, connectedNodeIds])

  // 커스텀 엣지 스타일 + 연결되지 않은 엣지 dimmed + 라벨 지원
  const styledEdges = useMemo(() => {
    if (!Array.isArray(edges)) return []
    return edges.map(edge => {
      const isConnected = !connectedNodeIds || 
        (connectedNodeIds.has(edge.source) && connectedNodeIds.has(edge.target))
      
      // 원본 엣지 데이터에서 스타일 타입과 라벨 가져오기
      const originalEdge = edge as WorkflowEdge
      const edgeStyle = originalEdge.edgeStyle || 'DEFAULT'
      const hasLabel = Boolean(originalEdge.label)
      
      return {
        ...edge,
        // 라벨이 있으면 labeled 타입 사용, 아니면 smoothstep
        type: hasLabel ? 'labeled' : (edge.type || 'smoothstep'),
        // 커스텀 엣지에서 사용할 데이터
        data: {
          ...edge.data,
          edgeStyle: edgeStyle
        },
        style: {
          stroke: isConnected 
            ? (edgeStyle === 'DASHED' ? '#8855ff' : edge.animated ? '#00d4ff' : '#4a5568') 
            : '#1a1a1a',
          strokeWidth: isConnected ? 2 : 1,
          strokeDasharray: edgeStyle === 'DASHED' ? '8 4' : undefined,
          opacity: isConnected ? 1 : 0.15
        },
        animated: isConnected && edge.animated,
        // 연결되지 않은 엣지는 라벨 숨기기
        label: isConnected ? originalEdge.label : undefined
      }
    })
  }, [edges, connectedNodeIds])

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
        edgeTypes={edgeTypes}
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
