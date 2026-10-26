import { Link } from 'react-router'
import type { RefObject } from 'react'
import { messageForError } from '../../../shared/api/errorMessages.ts'
import { useChunkQuery } from '../../../shared/chunk/chunkQueries.ts'
import { isChunkGoneError } from '../../../shared/chunk/chunkRules.ts'
import button from '../../../shared/components/button.module.css'
import { Callout } from '../../../shared/components/Callout.tsx'
import { Dialog } from '../../../shared/components/Dialog.tsx'
import { sourceDetailPath } from '../../../shared/workspace/workspaceRules.ts'
import { hitContentSegments, hitSourceLine, hitTitleSegments } from '../model/searchHitText.ts'
import type { SearchHitResponse } from '../model/types.ts'
import { HighlightedText } from './HighlightedText.tsx'
import styles from './SearchHitDialog.module.css'
import { SearchHitTags } from './SearchHitTags.tsx'

type SearchHitDialogProps = {
  workspaceId: number
  hit: SearchHitResponse
  returnFocusRef: RefObject<HTMLElement | null>
  onClose: () => void
}

export function SearchHitDialog({ workspaceId, hit, returnFocusRef, onClose }: SearchHitDialogProps) {
  const chunkQuery = useChunkQuery(workspaceId, hit.sourceId, hit.chunkId)
  const isChunkGone = isChunkGoneError(chunkQuery.error)

  return (
    <Dialog
      title={
        <span className={styles.title}>
          <HighlightedText segments={hitTitleSegments(hit)} />
        </span>
      }
      isOpen
      onClose={onClose}
      size="wide"
      hasCloseButton
      closeOnBackdropClick
      returnFocusRef={returnFocusRef}
    >
      <div className={styles.meta}>
        <SearchHitTags matchedBy={hit.matchedBy} />
        <small className={styles.source}>{hitSourceLine(hit)}</small>
      </div>
      {isChunkGone ? (
        <div role="alert">
          <Callout tone="warning">{messageForError(chunkQuery.error)}</Callout>
        </div>
      ) : (
        <div className={styles.text} role="region" aria-label="Parça metni" tabIndex={0}>
          <HighlightedText segments={hitContentSegments(hit)} />
        </div>
      )}
      <div className={styles.actions}>
        <button type="button" className={button.secondary} onClick={onClose}>
          Kapat
        </button>
        {isChunkGone ? null : (
          <Link className={button.primary} to={sourceDetailPath(workspaceId, hit.sourceId, hit.chunkId)}>
            Kaynakta göster
          </Link>
        )}
      </div>
    </Dialog>
  )
}
