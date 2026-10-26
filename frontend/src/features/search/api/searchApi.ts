import { request, withQuery } from '../../../shared/api/httpClient.ts'
import type { PageResponse } from '../../../shared/api/pagination.ts'
import { SEARCH_EVENTS_PAGE_SIZE } from '../model/searchRules.ts'
import type {
  PopularQueriesResponse,
  SearchChunksRequest,
  SearchEventResponse,
  SearchEventStatsResponse,
  SearchResultResponse,
} from '../model/types.ts'

export function searchChunks(workspaceId: number, searchRequest: SearchChunksRequest): Promise<SearchResultResponse> {
  return request(`/api/workspaces/${workspaceId}/search`, {
    method: 'POST',
    body: JSON.stringify(searchRequest),
  })
}

export function listSearchEvents(workspaceId: number, page: number): Promise<PageResponse<SearchEventResponse>> {
  return request(withQuery(`/api/workspaces/${workspaceId}/search-events`, { page, size: SEARCH_EVENTS_PAGE_SIZE }))
}

export function getSearchEventStats(workspaceId: number): Promise<SearchEventStatsResponse> {
  return request(`/api/workspaces/${workspaceId}/search-events/stats`)
}

export function listPopularQueries(workspaceId: number): Promise<PopularQueriesResponse> {
  return request(`/api/workspaces/${workspaceId}/search-events/popular-queries`)
}
