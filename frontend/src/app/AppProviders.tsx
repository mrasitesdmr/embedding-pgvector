import { QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import type { ReactNode } from 'react'
import { ErrorBoundary } from 'react-error-boundary'
import type { FallbackProps } from 'react-error-boundary'
import { BrowserRouter } from 'react-router'
import { messageFor } from '../shared/api/errorMessages.ts'
import styles from './AppProviders.module.css'
import { createQueryClient } from './queryClient.ts'

type AppProvidersProps = { children: ReactNode }

export function AppProviders({ children }: AppProvidersProps) {
  const [queryClient] = useState(createQueryClient)
  return (
    <ErrorBoundary FallbackComponent={AppErrorFallback} onError={(error) => console.error(error)}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>{children}</BrowserRouter>
      </QueryClientProvider>
    </ErrorBoundary>
  )
}

function AppErrorFallback({ resetErrorBoundary }: FallbackProps) {
  return (
    <main className={styles.fallback}>
      <div role="alert" className={styles.box}>
        <h1 className={styles.title}>{messageFor('UNEXPECTED_ERROR')}</h1>
        <button type="button" className={styles.button} onClick={resetErrorBoundary}>
          Tekrar dene
        </button>
      </div>
    </main>
  )
}
