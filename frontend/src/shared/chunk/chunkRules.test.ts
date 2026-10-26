import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/ApiError.ts'
import { isChunkGoneError } from './chunkRules.ts'

describe('isChunkGoneError', () => {
  it.each([
    [404, 'CHUNK_NOT_FOUND', true],
    [404, 'SOURCE_NOT_FOUND', true],
    [404, 'WORKSPACE_NOT_FOUND', true],
    [404, 'INGESTION_JOB_NOT_FOUND', false],
    [500, 'CHUNK_NOT_FOUND', false],
    [0, 'NETWORK_ERROR', false],
    [500, 'INTERNAL_ERROR', false],
  ])('givenApiError_%i_%s_whenIsChunkGoneError_thenReturns%s', (status, errorCode, expected) => {
    // Act
    const result = isChunkGoneError(new ApiError(status, errorCode))

    // Assert
    expect(result).toBe(expected)
  })

  it('givenNonApiError_whenIsChunkGoneError_thenFalse', () => {
    // Act
    const result = isChunkGoneError(new Error('CHUNK_NOT_FOUND'))

    // Assert
    expect(result).toBe(false)
  })

  it('givenNullError_whenIsChunkGoneError_thenFalse', () => {
    // Act
    const result = isChunkGoneError(null)

    // Assert
    expect(result).toBe(false)
  })
})
