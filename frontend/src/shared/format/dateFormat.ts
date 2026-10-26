export const DISPLAY_TIME_ZONE = 'Europe/Istanbul'

const dayKeyFormat = new Intl.DateTimeFormat('en-CA', {
  timeZone: DISPLAY_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

const dateTimeFormat = new Intl.DateTimeFormat('tr-TR', {
  timeZone: DISPLAY_TIME_ZONE,
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

type DateTimeParts = { day: string; month: string; year: string; hour: string; minute: string }

function toParts(date: Date): DateTimeParts {
  const parts: DateTimeParts = { day: '', month: '', year: '', hour: '', minute: '' }
  for (const part of dateTimeFormat.formatToParts(date)) {
    if (part.type === 'day' || part.type === 'month' || part.type === 'year' || part.type === 'hour' || part.type === 'minute') {
      parts[part.type] = part.value
    }
  }
  return parts
}

export function formatClock(isoDateTime: string, now: Date = new Date()): string {
  const date = new Date(isoDateTime)
  const parts = toParts(date)
  const time = `${parts.hour}:${parts.minute}`
  if (dayKeyFormat.format(date) === dayKeyFormat.format(now)) {
    return time
  }
  return `${parts.day} ${parts.month} ${time}`
}

export function formatDateTime(isoDateTime: string): string {
  const parts = toParts(new Date(isoDateTime))
  return `${parts.day} ${parts.month} ${parts.year} ${parts.hour}:${parts.minute}`
}
