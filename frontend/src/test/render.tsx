import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { vi } from 'vitest'

import { AppRoutes } from '@/App'
import { ThemeProvider } from '@/components/theme-provider'

/** Renders the real routes at the given URL with fresh providers. */
export function renderApp(url: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[url]}>
          <AppRoutes />
        </MemoryRouter>
      </QueryClientProvider>
    </ThemeProvider>,
  )
}

type Handler = (url: string, init?: RequestInit) => { status: number; body?: unknown }

/** Replaces window.fetch with a small fake backend. */
export function mockBackend(handler: Handler) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (url.endsWith('/auth/csrf')) {
      document.cookie = 'XSRF-TOKEN=test-csrf-token'
      return new Response('{}', { status: 200 })
    }
    // A handler may throw to simulate a network failure
    const { status, body } = handler(url, init)
    return new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    })
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}
