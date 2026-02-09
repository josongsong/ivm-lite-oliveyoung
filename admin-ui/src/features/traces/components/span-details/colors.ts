export function getMethodColor(method: string): string {
  switch (method.toUpperCase()) {
    case 'GET':
      return 'var(--accent-green)'
    case 'POST':
      return 'var(--accent-cyan)'
    case 'PUT':
      return 'var(--accent-orange)'
    case 'DELETE':
      return 'var(--status-error)'
    case 'PATCH':
      return 'var(--accent-purple)'
    default:
      return 'var(--text-muted)'
  }
}

export function getStatusColor(status: number): string {
  if (status >= 500) return 'var(--status-error)'
  if (status >= 400) return 'var(--accent-orange)'
  if (status >= 300) return 'var(--accent-cyan)'
  if (status >= 200) return 'var(--accent-green)'
  return 'var(--text-muted)'
}
