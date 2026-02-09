export interface ContractDescriptionProps {
  kind: string
  parsed: Record<string, unknown>
  allContracts?: Array<{ kind: string; id: string; parsed: Record<string, unknown> }>
}

export interface DescriptionProps {
  parsed: Record<string, unknown>
}

export interface EntitySchemaDescriptionProps extends DescriptionProps {
  allContracts?: Array<{ kind: string; id: string; parsed: Record<string, unknown> }>
}

export interface GenericDescriptionProps {
  kind: string
  parsed: Record<string, unknown>
}
