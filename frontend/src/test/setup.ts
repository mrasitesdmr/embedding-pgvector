import { File as NodeFile } from 'node:buffer'
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, expect } from 'vitest'
import 'vitest-axe/extend-expect'
import * as axeMatchers from 'vitest-axe/matchers'
import { server } from './server.ts'

expect.extend(axeMatchers)

beforeAll(() => {
  server.listen({ onUnhandledFrame: 'error' })
  globalThis.fetch = withNativeFormData(globalThis.fetch)
})

afterEach(() => {
  cleanup()
  server.resetHandlers()
  window.localStorage.clear()
})

afterAll(() => server.close())

const NodeFormData = (await new Response(new URLSearchParams()).formData()).constructor as typeof FormData

function withNativeFormData(interceptedFetch: typeof fetch): typeof fetch {
  return async (input, init) => {
    if (!(init?.body instanceof FormData)) {
      return interceptedFetch(input, init)
    }
    const nativeBody = new NodeFormData()
    for (const [key, value] of init.body.entries()) {
      if (typeof value === 'string') {
        nativeBody.append(key, value)
      } else {
        nativeBody.append(key, new NodeFile([await value.arrayBuffer()], value.name, { type: value.type }))
      }
    }
    return interceptedFetch(input, { ...init, body: nativeBody })
  }
}

if (typeof HTMLDialogElement.prototype.showModal !== 'function') {
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    if (this.hasAttribute('open')) {
      this.removeAttribute('open')
      this.dispatchEvent(new Event('close'))
    }
  }
}

document.addEventListener('keydown', (event) => {
  if (event.key !== 'Escape') {
    return
  }
  const openDialogs = document.querySelectorAll('dialog[open]')
  const dialog = openDialogs.item(openDialogs.length - 1)
  if (dialog === null) {
    return
  }
  const cancelEvent = new Event('cancel', { cancelable: true })
  dialog.dispatchEvent(cancelEvent)
  if (!cancelEvent.defaultPrevented) {
    ;(dialog as HTMLDialogElement).close()
  }
})

