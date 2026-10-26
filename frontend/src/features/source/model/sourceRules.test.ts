import { describe, expect, it } from 'vitest'
import { chunkTitle, previewChunkContent, sourceFileLine } from './sourceRules.ts'
import { source } from '../../../test/fixtures.ts'

describe('sourceRules', () => {
  it('givenLongContent_whenPreviewChunkContent_thenCutsAt160WithEllipsis', () => {
    expect(previewChunkContent('a'.repeat(160))).toBe('a'.repeat(160))
    expect(previewChunkContent('a'.repeat(161))).toBe(`${'a'.repeat(160)}…`)
  })

  it('givenMissingHeading_whenChunkTitle_thenUntitled', () => {
    expect(chunkTitle(null)).toBe('Başlıksız parça')
    expect(chunkTitle('Kurulum')).toBe('Kurulum')
  })

  it('givenFileAndVersion_whenSourceFileLine_thenShowsDashAndNoVersionFallbacks', () => {
    expect(sourceFileLine(source({ sourceId: 1, name: 'x', fileName: 'a.pdf', version: '3.1' }))).toBe('a.pdf · v3.1')
    expect(sourceFileLine(source({ sourceId: 1, name: 'x' }))).toBe('— · sürüm yok')
  })
})
