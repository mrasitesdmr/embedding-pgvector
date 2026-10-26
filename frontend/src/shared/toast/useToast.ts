import { createContext, use } from 'react'

export const TOAST_DURATION_MS = 5000

export type ToastApi = {
  showToast: (message: string) => void
}

export const ToastContext = createContext<ToastApi | null>(null)

export function useToast(): ToastApi {
  const toastApi = use(ToastContext)
  if (toastApi === null) {
    throw new Error('useToast must be used inside ToastProvider')
  }
  return toastApi
}
