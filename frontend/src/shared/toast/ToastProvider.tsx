import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { TOAST_DURATION_MS, ToastContext } from './useToast.ts'
import type { ToastApi } from './useToast.ts'
import styles from './ToastProvider.module.css'

type Toast = {
  id: number
  message: string
}

type ToastProviderProps = {
  children: ReactNode
}

export function ToastProvider({ children }: ToastProviderProps) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const nextIdRef = useRef(0)
  const timersRef = useRef(new Set<number>())

  useEffect(() => {
    const timers = timersRef.current
    return () => {
      for (const timer of timers) {
        window.clearTimeout(timer)
      }
      timers.clear()
    }
  }, [])

  const showToast = useCallback((message: string) => {
    nextIdRef.current += 1
    const id = nextIdRef.current
    setToasts((current) => [...current, { id, message }])
    const timer = window.setTimeout(() => {
      timersRef.current.delete(timer)
      setToasts((current) => current.filter((toast) => toast.id !== id))
    }, TOAST_DURATION_MS)
    timersRef.current.add(timer)
  }, [])

  const toastApi = useMemo<ToastApi>(() => ({ showToast }), [showToast])

  return (
    <ToastContext value={toastApi}>
      {children}
      <div className={styles.region} role="status" aria-live="polite">
        {toasts.map((toast) => (
          <p key={toast.id} className={styles.toast}>
            {toast.message}
          </p>
        ))}
      </div>
    </ToastContext>
  )
}
