import { QueryClient } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { chunkKey } from '../../../shared/chunk/chunkQueries.ts'
import { chunk } from '../../../test/fixtures.ts'
import { invalidateSourceData } from './sourceQueries.ts'

describe('invalidateSourceData', () => {
  it('givenCachedChunk_whenInvalidateSourceData_thenChunkQueryInvalidated', async () => {
    // Arrange
    const queryClient = new QueryClient()
    queryClient.setQueryData(chunkKey(42, 10001, 1380), chunk(1380))
    queryClient.setQueryData(chunkKey(19, 10001, 1380), chunk(1380))

    // Act
    await invalidateSourceData(queryClient, 42)

    // Assert
    expect(queryClient.getQueryState(chunkKey(42, 10001, 1380))?.isInvalidated).toBe(true)
    expect(queryClient.getQueryState(chunkKey(19, 10001, 1380))?.isInvalidated).toBe(false)
  })
})
