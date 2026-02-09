/**
 * useImpactGraph Hook (Phase 4: Impact Graph)
 *
 * Contract 의존성 그래프 조회 훅.
 * - 긴 staleTime으로 불필요한 리패칭 방지
 * - 그래프는 자주 변경되지 않음
 */
import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { contractEditorApi } from '../api'
import { PERFORMANCE } from '../constants/editorOptions'

interface UseImpactGraphOptions {
  contractKind?: string
  contractId?: string
  enabled?: boolean
}

interface GraphNode {
  id: string
  kind: string
}

interface GraphEdge {
  source: string
  target: string
  label?: string
}

interface UseImpactGraphResult {
  nodes: GraphNode[]
  edges: GraphEdge[]
  isLoading: boolean
  error: Error | null
}

export function useImpactGraph(
  options: UseImpactGraphOptions = {}
): UseImpactGraphResult {
  const { contractKind, contractId, enabled = true } = options

  // 특정 Contract의 그래프 또는 전체 그래프
  const hasSpecificContract = !!contractKind && !!contractId

  const graphQuery = useQuery({
    queryKey: hasSpecificContract
      ? ['contract-graph', contractKind, contractId]
      : ['contracts-graph'],
    queryFn: () =>
      hasSpecificContract
        ? contractEditorApi.getImpactGraph(contractKind!, contractId!)
        : contractEditorApi.getGraph(),
    enabled: enabled,
    staleTime: PERFORMANCE.STALE_TIME.GRAPH,
    gcTime: PERFORMANCE.STALE_TIME.GRAPH * 2, // 가비지 컬렉션 시간
  })

  const { nodes, edges } = useMemo(() => {
    if (!graphQuery.data) {
      return { nodes: [], edges: [] }
    }

    const graphNodes: GraphNode[] = graphQuery.data.nodes.map((n) => ({
      id: n.id,
      kind: n.kind,
    }))

    const graphEdges: GraphEdge[] = graphQuery.data.edges.map((e) => ({
      source: e.from,
      target: e.to,
      label: e.label ?? e.kind,
    }))

    return { nodes: graphNodes, edges: graphEdges }
  }, [graphQuery.data])

  return {
    nodes,
    edges,
    isLoading: graphQuery.isLoading,
    error: graphQuery.error as Error | null,
  }
}
