import { describe, expect, it } from 'vitest'
import { resultCountTone } from './resultCountTone.ts'

describe('resultCountTone', () => {
  it.each([
    [0, 10, 'bad'],
    [3, 10, 'run'],
    [10, 10, 'ok'],
    [4, null, 'ok'],
    [0, null, 'bad'],
  ] as const)('givenResultCountAndLimit_whenResultCountTone_thenReturnsTone (%i of %s)', (count, limit, expected) => {
    expect(resultCountTone(count, limit)).toBe(expected)
  })
})
