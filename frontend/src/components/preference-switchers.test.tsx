import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import i18n from '@/i18n'
import { LanguageSwitcher, ThemeSwitcher } from './preference-switchers'
import { ThemeProvider } from './theme-provider'

function withQueryClient(children: ReactNode) {
  return <QueryClientProvider client={new QueryClient()}>{children}</QueryClientProvider>
}

describe('ThemeSwitcher', () => {
  it('applies and remembers the dark theme', async () => {
    await i18n.changeLanguage('en')
    render(
      withQueryClient(
        <ThemeProvider>
          <ThemeSwitcher />
        </ThemeProvider>,
      ),
    )

    await userEvent.click(screen.getByRole('button', { name: 'Dark' }))

    expect(document.documentElement).toHaveClass('dark')
    expect(localStorage.getItem('cf_theme')).toBe('dark')
    expect(screen.getByRole('button', { name: 'Dark' })).toHaveAttribute('aria-pressed', 'true')
  })
})

describe('LanguageSwitcher', () => {
  it('switches the interface language and remembers it', async () => {
    await i18n.changeLanguage('en')
    render(withQueryClient(<ThemeProvider><LanguageSwitcher /></ThemeProvider>))

    await userEvent.click(screen.getByRole('button', { name: 'KZ' }))

    expect(i18n.resolvedLanguage).toBe('kk')
    expect(localStorage.getItem('cf_lang')).toBe('kk')
    expect(document.documentElement.lang).toBe('kk')
  })
})
