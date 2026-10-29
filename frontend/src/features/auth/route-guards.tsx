import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'

import { FullPageLoader } from '@/components/full-page-loader'
import { useCurrentUser } from './use-auth'

/**
 * Hides protected pages from signed-out visitors. This is only for UX:
 * the backend enforces authentication and permissions on every request.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { data: user, isPending } = useCurrentUser()
  const location = useLocation()

  if (isPending) return <FullPageLoader />
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return children
}

/** Sends already signed-in users away from the sign-in / sign-up pages. */
export function RedirectIfAuthenticated({ children }: { children: ReactNode }) {
  const { data: user, isPending } = useCurrentUser()

  if (isPending) return <FullPageLoader />
  if (user) return <Navigate to="/" replace />
  return children
}
