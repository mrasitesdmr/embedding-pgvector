import { describe, expect, it } from 'vitest'
import { formatDuration, formatFileSize, formatInteger, formatPercent, formatScore } from './numberFormat.ts'

describe('numberFormat', () => {
  it.each([
    [0.25, '%25'],
    [0.068, '%6,8'],
    [0, '%0'],
    [null, '—'],
    [undefined, '—'],
  ])('givenRate_whenFormatPercent_thenTurkishPercent (%s)', (value, expected) => {
    expect(formatPercent(value)).toBe(expected)
  })

  it.each([
    [1410, '1.410'],
    [42, '42'],
    [null, '—'],
  ])('givenCount_whenFormatInteger_thenTurkishGrouping (%s)', (value, expected) => {
    expect(formatInteger(value)).toBe(expected)
  })

  it.each([
    [0.1, '0,1000'],
    [0.2345, '0,2345'],
    [0.03279, '0,0328'],
    [null, '—'],
  ])('givenScore_whenFormatScore_thenFourDecimals (%s)', (value, expected) => {
    expect(formatScore(value)).toBe(expected)
  })

  it.each([
    [57, '57 ms'],
    [null, '—'],
  ])('givenMilliseconds_whenFormatDuration_thenAppendsUnit (%s)', (value, expected) => {
    expect(formatDuration(value)).toBe(expected)
  })
})

describe('formatFileSize', () => {
  it.each([
    [830, '830 B'],
    [0, '0 B'],
    [1023, '1.023 B'],
    [1024, '1 KB'],
    [1536, '1,5 KB'],
    [2_516_582, '2,4 MB'],
    [20_971_520, '20 MB'],
  ])('givenBytes_whenFormatFileSize_thenTurkishUnits (%i)', (bytes, expected) => {
    expect(formatFileSize(bytes)).toBe(expected)
  })
})
