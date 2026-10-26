import { EmptyState } from '../../../shared/components/EmptyState.tsx'
import { noResultHint } from '../model/searchNotices.ts'
import type { SearchResultResponse } from '../model/types.ts'

type SearchResultEmptyProps = { result: SearchResultResponse }

export function SearchResultEmpty({ result }: SearchResultEmptyProps) {
  return <EmptyState title={`“${result.query}” için sonuç bulunamadı`} description={noResultHint(result.mode)} />
}
