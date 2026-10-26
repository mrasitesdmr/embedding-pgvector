import { describe, expect, it } from 'vitest'
import { BELGELERIM } from '../../test/fixtures.ts'
import {
  embeddingModelsLabel,
  homeSection,
  parseWorkspaceId,
  shouldShowWorkspaceSelect,
  sourceDetailPath,
  uploadPath,
  workspaceHomePath,
} from './workspaceRules.ts'

describe('parseWorkspaceId', () => {
  it.each([
    ['42', 42],
    ['abc', null],
    ['0', null],
    ['-3', null],
    ['4.2', null],
    ['', null],
    [undefined, null],
    ['99999999999999999999', null],
  ])('givenPathParam_whenParseWorkspaceId_thenReturnsPositiveIntegerOrNull (%s)', (param, expected) => {
    expect(parseWorkspaceId(param)).toBe(expected)
  })
})

describe('sourceDetailPath', () => {
  it('givenHighlightedChunk_whenSourceDetailPath_thenAddsParcaQuery', () => {
    expect(sourceDetailPath(42, 10003, 1380)).toBe('/w/42/kaynaklar/10003?parca=1380')
    expect(sourceDetailPath(42, 10003)).toBe('/w/42/kaynaklar/10003')
  })
})

describe('embeddingModelsLabel', () => {
  it('givenModels_whenEmbeddingModelsLabel_thenJoinsOrShowsDash', () => {
    expect(embeddingModelsLabel({ ...BELGELERIM, embeddingModels: ['aaa@8', 'bge-m3@1024'] })).toBe('aaa@8, bge-m3@1024')
    expect(embeddingModelsLabel({ ...BELGELERIM, embeddingModels: [] })).toBe('—')
  })
})

describe('workspaceHomePath and uploadPath', () => {
  it('givenIds_whenBuildPaths_thenReturnsHomeAndUploadAddresses', () => {
    expect(workspaceHomePath(42)).toBe('/w/42')
    expect(uploadPath(42)).toBe('/w/42/belge-yukle')
    expect(uploadPath(42, 10001)).toBe('/w/42/belge-yukle?kaynak=10001')
  })
})

describe('homeSection', () => {
  it.each([
    [0, 'belge-yukle'],
    [1, 'arama'],
    [4, 'arama'],
  ])('givenSourceCount_whenHomeSection_thenPicksUploadOnlyWhenEmpty (%i)', (sourceCount, expected) => {
    expect(homeSection({ ...BELGELERIM, sourceCount })).toBe(expected)
  })
})

describe('shouldShowWorkspaceSelect', () => {
  it.each([
    [0, false],
    [1, false],
    [2, true],
    [37, true],
  ])('givenWorkspaceTotal_whenShouldShowWorkspaceSelect_thenShownFromTwo (%i)', (total, expected) => {
    expect(shouldShowWorkspaceSelect(total)).toBe(expected)
  })
})
