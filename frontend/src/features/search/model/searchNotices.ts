import type { SearchMode, SearchResultResponse } from './types.ts'

export function noResultHint(mode: SearchMode): string {
  return mode === 'KEYWORD'
    ? 'Kelime araması sadece aynı kelimeleri bulur. Anlam ya da Hybrid yöntemiyle tekrar dene.'
    : 'Farklı kelimelerle aramayı dene.'
}

export function shortVectorWarning(result: SearchResultResponse): string | null {
  if (!result.vectorShort) {
    return null
  }
  const vectorCount = result.vectorCount ?? 0
  if (result.filterStrategy === 'POST_FILTER') {
    return `Anlam araması istenen ${result.limit} sonuçtan sadece ${vectorCount} tanesini buldu (kısa kaldı). Gelişmiş ayarlarda filtre stratejisini iterative scan yapıp tekrar dene.`
  }
  return `Anlam araması istenen ${result.limit} sonuçtan sadece ${vectorCount} tanesini buldu. Bu çalışma alanında yeterli parça olmayabilir.`
}
