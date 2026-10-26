import { useState } from 'react'
import type { MouseEvent } from 'react'
import button from '../../../shared/components/button.module.css'
import { isUploadableKind } from '../model/sourceRules.ts'
import type { SourceResponse } from '../model/types.ts'
import { UploadVersionDialog } from './UploadVersionDialog.tsx'

type UploadVersionButtonProps = {
  workspaceId: number
  source: SourceResponse
  isIngesting: boolean
}

export function UploadVersionButton({ workspaceId, source, isIngesting }: UploadVersionButtonProps) {
  const [isOpen, setIsOpen] = useState(false)

  if (!isUploadableKind(source.kind)) {
    return null
  }

  function handleClick(event: MouseEvent<HTMLButtonElement>) {
    event.stopPropagation()
    setIsOpen(true)
  }

  return (
    <>
      <button
        type="button"
        className={`${button.secondary} ${button.small}`}
        disabled={isIngesting}
        title={isIngesting ? 'Yükleme sürüyor' : undefined}
        onClick={handleClick}
      >
        Yeni sürüm yükle
      </button>
      <UploadVersionDialog
        workspaceId={workspaceId}
        source={source}
        isOpen={isOpen}
        onClose={() => setIsOpen(false)}
      />
    </>
  )
}
