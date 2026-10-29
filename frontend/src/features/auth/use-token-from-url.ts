import { useState } from 'react'

/**
 * Reads ?token=... once and immediately removes it from the address bar,
 * so the single-use token does not stay in browser history or get copied.
 */
export function useTokenFromUrl(): string | null {
  const [token] = useState(() => {
    const params = new URLSearchParams(window.location.search)
    const value = params.get('token')
    if (value) {
      params.delete('token')
      const query = params.toString()
      window.history.replaceState(null, '', window.location.pathname + (query ? `?${query}` : ''))
    }
    return value
  })
  return token
}
