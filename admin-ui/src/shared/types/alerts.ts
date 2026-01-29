// Alerts Types
export interface AlertsResponse {
  summary: AlertSummary
  alerts: Alert[]
  rules: AlertRule[]
}

export interface AlertSummary {
  active: number
  acknowledged: number
  silenced: number
}

export type AlertSeverity = 'CRITICAL' | 'WARNING' | 'INFO'
export type AlertStatus = 'ACTIVE' | 'ACKNOWLEDGED' | 'SILENCED' | 'RESOLVED'

export interface Alert {
  id: string
  name: string
  severity: AlertSeverity
  status: AlertStatus
  message: string
  currentValue: string
  threshold: string
  firedAt: string
  occurrences: number
  silencedUntil?: string
}

export interface AlertRule {
  id: string
  name: string
  severity: AlertSeverity
  enabled: boolean
  channels: string[]
  condition: string
}
