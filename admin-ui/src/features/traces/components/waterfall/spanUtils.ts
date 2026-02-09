import { AlertCircle, Database, Globe, Layers, Server, Zap } from 'lucide-react'
import { createElement } from 'react'
import type { SpanDetail } from '@/shared/types'

export interface SpanColor {
  bg: string
  border: string
  text: string
}

const SERVICE_COLORS: Record<string, SpanColor> = {
  http: { bg: 'rgba(99, 179, 237, 0.3)', border: '#63b3ed', text: '#63b3ed' },
  database: { bg: 'rgba(72, 187, 120, 0.3)', border: '#48bb78', text: '#48bb78' },
  aws: { bg: 'rgba(246, 173, 85, 0.3)', border: '#f6ad55', text: '#f6ad55' },
  remote: { bg: 'rgba(237, 100, 166, 0.3)', border: '#ed64a6', text: '#ed64a6' },
  segment: { bg: 'rgba(160, 174, 192, 0.3)', border: '#a0aec0', text: '#a0aec0' },
  subsegment: { bg: 'rgba(129, 140, 248, 0.3)', border: '#818cf8', text: '#818cf8' },
  error: { bg: 'rgba(245, 101, 101, 0.3)', border: '#f56565', text: '#f56565' },
}

export function getSpanColor(span: SpanDetail): SpanColor {
  if (span.hasError) return SERVICE_COLORS.error
  const type = span.type?.toLowerCase() || 'segment'
  return SERVICE_COLORS[type] || SERVICE_COLORS.segment
}

export function getSpanIcon(span: SpanDetail) {
  if (span.hasError) return createElement(AlertCircle, { size: 12 })
  const type = span.type?.toLowerCase() || ''
  switch (type) {
    case 'database':
      return createElement(Database, { size: 12 })
    case 'http':
      return createElement(Globe, { size: 12 })
    case 'aws':
      return createElement(Server, { size: 12 })
    case 'remote':
      return createElement(Zap, { size: 12 })
    default:
      return createElement(Layers, { size: 12 })
  }
}

export interface NormalizedSpan extends SpanDetail {
  relativeStart: number
  relativeEnd: number
}

export interface TreeNode {
  span: NormalizedSpan
  children: TreeNode[]
  depth: number
  isExpanded: boolean
}
