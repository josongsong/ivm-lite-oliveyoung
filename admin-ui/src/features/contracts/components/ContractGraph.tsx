import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Background,
  BackgroundVariant,
  Controls,
  type Edge,
  MiniMap,
  type Node,
  Panel,
  ReactFlow,
  ReactFlowProvider,
  useEdgesState,
  useNodesState,
  useReactFlow,
} from '@xyflow/react'
import dagre from 'dagre'
import { LayoutGrid, Shuffle } from 'lucide-react'
import '@xyflow/react/dist/style.css'
import type { GraphEdge as ApiGraphEdge, GraphNode as ApiGraphNode, GraphResponse } from '@/shared/types'
import { Button } from '@/shared/ui'
import './ContractGraph.css'

// 레이아웃 방향 타입
type LayoutDirection = 'TB' | 'LR'

// 노드 타입별 색상
const NODE_COLORS: Record<string, { bg: string; border: string; text: string }> = {
  ENTITY_SCHEMA: { bg: 'rgba(0, 212, 255, 0.15)', border: '#00d4ff', text: '#00d4ff' },
  RULESET: { bg: 'rgba(136, 85, 255, 0.15)', border: '#8855ff', text: '#8855ff' },
  VIEW_DEFINITION: { bg: 'rgba(0, 255, 136, 0.15)', border: '#00ff88', text: '#00ff88' },
  SINKRULE: { bg: 'rgba(255, 170, 0, 0.15)', border: '#ffaa00', text: '#ffaa00' },
  SLICE: { bg: 'rgba(255, 136, 136, 0.15)', border: '#ff8888', text: '#ff8888' },
}

// 엣지 타입별 색상
const EDGE_COLORS: Record<string, string> = {
  DEFINES: '#00d4ff',
  PRODUCES: '#8855ff',
  REQUIRES: '#00ff88',
  CONSUMES: '#ffaa00',
}

// 노드 크기
const NODE_WIDTH = 160
const NODE_HEIGHT = 60

interface ContractGraphProps {
  data: GraphResponse
  onNodeClick?: (nodeId: string, kind: string) => void
  selectedNodeId?: string
}

/**
 * API 응답을 React Flow 노드/엣지로 변환
 */
function transformGraphData(data: GraphResponse): { nodes: Node[]; edges: Edge[] } {
  const nodes: Node[] = data.nodes.map((node: ApiGraphNode) => ({
    id: node.id,
    type: 'default',
    position: { x: 0, y: 0 },
    data: {
      label: node.label,
      kind: node.kind,
      entityType: node.entityType,
      status: node.status,
      metadata: node.metadata,
    },
    style: {
      background: NODE_COLORS[node.kind]?.bg || 'rgba(100, 100, 100, 0.15)',
      border: `2px solid ${NODE_COLORS[node.kind]?.border || '#666'}`,
      borderRadius: '8px',
      padding: '10px 16px',
      color: NODE_COLORS[node.kind]?.text || '#fff',
      fontSize: '12px',
      fontWeight: 600,
      minWidth: NODE_WIDTH,
      textAlign: 'center' as const,
    },
  }))

  const edges: Edge[] = data.edges.map((edge: ApiGraphEdge) => ({
    id: edge.id,
    source: edge.from,
    target: edge.to,
    type: 'smoothstep',
    animated: edge.kind === 'PRODUCES',
    label: edge.label,
    labelStyle: { fill: '#888', fontSize: 10 },
    style: {
      stroke: edge.kind ? EDGE_COLORS[edge.kind] ?? '#666' : '#666',
      strokeWidth: 2,
    },
  }))

  return { nodes, edges }
}

/**
 * Dagre 기반 자동 레이아웃
 */
function calculateLayout(
  nodes: Node[],
  edges: Edge[],
  direction: LayoutDirection
): { nodes: Node[]; edges: Edge[] } {
  if (!nodes.length) return { nodes: [], edges: [] }

  const dagreGraph = new dagre.graphlib.Graph()
  dagreGraph.setDefaultEdgeLabel(() => ({}))
  dagreGraph.setGraph({
    rankdir: direction,
    nodesep: 80,
    ranksep: 120,
    marginx: 50,
    marginy: 50,
  })

  nodes.forEach((node) => {
    dagreGraph.setNode(node.id, { width: NODE_WIDTH, height: NODE_HEIGHT })
  })

  edges.forEach((edge) => {
    dagreGraph.setEdge(edge.source, edge.target)
  })

  dagre.layout(dagreGraph)

  const layoutedNodes = nodes.map((node) => {
    const nodeWithPosition = dagreGraph.node(node.id)
    return {
      ...node,
      position: {
        x: nodeWithPosition.x - NODE_WIDTH / 2,
        y: nodeWithPosition.y - NODE_HEIGHT / 2,
      },
    }
  })

  return { nodes: layoutedNodes, edges }
}

/**
 * 선택된 노드와 연결된 노드 ID 찾기
 */
