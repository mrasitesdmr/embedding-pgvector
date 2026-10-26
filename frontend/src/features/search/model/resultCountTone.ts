export type ResultCountTone = 'bad' | 'run' | 'ok'

export function resultCountTone(resultCount: number, limit: number | null): ResultCountTone {
  if (resultCount === 0) {
    return 'bad'
  }
  if (limit !== null && resultCount < limit) {
    return 'run'
  }
  return 'ok'
}
