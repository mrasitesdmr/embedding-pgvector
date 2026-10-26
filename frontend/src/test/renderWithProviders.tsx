import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter } from 'react-router'
import { App } from '../app/App.tsx'
import { LocationProbe } from './LocationProbe.tsx'
import { locationState } from './locationState.ts'

export function currentUrl(): string {
  return locationState.url
}

export function createTestQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

export function renderWithProviders(ui: ReactElement, route = '/') {
  const queryClient = createTestQueryClient()
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <LocationProbe />
        {ui}
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

export function renderApp(route: string) {
  return renderWithProviders(<App />, route)
}
