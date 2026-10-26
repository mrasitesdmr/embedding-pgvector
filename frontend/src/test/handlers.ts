import { http, HttpResponse } from 'msw'
import { chunk, BELGELERIM, ingestionJob, BELGELERIM_SOURCES, DENEME_SOURCES, pageOf, searchResult, stats, WORKSPACES } from './fixtures.ts'
import { problem } from './problem.ts'

const ALL_SOURCES = [...BELGELERIM_SOURCES, ...DENEME_SOURCES]

export const handlers = [
  http.get('/api/workspaces', () => HttpResponse.json(pageOf(WORKSPACES, 0, 100))),
  http.get('/api/workspaces/:workspaceId', ({ params }) => {
    const workspace = WORKSPACES.find((candidate) => candidate.workspaceId === Number(params.workspaceId))
    return workspace === undefined ? problem(404, 'WORKSPACE_NOT_FOUND') : HttpResponse.json(workspace)
  }),
  http.get('/api/workspaces/:workspaceId/search-events/stats', () => HttpResponse.json(stats())),
  http.get('/api/workspaces/:workspaceId/search-events/popular-queries', () => HttpResponse.json({ queries: [] })),
  http.get('/api/workspaces/:workspaceId/search-events', () => HttpResponse.json(pageOf([]))),
  http.post('/api/workspaces/:workspaceId/search', () => HttpResponse.json(searchResult())),
  http.get('/api/workspaces/:workspaceId/sources', ({ params }) =>
    HttpResponse.json(pageOf(ALL_SOURCES.filter((candidate) => candidate.workspaceId === Number(params.workspaceId)))),
  ),
  http.get('/api/workspaces/:workspaceId/sources/:sourceId', ({ params }) => {
    const found = ALL_SOURCES.find(
      (candidate) => candidate.workspaceId === Number(params.workspaceId) && candidate.sourceId === Number(params.sourceId),
    )
    return found === undefined ? problem(404, 'SOURCE_NOT_FOUND') : HttpResponse.json(found)
  }),
  http.post('/api/workspaces/:workspaceId/sources', ({ params }) =>
    HttpResponse.json(
      ingestionJob({ jobId: 8, sourceId: 10001, workspaceId: Number(params.workspaceId), version: null, fileName: 'izin-notlari.md' }),
      { status: 202 },
    ),
  ),
  http.post('/api/workspaces/:workspaceId/sources/:sourceId/versions', ({ params }) =>
    HttpResponse.json(
      ingestionJob({ jobId: 7, sourceId: Number(params.sourceId), workspaceId: Number(params.workspaceId) }),
      { status: 202 },
    ),
  ),
  http.get('/api/workspaces/:workspaceId/ingestion-jobs/:jobId', () => problem(404, 'INGESTION_JOB_NOT_FOUND')),
  http.get('/api/workspaces/:workspaceId/sources/:sourceId/chunks', () =>
    HttpResponse.json(pageOf([chunk(1380), chunk(1381)])),
  ),
  http.get('/api/workspaces/:workspaceId/sources/:sourceId/chunks/:chunkId', ({ params }) =>
    HttpResponse.json(chunk(Number(params.chunkId))),
  ),
]

export const DEFAULT_WORKSPACE = BELGELERIM
