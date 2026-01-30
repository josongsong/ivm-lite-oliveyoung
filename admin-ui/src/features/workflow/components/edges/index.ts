import { LabeledEdge } from './LabeledEdge'

export const edgeTypes = {
  labeled: LabeledEdge,
  smoothstep: LabeledEdge, // 기본 smoothstep도 라벨 지원하도록
  default: LabeledEdge
}

export { LabeledEdge }
