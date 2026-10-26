import { useMemo, useRef } from 'react'
import type { ReactNode } from 'react'
import { useSearchChunksMutation } from '../api/searchQueries.ts'
import { LastSearchContext } from '../hooks/useLastSearch.ts'
import type { LastSearch } from '../hooks/useLastSearch.ts'
import type { SearchChunksRequest } from '../model/types.ts'

type LastSearchProviderProps = {
  workspaceId: number
  children: ReactNode
}

export function LastSearchProvider({ workspaceId, children }: LastSearchProviderProps) {
  const searchMutation = useSearchChunksMutation(workspaceId)
  const isInFlightRef = useRef(false)
  const { mutate, data, error, isPending, variables } = searchMutation

  const value = useMemo<LastSearch>(
    () => ({
      query: variables?.query ?? null,
      result: data,
      error,
      isPending,
      search: (searchRequest: SearchChunksRequest) => {
        if (isInFlightRef.current) {
          return
        }
        isInFlightRef.current = true
        mutate(searchRequest, {
          onSettled: () => {
            isInFlightRef.current = false
          },
        })
      },
    }),
    [variables, data, error, isPending, mutate],
  )

  return <LastSearchContext value={value}>{children}</LastSearchContext>
}
