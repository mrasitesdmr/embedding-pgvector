import type { PageResponse } from '../shared/api/pagination.ts'
import type {
  SearchEventResponse,
  SearchEventStatsResponse,
  SearchHitResponse,
  SearchResultResponse,
} from '../features/search/model/types.ts'
import type { IngestionJobResponse, SourceResponse } from '../features/source/model/types.ts'
import type { ChunkResponse } from '../shared/chunk/types.ts'
import type { WorkspaceResponse } from '../shared/workspace/types.ts'

export function pageOf<T>(content: T[], page = 0, size = 20, totalElements = content.length): PageResponse<T> {
  return { content, page, size, totalElements, totalPages: Math.max(1, Math.ceil(totalElements / size)) }
}

export const BELGELERIM: WorkspaceResponse = {
  workspaceId: 42,
  name: 'Belgelerim',
  sourceCount: 4,
  chunkCount: 42,
  embeddingModels: ['bge-m3@1024'],
}

export const DENEME: WorkspaceResponse = {
  workspaceId: 19,
  name: 'Deneme alanı',
  sourceCount: 3,
  chunkCount: 38,
  embeddingModels: ['bge-m3@1024'],
}

export const WORKSPACES = [DENEME, BELGELERIM]

export function source(overrides: Partial<SourceResponse> & Pick<SourceResponse, 'sourceId' | 'name'>): SourceResponse {
  return {
    workspaceId: 42,
    kind: 'WEB_CRAWL',
    version: null,
    chunkCount: 0,
    status: 'READY',
    syncedAt: '2026-10-12T11:31:40Z',
    fileName: null,
    createdAt: '2026-10-01T09:00:00Z',
    latestJob: null,
    ...overrides,
  }
}

export function ingestionJob(
  overrides: Partial<IngestionJobResponse> & Pick<IngestionJobResponse, 'jobId' | 'sourceId'>,
): IngestionJobResponse {
  return {
    workspaceId: 42,
    version: '3.2',
    fileName: 'Atlaskap-Lojistik-Surec-El-Kitabi-v3.2.pdf',
    stage: 'QUEUED',
    progress: 0,
    embeddedChunks: 0,
    totalChunks: null,
    errorCode: null,
    createdAt: '2026-10-25T10:00:00Z',
    startedAt: null,
    finishedAt: null,
    statusUrl: `/api/workspaces/${overrides.workspaceId ?? 42}/ingestion-jobs/${overrides.jobId}`,
    ...overrides,
  }
}

export const BELGELERIM_SOURCES: SourceResponse[] = [
  source({ sourceId: 10001, name: 'İzin notları', kind: 'WEB_CRAWL', chunkCount: 21 }),
  source({
    sourceId: 10002,
    name: 'Atlaskap-Lojistik-Surec-El-Kitabi',
    kind: 'PDF',
    version: '3.1',
    fileName: 'Atlaskap-Lojistik-Surec-El-Kitabi-v3.1.pdf',
    chunkCount: 5,
  }),
  source({ sourceId: 10003, name: 'Masraf kuralları tablosu', kind: 'CSV', chunkCount: 9, status: 'INGESTING', syncedAt: null }),
  source({ sourceId: 10004, name: 'Destek talepleri dışa aktarımı', kind: 'TICKET_EXPORT', chunkCount: 7, status: 'FAILED' }),
]

export const DENEME_SOURCES: SourceResponse[] = [
  source({ sourceId: 10011, workspaceId: 19, name: 'Deneme notu', chunkCount: 20 }),
  source({ sourceId: 10012, workspaceId: 19, name: 'Deneme kılavuzu', kind: 'PDF', chunkCount: 10 }),
  source({ sourceId: 10013, workspaceId: 19, name: 'Deneme kayıtları', kind: 'CSV', chunkCount: 8 }),
]

export function chunk(chunkId: number, overrides: Partial<ChunkResponse> = {}): ChunkResponse {
  return {
    chunkId,
    chunkNo: chunkId % 1000,
    heading: `Başlık ${chunkId}`,
    content: `İçerik ${chunkId}`,
    tokenCount: 12,
    embeddingModel: 'bge-m3@1024',
    ...overrides,
  }
}

export function hit(overrides: Partial<SearchHitResponse> = {}): SearchHitResponse {
  return {
    rank: 1,
    chunkId: 1380,
    chunkNo: 31,
    sourceId: 10001,
    sourceName: 'İzin notları',
    heading: 'Yıllık izin kaç gün?',
    content: 'Yıllık izin 14 iş günüdür.',
    matchedBy: ['KEYWORD', 'SEMANTIC'],
    keywordRank: 1,
    keywordScore: 0.1,
    vectorRank: 1,
    distance: 0.2345,
    rrfScore: 0.0328,
    matchedTerms: [{ field: 'CONTENT', start: 0, end: 6 }],
    ...overrides,
  }
}

export function searchResult(overrides: Partial<SearchResultResponse> = {}): SearchResultResponse {
  return {
    query: 'Yıllık izin kaç gün?',
    mode: 'HYBRID',
    filterStrategy: 'ITERATIVE_SCAN',
    efSearch: 40,
    limit: 10,
    returnedCount: 2,
    keywordCount: 1,
    vectorCount: 10,
    vectorShort: false,
    timings: { embeddingMs: 40, sqlMs: 12, totalMs: 57 },
    hits: [
      hit(),
      hit({
        rank: 2,
        chunkId: 1381,
        chunkNo: 32,
        heading: 'Masraf beyanı',
        content: 'Başka bir hata kodu açıklaması.',
        matchedBy: ['SEMANTIC'],
        keywordRank: null,
        keywordScore: null,
        vectorRank: 2,
        distance: 0.3,
        rrfScore: 0.0161,
        matchedTerms: [],
      }),
    ],
    vectorPlan: null,
    ...overrides,
  }
}

export function stats(overrides: Partial<SearchEventStatsResponse> = {}): SearchEventStatsResponse {
  return {
    totalCount: 0,
    todayCount: 0,
    last7DaysCount: 0,
    zeroResultCount: 0,
    lowResultCount: 0,
    zeroResultRate: null,
    lowResultRate: null,
    timeZone: 'Europe/Istanbul',
    todayStart: '2026-10-24T21:00:00Z',
    last7DaysStart: '2026-10-18T21:00:00Z',
    ...overrides,
  }
}

export function searchEvent(overrides: Partial<SearchEventResponse> = {}): SearchEventResponse {
  return {
    searchEventId: 1,
    createdAt: '2026-10-25T11:05:00Z',
    channel: 'Temsilci paneli',
    query: 'Yıllık izin kaç gün?',
    mode: 'HYBRID',
    filterStrategy: 'ITERATIVE_SCAN',
    limit: 10,
    resultCount: 10,
    topChunkId: 1380,
    topChunkHeading: 'Yıllık izin kaç gün?',
    totalMs: 57,
    ...overrides,
  }
}
