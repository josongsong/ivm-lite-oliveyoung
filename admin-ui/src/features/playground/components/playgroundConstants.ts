import { Database, FileCode2, Layers, Zap } from 'lucide-react'

export type ContractType = 'RULESET' | 'VIEW_DEFINITION' | 'SINK_RULE' | 'ENTITY_SCHEMA'

export const CONTRACT_TYPES: { type: ContractType; label: string; icon: React.ElementType; color: string }[] = [
  { type: 'RULESET', label: 'RuleSet', icon: Layers, color: '#6366f1' },
  { type: 'VIEW_DEFINITION', label: 'ViewDef', icon: Database, color: '#10b981' },
  { type: 'SINK_RULE', label: 'SinkRule', icon: Zap, color: '#f59e0b' },
  { type: 'ENTITY_SCHEMA', label: 'Schema', icon: FileCode2, color: '#ec4899' },
]

export const TEMPLATES: Record<ContractType, { yaml: string; sample: string }> = {
  RULESET: {
    yaml: `kind: RULESET
id: my_ruleset
version: "1.0.0"
entityType: PRODUCT
slices:
  - type: CORE
    buildRules:
      passThrough: ["id", "name", "price"]
  - type: SUMMARY
    buildRules:
      mapFields:
        name: displayName
        price: displayPrice`,
    sample: `{
  "id": "SKU-001",
  "name": "상품명",
  "price": 10000,
  "brandId": "BRAND-001",
  "isActive": true
}`,
  },
  VIEW_DEFINITION: {
    yaml: `kind: VIEW_DEFINITION
id: product_view
version: "1.0.0"
viewName: ProductFullView
primaryEntity: PRODUCT
compose:
  - sliceRef: CORE
    from: PRODUCT
  - sliceRef: SUMMARY
    from: PRODUCT
    optional: true`,
    sample: `{
  "entityKey": "SKU-001",
  "entityType": "PRODUCT"
}`,
  },
  SINK_RULE: {
    yaml: `kind: SINK_RULE
id: product_sink
version: "1.0.0"
viewRef: ProductFullView
sink:
  type: WEBHOOK
  endpoint: https://api.example.com/sync
  headers:
    Authorization: "Bearer \${env.API_TOKEN}"
  retryPolicy:
    maxRetries: 3
    backoffMs: 1000`,
    sample: `{
  "viewName": "ProductFullView",
  "entityKey": "SKU-001"
}`,
  },
  ENTITY_SCHEMA: {
    yaml: `kind: ENTITY_SCHEMA
id: product_schema
version: "1.0.0"
entityType: PRODUCT
fields:
  - name: id
    type: string
    required: true
  - name: name
    type: string
    required: true
  - name: price
    type: number
    required: true
  - name: brandId
    type: string
    required: false`,
    sample: `{
  "id": "SKU-001",
  "name": "상품명",
  "price": 10000
}`,
  },
}
