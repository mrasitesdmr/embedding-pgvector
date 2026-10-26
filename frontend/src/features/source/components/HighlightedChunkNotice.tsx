import { messageForError } from '../../../shared/api/errorMessages.ts'
import { Callout } from '../../../shared/components/Callout.tsx'

type HighlightedChunkNoticeProps = {
  chunkId: number
  isLoaded: boolean
  error: unknown
}

export function HighlightedChunkNotice({ chunkId, isLoaded, error }: HighlightedChunkNoticeProps) {
  if (error !== null && error !== undefined) {
    return <Callout tone="warning">{messageForError(error)}</Callout>
  }
  if (!isLoaded) {
    return null
  }
  return <Callout tone="warning">{`Arama sonucundan açılan parça vurgulandı: #${chunkId}`}</Callout>
}
