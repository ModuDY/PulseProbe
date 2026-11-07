/**
 * 与 fusion-openapi-0.1.json 对应的类型。
 * 刻意保留 null 联合类型：契约里这些字段可空，缺失与 0 是两件事。
 */

export type SourceStateName = 'ok' | 'stale' | 'unavailable' | 'unsupported'

export interface SourceState {
  providerId: string
  state: SourceStateName
  sourceCollectedAt: string | null
  ageSeconds: number | null
  code: string | null
  message: string | null
}

export type CapabilityState = 'supported' | 'unsupported' | 'unverified'

export interface Provider {
  id: string
  name: string
  type: 'hertzbeat' | 'skywalking'
  version: string | null
  state: SourceState
  capabilities: Record<string, CapabilityState>
}

export type AssetType = 'host' | 'container' | 'service' | 'service_instance' | 'database'

export interface Asset {
  id: string
  siteId: string
  environment: string
  type: AssetType
  parentAssetId?: string | null
  name: string
  labels: Record<string, string>
}

export type Quality = 'valid' | 'partial' | 'no_data' | 'reset' | 'stale' | 'unknown' | 'unsupported'

export interface SampleScope {
  mode: 'full' | 'sampled' | 'unknown' | 'not_applicable'
  ratio: number | null
  note?: string | null
}

export interface OverviewSignal {
  metricId: string
  providerId: string
  value: number | null
  unit: string
  sourceCollectedAt: string | null
  quality: Quality
  sampleScope: SampleScope
}

export interface OverviewCard {
  asset: Asset
  health: 'healthy' | 'warning' | 'critical' | 'unknown'
  signals: OverviewSignal[]
}

export interface OverviewResponse {
  requestId: string
  range: { start: string; end: string }
  partial: boolean
  sources: SourceState[]
  cards: OverviewCard[]
}

export interface ProvidersResponse {
  requestId: string
  items: Provider[]
}

export interface ApiError {
  requestId: string
  code: string
  message: string
  sources?: SourceState[]
}
