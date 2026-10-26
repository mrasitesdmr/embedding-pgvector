import { createContext, use } from 'react'
import type { SearchChunksRequest, SearchResultResponse } from '../model/types.ts'

export type LastSearch = {
  query: string | null
  result: SearchResultResponse | undefined
  error: unknown
  isPending: boolean
  search: (searchRequest: SearchChunksRequest) => void
}

export const LastSearchContext = createContext<LastSearch | null>(null)

export function useLastSearch(): LastSearch {
  const lastSearch = use(LastSearchContext)
  if (lastSearch === null) {
    throw new Error('useLastSearch must be used inside LastSearchProvider')
  }
  return lastSearch
}
