import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'

import { useTheme, type Theme } from '@/components/theme-provider'
import type { Me } from '@/features/auth/api'
import { ME_QUERY_KEY, useCurrentUser } from '@/features/auth/use-auth'
import type { Language } from '@/i18n'
import { settingsApi } from './api'

const SYNCED_KEY = 'cf_prefs_synced_for'
const toServerTheme = (theme: Theme) => theme.toUpperCase() as Me['theme']
const toLocalTheme = (theme: Me['theme']) => theme.toLowerCase() as Theme

/**
 * Language and theme are stored in the account. Changing them in the UI updates
 * the browser immediately and saves them on the server for other devices.
 */
export function usePreferences() {
  const { i18n } = useTranslation()
  const { theme, setTheme } = useTheme()
  const { data: user } = useCurrentUser()
  const queryClient = useQueryClient()

  const save = useMutation({
    mutationFn: settingsApi.updateProfile,
    onSuccess: (me) => queryClient.setQueryData(ME_QUERY_KEY, me),
  })

  return {
    language: (i18n.resolvedLanguage ?? 'ru') as Language,
    theme,
    setLanguage: (lng: Language) => {
      void i18n.changeLanguage(lng)
      if (user) save.mutate({ preferredLanguage: lng })
    },
    setTheme: (next: Theme) => {
      setTheme(next)
      if (user) save.mutate({ theme: toServerTheme(next) })
    },
  }
}

/**
 * Applies the account's language and theme once per sign-in on this browser.
 * The marker lives in localStorage, so a lazy re-mount of the layout or a page
 * reload never overrides a choice the user made locally afterwards.
 */
export function PreferencesSync() {
  const { i18n } = useTranslation()
  const { setTheme } = useTheme()
  const { data: user } = useCurrentUser()

  useEffect(() => {
    if (!user) return
    let synced: string | null = null
    try {
      synced = localStorage.getItem(SYNCED_KEY)
    } catch {
      /* storage unavailable: apply once per mount */
    }
    if (synced === user.id) return
    try {
      localStorage.setItem(SYNCED_KEY, user.id)
    } catch {
      /* ignore */
    }
    if (i18n.resolvedLanguage !== user.preferredLanguage) void i18n.changeLanguage(user.preferredLanguage)
    setTheme(toLocalTheme(user.theme))
  }, [user, i18n, setTheme])

  return null
}

/** Call on sign-out so the next account's preferences are applied again. */
export function resetPreferencesSync() {
  try {
    localStorage.removeItem(SYNCED_KEY)
  } catch {
    /* ignore */
  }
}
