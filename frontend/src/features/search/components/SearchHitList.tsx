import { useRef, useState } from 'react'
import type { SearchHitResponse } from '../model/types.ts'
import { SearchHitDialog } from './SearchHitDialog.tsx'
import { SearchHitItem } from './SearchHitItem.tsx'
import styles from './SearchHitList.module.css'

type SearchHitListProps = {
  workspaceId: number
  hits: SearchHitResponse[]
}

export function SearchHitList({ workspaceId, hits }: SearchHitListProps) {
  const [openHit, setOpenHit] = useState<SearchHitResponse | null>(null)
  const openerRef = useRef<HTMLButtonElement | null>(null)

  function handleOpen(hit: SearchHitResponse, opener: HTMLButtonElement) {
    openerRef.current = opener
    setOpenHit(hit)
  }

  return (
    <>
      <ol className={styles.list} aria-label="Arama sonuçları">
        {hits.map((hit) => (
          <li key={hit.chunkId}>
            <SearchHitItem hit={hit} onOpen={handleOpen} />
          </li>
        ))}
      </ol>
      {openHit !== null ? (
        <SearchHitDialog
          workspaceId={workspaceId}
          hit={openHit}
          returnFocusRef={openerRef}
          onClose={() => setOpenHit(null)}
        />
      ) : null}
    </>
  )
}
