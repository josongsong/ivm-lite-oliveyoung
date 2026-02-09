import { useMemo } from 'react'
import dagre from 'dagre'
import type { DepthLevel, FilterMode, ImpactEdge, ImpactLevel, ImpactNode } from './types'

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

interface UseGraphLayoutOptions {
  graphNodes: GraphNode[]
  graphEdges: GraphEdge[]
  currentContractId?: string
  depth: DepthLevel
  filterMode: FilterMode
}

export function useGraphLayout({
  graphNodes,
  graphEdges,
  currentContractId,
  depth,
  filterMode,
}: UseGraphLayoutOptions) {
  const { filteredNodes, filteredEdges } = useMemo(() => {
    if (!currentContractId || depth === 'all') {
      const nodes = graphNodes.map((n) => ({
        ...n,
        impact: n.id === currentContractId ? ('changed' as ImpactLevel) : n.impact,
      }))

      if (filterMode === 'affected') {
        const affectedIds = new Set(nodes.filter((n) => n.impact).map((n) => n.id))
        return {
          filteredNodes: nodes.filter((n) => n.impact),
          filteredEdges: graphEdges.filter(
            (e) => affectedIds.has(e.source) && affectedIds.has(e.target)
          ),
        }
      }
      return { filteredNodes: nodes, filteredEdges: graphEdges }
    }

    const reachable = new Set<string>([currentContractId])
    const edgeMap = new Map<string, string[]>()

    graphEdges.forEach((e) => {
      if (!edgeMap.has(e.source)) edgeMap.set(e.source, [])
      if (!edgeMap.has(e.target)) edgeMap.set(e.target, [])
      edgeMap.get(e.source)!.push(e.target)
      edgeMap.get(e.target)!.push(e.source)
    })

    let frontier = [currentContractId]
    for (let d = 0; d < depth; d++) {
      const nextFrontier: string[] = []
      frontier.forEach((nodeId) => {
        const neighbors = edgeMap.get(nodeId) || []
        neighbors.forEach((neighbor) => {
          if (!reachable.has(neighbor)) {
            reachable.add(neighbor)
            nextFrontier.push(neighbor)
          }
        })
      })
      frontier = nextFrontier
      if (frontier.length === 0) break
    }

    const nodes = graphNodes
      .filter((n) => reachable.has(n.id))
      .map((n) => ({
        ...n,
        impact:
          n.id === currentContractId
            ? ('changed' as ImpactLevel)
            : n.impact || ('affected' as ImpactLevel),
      }))

    if (filterMode === 'affected') {
      const affectedIds = new Set(nodes.filter((n) => n.impact).map((n) => n.id))
      return {
        filteredNodes: nodes.filter((n) => n.impact),
        filteredEdges: graphEdges.filter(
          (e) =>
            reachable.has(e.source) &&
            reachable.has(e.target) &&
            affectedIds.has(e.source) &&
            affectedIds.has(e.target)
        ),
      }
    }

    return {
      filteredNodes: nodes,
      filteredEdges: graphEdges.filter(
        (e) => reachable.has(e.source) && reachable.has(e.target)
      ),
    }
  }, [graphNodes, graphEdges, currentContractId, depth, filterMode])

  const { layoutNodes, layoutEdges } = useMemo(() => {
    if (filteredNodes.length === 0) {
      return { layoutNodes: [], layoutEdges: [] }
    }

    const g = new dagre.graphlib.Graph()
    g.setDefaultEdgeLabel(() => ({}))
    g.setGraph({ rankdir: 'TB', nodesep: 60, ranksep: 80 })

    filteredNodes.forEach((node) => {
      g.setNode(node.id, { width: 120, height: 60 })
    })

    filteredEdges.forEach((edge) => {
      g.setEdge(edge.source, edge.target)
    })

    dagre.layout(g)

    const nodes: ImpactNode[] = filteredNodes.map((node) => {
      const pos = g.node(node.id)
      return {
        id: node.id,
        type: 'contract',
        position: { x: pos.x - 60, y: pos.y - 30 },
        data: {
          id: node.id,
          kind: node.kind,
          impact: node.impact,
        },
      }
    })

    const edges: ImpactEdge[] = filteredEdges.map((edge, i) => ({
      id: `e-${i}-${edge.source}-${edge.target}`,
      source: edge.source,
      target: edge.target,
      type: 'dependency',
      data: { label: edge.label },
    }))

    return { layoutNodes: nodes, layoutEdges: edges }
  }, [filteredNodes, filteredEdges])

  return { layoutNodes, layoutEdges }
}
