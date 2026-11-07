import type { ApiError, OverviewResponse, ProvidersResponse } from './types'

/**
 * 统一请求封装。错误按机器码抛出，页面不解析文案。
 */
export class ApiRequestError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly requestId: string | null,
    readonly status: number,
  ) {
    super(message)
    this.name = 'ApiRequestError'
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    headers: { Accept: 'application/json' },
    ...init,
  })

  if (!response.ok) {
    let payload: ApiError | null = null
    try {
      payload = (await response.json()) as ApiError
    } catch {
      // 响应体不是 JSON 时保留状态码信息
    }
    throw new ApiRequestError(
      payload?.code ?? 'HTTP_' + response.status,
      payload?.message ?? '请求失败',
      payload?.requestId ?? response.headers.get('X-Request-Id'),
      response.status,
    )
  }

  return (await response.json()) as T
}

export function fetchProviders(): Promise<ProvidersResponse> {
  return request<ProvidersResponse>('/api/v1/providers')
}

export function fetchOverview(params: {
  siteId: string
  environment: string
  start: string
  end: string
}): Promise<OverviewResponse> {
  const query = new URLSearchParams(params)
  return request<OverviewResponse>('/api/v1/overview?' + query.toString())
}
