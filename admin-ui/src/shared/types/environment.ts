/**
 * Environment 관련 타입 정의
 */

export type EnvironmentType = 'Dev' | 'QA' | 'Staging' | 'Production'

export interface DatabaseInfo {
  name: string
  type: 'PostgreSQL' | 'DynamoDB' | 'Kafka'
  host?: string
  port?: number
  database?: string
  region?: string
  status: 'connected' | 'disconnected' | 'unknown'
  latencyMs?: number
}

export interface EnvironmentConfig {
  database: {
    url: string
    user: string
    maxPoolSize: number
    minIdle: number
  }
  dynamodb: {
    endpoint?: string
    region: string
    tableName: string
  }
  kafka: {
    bootstrapServers: string
    consumerGroup: string
    topicPrefix: string
  }
  observability: {
    metricsEnabled: boolean
    tracingEnabled: boolean
    otlpEndpoint: string
  }
  worker: {
    enabled: boolean
    pollIntervalMs: number
    batchSize: number
  }
}

export interface GitInfo {
  branch: string
  commit: string
  commitMessage: string
  author: string
  commitDate: string
  isDirty: boolean
  remoteUrl?: string
}

export type AppInstanceType = 'Backend' | 'Frontend' | 'Worker'
export type AppInstanceStatus = 'running' | 'stopped' | 'unknown'

export interface AppInstance {
  name: string
  type: AppInstanceType
  status: AppInstanceStatus
  port?: number
  version: string
  uptime?: string
  startedAt?: string
  memoryUsageMb?: number
  cpuPercent?: number
  requestsPerMin?: number
  healthEndpoint?: string
  features: string[]
}

export interface EnvironmentResponse {
  environment: EnvironmentType
  databases: DatabaseInfo[]
  config: EnvironmentConfig
  git: GitInfo
  appInstances: AppInstance[]
  timestamp: string
}
