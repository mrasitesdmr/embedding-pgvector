import { useEffect, useId, useRef } from 'react'
import type { KeyboardEvent, MouseEvent, ReactNode, RefObject, SyntheticEvent } from 'react'
import styles from './Dialog.module.css'

const FOCUSABLE_SELECTOR = 'input:not([disabled]), select:not([disabled]), textarea:not([disabled]), button:not([disabled]), [href], [tabindex]:not([tabindex="-1"])'

type DialogProps = {
  title: ReactNode
  isOpen: boolean
  onClose: () => void
  canClose?: boolean
  initialFocusRef?: RefObject<HTMLElement | null>
  size?: 'default' | 'wide'
  hasCloseButton?: boolean
  closeOnBackdropClick?: boolean
  returnFocusRef?: RefObject<HTMLElement | null>
  children: ReactNode
}

export function Dialog({
  title,
  isOpen,
  onClose,
  canClose = true,
  initialFocusRef,
  size = 'default',
  hasCloseButton = false,
  closeOnBackdropClick = false,
  returnFocusRef,
  children,
}: DialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog === null || !isOpen) {
      return
    }
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const returnTarget = returnFocusRef?.current ?? null
    if (!dialog.open) {
      dialog.showModal()
    }
    const focusTarget = initialFocusRef?.current ?? dialog.querySelector<HTMLElement>(FOCUSABLE_SELECTOR)
    focusTarget?.focus()
    return () => {
      if (dialog.open) {
        dialog.close()
      }
      if (returnTarget?.isConnected) {
        returnTarget.focus()
      } else {
        opener?.focus()
      }
    }
  }, [isOpen, initialFocusRef, returnFocusRef])

  function handleCancel(event: SyntheticEvent<HTMLDialogElement>) {
    event.preventDefault()
    if (canClose) {
      onClose()
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDialogElement>) {
    if (event.key !== 'Tab') {
      return
    }
    const focusables = Array.from(event.currentTarget.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR))
    const first = focusables.at(0)
    const last = focusables.at(-1)
    if (first === undefined || last === undefined) {
      return
    }
    const active = document.activeElement
    if (event.shiftKey && (active === first || active === event.currentTarget)) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && active === last) {
      event.preventDefault()
      first.focus()
    }
  }

  function handleClick(event: MouseEvent<HTMLDialogElement>) {
    if (closeOnBackdropClick && canClose && event.target === event.currentTarget) {
      onClose()
    }
  }

  function handleNativeClose(event: SyntheticEvent<HTMLDialogElement>) {
    // StrictMode re-runs the open effect; the close event of its cleanup arrives after showModal() reopened the dialog.
    if (isOpen && !event.currentTarget.open) {
      onClose()
    }
  }

  const heading = (
    <h2 id={titleId} className={styles.title}>
      {title}
    </h2>
  )

  return (
    // Backdrop clicks land on the dialog element itself; the keyboard equivalent is Esc (onCancel).
    // oxlint-disable-next-line jsx-a11y/no-noninteractive-element-interactions
    <dialog
      ref={dialogRef}
      className={size === 'wide' ? `${styles.dialog} ${styles.wide}` : styles.dialog}
      aria-labelledby={isOpen ? titleId : undefined}
      onCancel={handleCancel}
      onKeyDown={handleKeyDown}
      onClick={handleClick}
      onClose={handleNativeClose}
    >
      {isOpen ? (
        <div className={styles.content}>
          {hasCloseButton ? (
            <div className={styles.header}>
              {heading}
              <button
                type="button"
                className={styles.closeButton}
                aria-label="Paneli kapat"
                disabled={!canClose}
                onClick={onClose}
              >
                <span aria-hidden="true">×</span>
              </button>
            </div>
          ) : (
            heading
          )}
          {children}
        </div>
      ) : null}
    </dialog>
  )
}
