/**
 * ImpactGraph Component (Phase 4: Impact Graph)
 *
 * Contract 변경 영향 그래프 시각화.
 */
import { useCallback, useEffect, useState } from 'react'
import {
  Background,
  BackgroundVariant,
  Controls,
  type EdgeTypes,
  MiniMap,
  type NodeTypes,
  ReactFlow,
  useEdgesState,
  useNodesState,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import {
  ContractNode,
  DependencyEdge,
  GraphControlPanel,
  GraphLegend,
  useGraphLayout,
} from './graph'
import type { DepthLevel, FilterMode, ImpactLevel, ImpactNode } from './graph'
import './ImpactGraph.css'

interface GraphNode {
  id: string
  kind: string
  impact?: ImpactLevel
}

interface GraphEdge {
  source: string
  target: string
  label?: string
}

interface ImpactGraphProps {
  currentContractId?: string
  graphNodes: GraphNode[]
  graphEdges: GraphEdge[]
  isLoading: boolean
  onNodeClick?: (node: { id: string; kind: string }) => void
}

 
const nodeTypes: NodeTypes = {
  contract: ContractNode,
}

const edgeTypes: EdgeTypes = {
  dependency: DependencyEdge,
}

export function ImpactGraph({
  currentContractId,
  graphNodes,
  graphEdges,
  isLoading,
  onNodeClick,
}: ImpactGraphProps) {
  const [filterMode, setFilterMode] = useState<FilterMode>('all')
  const [depth, setDepth] = useState<DepthLevel>('all')

  const { layoutNodes, layoutEdges } = useGraphLayout({
    graphNodes,
    graphEdges,
    currentContractId,
    depth,
    filterMode,
  })

  const [nodes, setNodes, onNodesChange] = useNodesState(layoutNodes)
  const [edges, setEdges, onEdgesChange] = useEdgesState(layoutEdges)

  useEffect(() => {
    setNodes(layoutNodes)
    setEdges(layoutEdges)
  }, [layoutNodes, layoutEdges, setNodes, setEdges])

  const handleNodeClick = useCallback(
    (_: React.MouseEvent, node: ImpactNode) => {
      onNodeClick?.({ id: node.data.id, kind: node.data.kind })
    },
    [onNodeClick]
  )

  if (isLoading) {
    return (
      <div className="impact-graph impact-graph--loading">
        <div className="impact-graph__spinner" />
        <span>그래프 로딩 중...</span>
      </div>
    )
  }

  if (graphNodes.length === 0) {
    return (
      <div className="impact-graph impact-graph--empty">
        <span>표시할 그래프가 없습니다</span>
      </div>
    )
  }

  return (
    <div className="impact-graph">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onNodeClick={handleNodeClick}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        fitView
        fitViewOptions={{ padding: 0.2 }}
        minZoom={0.1}
        maxZoom={2}
        proOptions={{ hideAttribution: true }}
      >
        <Background variant={BackgroundVariant.Dots} gap={16} size={1} />
        <Controls showInteractive={false} />
        <MiniMap
          nodeColor={(node) => {
            const data = node.data as { impact?: ImpactLevel }
            if (data.impact === 'changed') return '#ef4444'
            if (data.impact === 'affected') return '#f59e0b'
            return '#64748b'
          }}
          maskColor="rgba(0, 0, 0, 0.4)"
        />

        <GraphControlPanel
          depth={depth}
          filterMode={filterMode}
          onDepthChange={setDepth}
          onFilterModeChange={setFilterMode}
        />
        <GraphLegend />
      </ReactFlow>
    </div>
  )
}