function getConnectedNodeIds(selectedId: string, edges: Edge[]): Set<string> {
  const connected = new Set<string>([selectedId])

  const findDownstream = (nodeId: string) => {
    edges.forEach(edge => {
      if (edge.source === nodeId && !connected.has(edge.target)) {
        connected.add(edge.target)
        findDownstream(edge.target)
      }
    })
  }

  const findUpstream = (nodeId: string) => {
    edges.forEach(edge => {
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

function ContractGraphInner({ data, onNodeClick, selectedNodeId }: ContractGraphProps) {
  const [layoutDirection, setLayoutDirection] = useState<LayoutDirection>('TB')
  const { fitView } = useReactFlow()

  // 데이터 변환
  const { nodes: rawNodes, edges: rawEdges } = useMemo(() => transformGraphData(data), [data])

  // 레이아웃 계산
  const { nodes: layoutedNodes, edges: layoutedEdges } = useMemo(
    () => calculateLayout(rawNodes, rawEdges, layoutDirection),
    [rawNodes, rawEdges, layoutDirection]
  )

  const [nodes, setNodes, onNodesChange] = useNodesState(layoutedNodes)
  const [edges, setEdges, onEdgesChange] = useEdgesState(layoutedEdges)

  // 레이아웃 변경 시 업데이트
  useEffect(() => {
    setNodes(layoutedNodes)
    setEdges(layoutedEdges)
    setTimeout(() => fitView({ padding: 0.2, duration: 300 }), 50)
  }, [layoutedNodes, layoutedEdges, setNodes, setEdges, fitView])

  const toggleLayout = useCallback(() => {
    setLayoutDirection(prev => prev === 'TB' ? 'LR' : 'TB')
  }, [])

  const handleNodeClick = useCallback((_: React.MouseEvent, node: Node) => {
    onNodeClick?.(node.id, node.data.kind as string)
  }, [onNodeClick])

  // 연결된 노드 계산
  const connectedNodeIds = useMemo(() => {
    if (!selectedNodeId) return null
    return getConnectedNodeIds(selectedNodeId, edges)
  }, [selectedNodeId, edges])

  // 노드 스타일링
  const styledNodes = useMemo(() => {
    return nodes.map(node => ({
      ...node,
      selected: node.id === selectedNodeId,
      className: connectedNodeIds && !connectedNodeIds.has(node.id) ? 'dimmed' : '',
    }))
  }, [nodes, selectedNodeId, connectedNodeIds])

  // 엣지 스타일링
  const styledEdges = useMemo(() => {
    return edges.map(edge => {
      const isConnected = !connectedNodeIds ||
        (connectedNodeIds.has(edge.source) && connectedNodeIds.has(edge.target))
      return {
        ...edge,
        style: {
          ...edge.style,
          opacity: isConnected ? 1 : 0.15,
          strokeWidth: isConnected ? 2 : 1,
        },
        animated: isConnected && edge.animated,
      }
    })
  }, [edges, connectedNodeIds])

  const minimapNodeColor = useCallback((node: Node) => {
    return NODE_COLORS[node.data?.kind as string]?.border || '#666'
  }, [])

  return (
    <ReactFlow
      nodes={styledNodes}
      edges={styledEdges}
      onNodesChange={onNodesChange}
      onEdgesChange={onEdgesChange}
      onNodeClick={handleNodeClick}
      fitView
      fitViewOptions={{ padding: 0.2 }}
      minZoom={0.1}
      maxZoom={2}
      proOptions={{ hideAttribution: true }}
    >
      <Background
        variant={BackgroundVariant.Dots}
        gap={20}
        size={1}
        color="rgba(255, 255, 255, 0.05)"
      />

      <Controls className="contract-graph-controls" showInteractive={false} />

      <MiniMap
        className="contract-graph-minimap"
        nodeColor={minimapNodeColor}
        maskColor="rgba(0, 0, 0, 0.8)"
        pannable
        zoomable
      />

      <Panel position="top-right" className="layout-controls">
        <Button variant="secondary" size="sm" onClick={toggleLayout} icon={layoutDirection === 'TB' ? <LayoutGrid size={16} /> : <Shuffle size={16} />}>
          {layoutDirection === 'TB' ? '↓ Vertical' : '→ Horizontal'}
        </Button>
      </Panel>

      <Panel position="bottom-left" className="contract-graph-legend">
        <div className="legend-title">Contract Types</div>
        <div className="legend-items">
          {Object.entries(NODE_COLORS).map(([kind, colors]) => (
            <div key={kind} className="legend-item">
              <span className="legend-dot" style={{ backgroundColor: colors.border }} />
              <span className="legend-label">{kind.replace('_', ' ')}</span>
            </div>
          ))}
        </div>
      </Panel>
    </ReactFlow>
  )
}

export function ContractGraph(props: ContractGraphProps) {
  return (
    <div className="contract-graph-container">
      <ReactFlowProvider>
        <ContractGraphInner {...props} />
      </ReactFlowProvider>
    </div>
  )
}
