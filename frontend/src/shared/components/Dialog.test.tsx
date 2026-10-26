import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StrictMode, createRef } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { Dialog } from './Dialog.tsx'

describe('Dialog', () => {
  it('givenCloseOnBackdropClick_whenBackdropOrContentClicked_thenClosesOnlyOnBackdrop', async () => {
    // Arrange
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(
      <Dialog title="Panel" isOpen onClose={onClose} closeOnBackdropClick>
        <p>İçerik</p>
      </Dialog>,
    )

    // Act
    await user.click(screen.getByText('İçerik'))
    const afterContentClick = onClose.mock.calls.length
    await user.click(screen.getByRole('dialog'))

    // Assert
    expect(afterContentClick).toBe(0)
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('givenCloseOnBackdropClickButCannotClose_whenBackdropClicked_thenStaysOpen', async () => {
    // Arrange
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(
      <Dialog title="Panel" isOpen onClose={onClose} closeOnBackdropClick canClose={false}>
        <p>İçerik</p>
      </Dialog>,
    )

    // Act
    await user.click(screen.getByRole('dialog'))

    // Assert
    expect(onClose).not.toHaveBeenCalled()
  })

  it('givenDefaultProps_whenBackdropClicked_thenStaysOpenAndHasNoCloseButton', async () => {
    // Arrange
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(
      <Dialog title="Panel" isOpen onClose={onClose}>
        <button type="button">Tamam</button>
      </Dialog>,
    )

    // Act
    await user.click(screen.getByRole('dialog'))

    // Assert
    expect(onClose).not.toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: 'Paneli kapat' })).not.toBeInTheDocument()
  })

  it('givenHasCloseButton_whenCloseButtonClicked_thenOnCloseCalled', async () => {
    // Arrange
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(
      <Dialog title="Panel" isOpen onClose={onClose} hasCloseButton>
        <p>İçerik</p>
      </Dialog>,
    )

    // Act
    await user.click(screen.getByRole('button', { name: 'Paneli kapat' }))

    // Assert
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('givenHasCloseButtonAndCannotClose_whenRendered_thenCloseButtonDisabled', () => {
    // Act
    render(
      <Dialog title="Panel" isOpen onClose={vi.fn()} hasCloseButton canClose={false}>
        <p>İçerik</p>
      </Dialog>,
    )

    // Assert
    expect(screen.getByRole('button', { name: 'Paneli kapat' })).toBeDisabled()
  })

  it('givenReactNodeTitle_whenRendered_thenDialogNamedByTitleText', () => {
    // Act
    render(
      <Dialog
        title={
          <span>
            Yıllık <mark>izin</mark>
          </span>
        }
        isOpen
        onClose={vi.fn()}
      >
        <p>İçerik</p>
      </Dialog>,
    )

    // Assert
    expect(screen.getByRole('dialog', { name: 'Yıllık izin' })).toBeInTheDocument()
  })

  it('givenReturnFocusRef_whenDialogUnmounted_thenFocusMovesToRefElement', () => {
    // Arrange
    const returnFocusRef = createRef<HTMLButtonElement>()
    const { rerender } = render(
      <>
        <button type="button">Başka</button>
        <button type="button" ref={returnFocusRef}>
          Sonuç
        </button>
        <Dialog title="Panel" isOpen onClose={vi.fn()} returnFocusRef={returnFocusRef}>
          <button type="button">İçeride</button>
        </Dialog>
      </>,
    )
    const other = screen.getByRole('button', { name: 'Başka' })
    other.focus()

    // Act
    rerender(
      <>
        <button type="button">Başka</button>
        <button type="button" ref={returnFocusRef}>
          Sonuç
        </button>
      </>,
    )

    // Assert
    expect(screen.getByRole('button', { name: 'Sonuç' })).toHaveFocus()
  })

  it('givenNoReturnFocusRef_whenDialogUnmounted_thenFocusReturnsToOpener', async () => {
    // Arrange
    const user = userEvent.setup()
    const opener = document.createElement('button')
    document.body.append(opener)
    opener.focus()
    const { unmount } = render(
      <Dialog title="Panel" isOpen onClose={vi.fn()}>
        <button type="button">İçeride</button>
      </Dialog>,
    )
    await user.tab()

    // Act
    unmount()

    // Assert
    expect(opener).toHaveFocus()
    opener.remove()
  })

  it('givenOpenDialog_whenTabOnLastAndShiftTabOnFirst_thenFocusWraps', async () => {
    // Arrange
    const user = userEvent.setup()
    render(
      <Dialog title="Panel" isOpen onClose={vi.fn()}>
        <button type="button">Birinci</button>
        <button type="button">İkinci</button>
      </Dialog>,
    )
    const first = screen.getByRole('button', { name: 'Birinci' })
    const last = screen.getByRole('button', { name: 'İkinci' })
    expect(first).toHaveFocus()

    // Act
    await user.keyboard('{Shift>}{Tab}{/Shift}')
    const afterShiftTab = document.activeElement
    await user.tab()

    // Assert
    expect(afterShiftTab).toBe(last)
    expect(first).toHaveFocus()
  })

  it('givenAlwaysOpenDialogInStrictMode_whenCleanupCloseEventArrivesAfterReopen_thenOnCloseNotCalled', async () => {
    // Arrange
    const originalClose = HTMLDialogElement.prototype.close
    HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
      if (this.hasAttribute('open')) {
        this.removeAttribute('open')
        queueMicrotask(() => this.dispatchEvent(new Event('close')))
      }
    }
    const onClose = vi.fn()

    try {
      // Act
      render(
        <StrictMode>
          <Dialog title="Panel" isOpen onClose={onClose}>
            <p>İçerik</p>
          </Dialog>
        </StrictMode>,
      )
      await act(async () => {
        await Promise.resolve()
      })

      // Assert
      expect(onClose).not.toHaveBeenCalled()
      expect(screen.getByRole('dialog', { name: 'Panel' })).toHaveAttribute('open')
    } finally {
      HTMLDialogElement.prototype.close = originalClose
    }
  })

  it('givenOpenDialog_whenNativeCloseEventWhileClosed_thenOnCloseCalled', () => {
    // Arrange
    const onClose = vi.fn()
    render(
      <Dialog title="Panel" isOpen onClose={onClose}>
        <p>İçerik</p>
      </Dialog>,
    )

    // Act
    act(() => (screen.getByRole('dialog') as HTMLDialogElement).close())

    // Assert
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
