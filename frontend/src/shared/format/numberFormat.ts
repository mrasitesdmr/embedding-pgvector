export const EMPTY_VALUE = '—'

const integerFormat = new Intl.NumberFormat('tr-TR')
const percentFormat = new Intl.NumberFormat('tr-TR', { style: 'percent', maximumFractionDigits: 1 })
const scoreFormat = new Intl.NumberFormat('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 4 })

export function formatInteger(value: number | null | undefined): string {
  return value === null || value === undefined ? EMPTY_VALUE : integerFormat.format(value)
}

export function formatPercent(value: number | null | undefined): string {
  return value === null || value === undefined ? EMPTY_VALUE : percentFormat.format(value)
}

export function formatScore(value: number | null | undefined): string {
  return value === null || value === undefined ? EMPTY_VALUE : scoreFormat.format(value)
}

export function formatDuration(milliseconds: number | null | undefined): string {
  return milliseconds === null || milliseconds === undefined ? EMPTY_VALUE : `${integerFormat.format(milliseconds)} ms`
}

const BYTES_PER_KILOBYTE = 1024
const BYTES_PER_MEGABYTE = BYTES_PER_KILOBYTE * BYTES_PER_KILOBYTE
const fileSizeFormat = new Intl.NumberFormat('tr-TR', { maximumFractionDigits: 1 })

export function formatFileSize(bytes: number): string {
  if (bytes < BYTES_PER_KILOBYTE) {
    return `${integerFormat.format(bytes)} B`
  }
  if (bytes < BYTES_PER_MEGABYTE) {
    return `${fileSizeFormat.format(bytes / BYTES_PER_KILOBYTE)} KB`
  }
  return `${fileSizeFormat.format(bytes / BYTES_PER_MEGABYTE)} MB`
}
