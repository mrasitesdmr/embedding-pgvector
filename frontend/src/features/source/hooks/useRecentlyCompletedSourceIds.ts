import { createContext, use } from 'react'

const NO_SOURCE_IDS: ReadonlySet<number> = new Set()

export const RecentlyCompletedSourceIdsContext = createContext<ReadonlySet<number>>(NO_SOURCE_IDS)

export function useRecentlyCompletedSourceIds(): ReadonlySet<number> {
  return use(RecentlyCompletedSourceIdsContext)
}
