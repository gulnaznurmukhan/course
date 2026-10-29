import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api'
import { resetPreferencesSync } from '@/features/settings/use-preferences'
import { authApi, type Me } from './api'

export const ME_QUERY_KEY = ['me'] as const

/**
 * The signed-in user, or null when signed out.
 * The session itself lives in an HttpOnly cookie; the frontend only knows
 * whether /users/me answers 200 or 401.
 */
export function useCurrentUser() {
  return useQuery<Me | null>({
    queryKey: ME_QUERY_KEY,
    queryFn: async () => {
      try {
        return await authApi.me()
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null
        throw error
      }
    },
    staleTime: 5 * 60 * 1000,
    // When the server is down, pages that mount later must not refetch on their own:
    // that reset the query to "pending" and the guards looped loader <-> page forever
    retryOnMount: false,
  })
}

export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) =>
      authApi.login(email, password),
    onSuccess: (me) => queryClient.setQueryData(ME_QUERY_KEY, me),
  })
}

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: authApi.logout,
    onSettled: () => {
      // Forget every cached record of the previous user
      resetPreferencesSync()
      queryClient.clear()
      queryClient.setQueryData(ME_QUERY_KEY, null)
    },
  })
}
