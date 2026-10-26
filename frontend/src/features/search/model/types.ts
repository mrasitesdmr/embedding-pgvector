export const SEARCH_MODES = ['KEYWORD', 'VECTOR', 'HYBRID'] as const
export type SearchMode = (typeof SEARCH_MODES)[number]

export const FILTER_STRATEGIES = ['POST_FILTER', 'ITERATIVE_SCAN'] as const
export type FilterStrategy = (typeof FILTER_STRATEGIES)[number]

export type SearchBranch = 'KEYWORD' | 'SEMANTIC'

export type MatchedTermField = 'HEADING' | 'CONTENT'

export type SearchSettings = {
  mode: SearchMode
  filterStrategy: FilterStrategy
  efSearch: number
  limit: number
  explain: boolean
}

export type SearchChunksRequest = {
  query: string
  mode?: SearchMode | null
  filterStrategy?: FilterStrategy | null
  efSearch?: number | null
  limit?: number | null
  channel?: string | null
  explain?: boolean | null
}

export type SearchTimingsResponse = {
  embeddingMs?: number | null
  sqlMs: number
  totalMs: number
}

export type MatchedTermResponse = {
  field: MatchedTermField
  start: number
  end: number
}

export type SearchHitResponse = {
  rank: number
  chunkId: number
  chunkNo: number
  sourceId: number
  sourceName: string
  heading?: string | null
  content: string
  matchedBy: SearchBranch[]
  keywordRank?: number | null
  keywordScore?: number | null
  vectorRank?: number | null
  distance?: number | null
  rrfScore?: number | null
  matchedTerms: MatchedTermResponse[]
}

export type SearchResultResponse = {
  query: string
  mode: SearchMode
  filterStrategy?: FilterStrategy | null
  efSearch?: number | null
  limit: number
  returnedCount: number
  keywordCount?: number | null
  vectorCount?: number | null
  vectorShort: boolean
  timings: SearchTimingsResponse
  hits: SearchHitResponse[]
  vectorPlan?: string | null
}

export type SearchEventResponse = {
  searchEventId: number
  createdAt: string
  channel?: string | null
  query: string
  mode: SearchMode
  filterStrategy?: FilterStrategy | null
  limit?: number | null
  resultCount: number
  topChunkId?: number | null
  topChunkHeading?: string | null
  totalMs: number
}

export type SearchEventStatsResponse = {
  totalCount: number
  todayCount: number
  last7DaysCount: number
  zeroResultCount: number
  lowResultCount: number
  zeroResultRate?: number | null
  lowResultRate?: number | null
  timeZone: string
  todayStart: string
  last7DaysStart: string
}

export type PopularQueryResponse = {
  query: string
  searchCount: number
  lastSearchedAt: string
}

export type PopularQueriesResponse = {
  queries: PopularQueryResponse[]
}
