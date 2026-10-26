import { ApiError } from '../api/ApiError.ts'

export const CHUNK_GONE_ERROR_CODES = ['CHUNK_NOT_FOUND', 'SOURCE_NOT_FOUND', 'WORKSPACE_NOT_FOUND'] as const

const chunkGoneErrorCodes: readonly string[] = CHUNK_GONE_ERROR_CODES

export function isChunkGoneError(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404 && chunkGoneErrorCodes.includes(error.errorCode)
}
