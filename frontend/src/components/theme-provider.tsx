import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

export type Theme = 'light' | 'dark' | 'pink' | 'system'
export const THEMES: Theme[] = ['light', 'dark', 'pink', 'system']

const STORAGE_KEY = 'cf_theme'
const META_COLOR = { light: '#edf0f2', dark: '#0c0c0e', pink: '#fbdde9' } as const

interface ThemeContextValue {
  theme: Theme
  /** The theme actually on screen ("system" resolved to light or dark). */
  resolved: 'light' | 'dark' | 'pink'
  setTheme: (theme: Theme) => void
}

const ThemeContext = createContext<ThemeContextValue | null>(null)

function readStoredTheme(): Theme {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return stored === 'light' || stored === 'dark' || stored === 'pink' ? stored : 'system'
  } catch {
    return 'system'
  }
}

function resolve(theme: Theme): 'light' | 'dark' | 'pink' {
  if (theme !== 'system') return theme
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

/** Puts the "dark" or "pink" class on <html> and keeps the browser chrome colour in step. */
function applyTheme(theme: Theme) {
  const active = resolve(theme)
  const root = document.documentElement
  root.classList.toggle('dark', active === 'dark')
  root.classList.toggle('pink', active === 'pink')
  const meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')
  if (meta) meta.content = META_COLOR[active]
  return active
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(readStoredTheme)
  const [resolved, setResolved] = useState<'light' | 'dark' | 'pink'>(() => resolve(theme))

  useEffect(() => {
    setResolved(applyTheme(theme))
    if (theme !== 'system') return
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = () => setResolved(applyTheme('system'))
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [theme])

  const setTheme = useCallback((next: Theme) => {
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      /* ignore */
    }
    setThemeState(next)
  }, [])

  const value = useMemo(() => ({ theme, resolved, setTheme }), [theme, resolved, setTheme])
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme() {
  const context = useContext(ThemeContext)
  if (!context) throw new Error('useTheme must be used inside ThemeProvider')
  return context
}
