import { describe, expect, it } from 'vitest'
import { ingestionJob, source } from '../../../test/fixtures.ts'
import {
  displayedSourceStatus,
  freshestJob,
  ingestionDoneMessage,
  ingestionFailureLine,
  ingestionStageText,
  isJobActive,
  isSourceIngesting,
  progressPercent,
} from './ingestionRules.ts'

describe('ingestionStageText', () => {
  it.each([
    [{ stage: 'QUEUED' }, 'Sıraya alındı'],
    [{ stage: 'PARSING' }, 'Dosya okunuyor'],
    [{ stage: 'CHUNKING' }, 'Parçalara bölünüyor'],
    [{ stage: 'WRITING' }, 'Yazılıyor'],
    [{ stage: 'EMBEDDING', embeddedChunks: 96, totalChunks: 214 }, 'Embed ediliyor · 96/214 parça'],
    [{ stage: 'EMBEDDING', embeddedChunks: 1200, totalChunks: 1410 }, 'Embed ediliyor · 1.200/1.410 parça'],
    [{ stage: 'EMBEDDING', embeddedChunks: 0, totalChunks: null }, 'Embed ediliyor · 0/… parça'],
  ] as const)('givenJobStage_whenIngestionStageText_thenReturnsTurkishStageText (%j)', (overrides, expected) => {
    expect(ingestionStageText(ingestionJob({ jobId: 7, sourceId: 10002, ...overrides }))).toBe(expected)
  })
})

describe('progressPercent', () => {
  it.each([
    [0.458879, 46],
    [0, 0],
    [1, 100],
    [0.1, 10],
    [1.4, 100],
    [-0.2, 0],
  ])('givenProgress_whenProgressPercent_thenRoundedAndClamped (%f)', (progress, expected) => {
    expect(progressPercent(ingestionJob({ jobId: 7, sourceId: 10002, progress }))).toBe(expected)
  })
})

describe('ingestionDoneMessage', () => {
  it('givenVersionedJob_whenIngestionDoneMessage_thenNameVersionAndChunkCount', () => {
    const job = ingestionJob({ jobId: 7, sourceId: 10002, stage: 'DONE', version: '3.2', totalChunks: 214 })
    expect(ingestionDoneMessage('Atlaskap-Lojistik-Surec-El-Kitabi', job)).toBe(
      'Atlaskap-Lojistik-Surec-El-Kitabi v3.2 hazır · 214 parça',
    )
  })

  it('givenJobWithoutVersion_whenIngestionDoneMessage_thenOmitsVersion', () => {
    const job = ingestionJob({ jobId: 8, sourceId: 10001, stage: 'DONE', version: null, totalChunks: 12 })
    expect(ingestionDoneMessage('Yardım notları', job)).toBe('Yardım notları hazır · 12 parça')
  })

  it('givenThousandsOfChunks_whenIngestionDoneMessage_thenTurkishGrouping', () => {
    const job = ingestionJob({ jobId: 8, sourceId: 10001, stage: 'DONE', version: null, totalChunks: 1410 })
    expect(ingestionDoneMessage('Büyük kaynak', job)).toBe('Büyük kaynak hazır · 1.410 parça')
  })
})

describe('ingestionFailureLine', () => {
  it('givenEmbeddingUnavailable_whenIngestionFailureLine_thenPrefixedReason', () => {
    const job = ingestionJob({ jobId: 7, sourceId: 10002, stage: 'FAILED', errorCode: 'EMBEDDING_UNAVAILABLE' })
    expect(ingestionFailureLine(job)).toBe('Son yükleme başarısız: embedding servisine (Ollama) ulaşılamıyor')
  })
})

describe('isJobActive / isSourceIngesting / displayedSourceStatus', () => {
  it.each([
    ['QUEUED', true],
    ['PARSING', true],
    ['CHUNKING', true],
    ['EMBEDDING', true],
    ['WRITING', true],
    ['DONE', false],
    ['FAILED', false],
  ] as const)('givenStage_whenIsJobActive_thenOnlyUnfinishedStagesAreActive (%s)', (stage, expected) => {
    expect(isJobActive(ingestionJob({ jobId: 7, sourceId: 10002, stage }))).toBe(expected)
  })

  it('givenNoJob_whenIsJobActive_thenFalse', () => {
    expect(isJobActive(null)).toBe(false)
    expect(isJobActive(undefined)).toBe(false)
  })

  it('givenReadySourceWithActiveJob_whenDisplayedSourceStatus_thenIngesting', () => {
    const readySource = source({ sourceId: 10002, name: 'Kılavuz', status: 'READY' })
    const embedding = ingestionJob({ jobId: 7, sourceId: 10002, stage: 'EMBEDDING' })
    expect(isSourceIngesting(readySource, embedding)).toBe(true)
    expect(displayedSourceStatus(readySource, embedding)).toBe('INGESTING')
  })

  it('givenReadySourceWithFinishedJob_whenDisplayedSourceStatus_thenSourceOwnStatus', () => {
    const readySource = source({ sourceId: 10002, name: 'Kılavuz', status: 'READY' })
    const failed = ingestionJob({ jobId: 7, sourceId: 10002, stage: 'FAILED', errorCode: 'EMBEDDING_UNAVAILABLE' })
    expect(isSourceIngesting(readySource, failed)).toBe(false)
    expect(displayedSourceStatus(readySource, failed)).toBe('READY')
  })

  it('givenIngestingSourceWithoutJob_whenIsSourceIngesting_thenTrue', () => {
    const ingesting = source({ sourceId: 10003, name: 'Hata kodları', status: 'INGESTING' })
    expect(isSourceIngesting(ingesting, null)).toBe(true)
  })
})

describe('freshestJob', () => {
  const embedding40 = ingestionJob({ jobId: 7, sourceId: 10002, stage: 'EMBEDDING', progress: 0.4 })
  const embedding60 = ingestionJob({ jobId: 7, sourceId: 10002, stage: 'EMBEDDING', progress: 0.6 })
  const done = ingestionJob({ jobId: 7, sourceId: 10002, stage: 'DONE', progress: 1 })
  const newerJob = ingestionJob({ jobId: 9, sourceId: 10002, stage: 'QUEUED' })

  it('givenOnlyOneSide_whenFreshestJob_thenReturnsThatSide', () => {
    expect(freshestJob(embedding40, null)).toBe(embedding40)
    expect(freshestJob(null, embedding40)).toBe(embedding40)
    expect(freshestJob(undefined, undefined)).toBeNull()
  })

  it('givenDifferentJobIds_whenFreshestJob_thenListedJobWins', () => {
    expect(freshestJob(done, newerJob)).toBe(newerJob)
  })

  it('givenSameActiveJob_whenFreshestJob_thenFurtherProgressWins', () => {
    expect(freshestJob(embedding60, embedding40)).toBe(embedding60)
    expect(freshestJob(embedding40, embedding60)).toBe(embedding60)
  })

  it('givenListedJobFinished_whenFreshestJob_thenListedWins', () => {
    expect(freshestJob(embedding60, done)).toBe(done)
  })

  it('givenCachedJobFinishedWhileListedActive_whenFreshestJob_thenCachedWins', () => {
    expect(freshestJob(done, embedding40)).toBe(done)
  })
})
