import { ApiError } from './ApiError.ts'
import type { Violation } from './ApiError.ts'

const UNEXPECTED_ERROR = 'UNEXPECTED_ERROR'
const NETWORK_ERROR = 'NETWORK_ERROR'
const SERVER_ERROR_MIN_STATUS = 500

type QueryValue = string | number | boolean | null | undefined

export function withQuery(path: string, params: Record<string, QueryValue>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== null && value !== undefined) {
      search.set(key, String(value))
    }
  }
  const query = search.toString()
  return query === '' ? path : `${path}?${query}`
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  const body = init?.body
  if (body !== undefined && body !== null && !(body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  let response: Response
  try {
    response = await fetch(path, { ...init, headers })
  } catch {
    throw new ApiError(0, NETWORK_ERROR)
  }
  if (!response.ok) {
    throw await toApiError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  try {
    return (await response.json()) as T
  } catch {
    throw new ApiError(response.status, UNEXPECTED_ERROR)
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  const fallbackErrorCode = fallbackErrorCodeFor(response.status)
  let body: unknown
  try {
    body = await response.json()
  } catch {
    return new ApiError(response.status, fallbackErrorCode)
  }
  if (!isRecord(body) || typeof body.errorCode !== 'string' || body.errorCode === '') {
    return new ApiError(response.status, fallbackErrorCode)
  }
  return new ApiError(response.status, body.errorCode, toViolations(body.violations))
}

function fallbackErrorCodeFor(status: number): string {
  return status >= SERVER_ERROR_MIN_STATUS ? NETWORK_ERROR : UNEXPECTED_ERROR
}

function toViolations(value: unknown): Violation[] {
  if (!Array.isArray(value)) {
    return []
  }
  return value.flatMap((item: unknown) =>
    isRecord(item) && typeof item.field === 'string' && typeof item.message === 'string'
      ? [{ field: item.field, message: item.message }]
      : [],
  )
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
