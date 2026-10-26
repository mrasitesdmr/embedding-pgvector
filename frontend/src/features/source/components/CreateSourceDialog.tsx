import { useRef } from 'react'
import { Dialog } from '../../../shared/components/Dialog.tsx'
import { useCreateSourceMutation } from '../api/sourceQueries.ts'
import { CreateSourceForm } from './CreateSourceForm.tsx'
import styles from './uploadDialog.module.css'

type CreateSourceDialogProps = {
  workspaceId: number
  isOpen: boolean
  onClose: () => void
}

export function CreateSourceDialog({ workspaceId, isOpen, onClose }: CreateSourceDialogProps) {
  const nameInputRef = useRef<HTMLInputElement>(null)
  const createSourceMutation = useCreateSourceMutation(workspaceId)
  const isPending = createSourceMutation.isPending

  return (
    <Dialog
      title="Yeni kaynak ekle"
      isOpen={isOpen}
      onClose={onClose}
      canClose={!isPending}
      initialFocusRef={nameInputRef}
    >
      <div className={styles.form}>
        <p className={styles.description}>
          Dosya yüklenince parçalara bölünür ve embedding'e çevrilir. İş bitene kadar kaynak listede İşleniyor görünür.
        </p>
        <CreateSourceForm
          nameInputRef={nameInputRef}
          isPending={isPending}
          submitLabel="Kaynağı ekle"
          pendingLabel="Yükleniyor…"
          onCreate={createSourceMutation.mutateAsync}
          onCancel={onClose}
          onCreated={() => onClose()}
        />
      </div>
    </Dialog>
  )
}
