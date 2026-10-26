import { describe, expect, it } from 'vitest'
import { formatClock, formatDateTime } from './dateFormat.ts'

const NOW = new Date('2026-10-25T10:00:00Z')

describe('formatClock', () => {
  it('givenEventToday_whenFormatClock_thenShowsIstanbulTimeOnly', () => {
    expect(formatClock('2026-10-25T11:05:00Z', NOW)).toBe('14:05')
  })

  it('givenEventYesterday_whenFormatClock_thenShowsDayMonthAndTime', () => {
    expect(formatClock('2026-10-24T11:05:00Z', NOW)).toBe('24 Eki 14:05')
  })

  it('givenEventAfterIstanbulMidnightButBeforeUtcMidnight_whenFormatClock_thenCountsAsNextDay', () => {
    expect(formatClock('2026-10-24T21:30:00Z', NOW)).toBe('00:30')
  })

  it('givenEventJustBeforeIstanbulMidnight_whenFormatClock_thenCountsAsPreviousDay', () => {
    expect(formatClock('2026-10-24T20:59:00Z', NOW)).toBe('24 Eki 23:59')
  })
})

describe('formatDateTime', () => {
  it('givenIsoDateTime_whenFormatDateTime_thenShowsIstanbulDateAndTime', () => {
    expect(formatDateTime('2026-10-12T11:31:40Z')).toBe('12 Eki 2026 14:31')
  })
})
