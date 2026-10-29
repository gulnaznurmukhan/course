import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { Logo } from '@/components/logo'
import { FlowScene } from '@/components/flow-scene'
import { LanguageSwitcher, ThemeSwitcher } from '@/components/preference-switchers'

interface AuthLayoutProps {
  title: string
  description?: string
  children: ReactNode
}

/**
 * Two-panel sign-in layout: the form on the left, an animated client -> order -> payment scene on the right
 * (hidden on phones). The form card rises in on mount.
 */
export function AuthLayout({ title, description, children }: AuthLayoutProps) {
  const { t } = useTranslation()
  return (
    <div className="grid min-h-svh lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
      <div className="flex flex-col">
        <header className="flex flex-wrap items-center justify-between gap-3 px-4 py-4 sm:px-8">
          <Logo />
          <div className="flex flex-wrap items-center gap-2">
            <LanguageSwitcher />
            <ThemeSwitcher />
          </div>
        </header>
        <main className="flex flex-1 items-center justify-center px-4 py-8 sm:px-8">
          <div className="cf-page w-full max-w-sm">
            <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
            {description ? <p className="mt-1.5 text-sm text-muted-foreground">{description}</p> : null}
            <div className="mt-6">{children}</div>
          </div>
        </main>
        <footer className="px-4 py-4 text-xs text-muted-foreground sm:px-8">{t('app.tagline')}</footer>
      </div>

      <aside className="relative hidden items-center justify-center overflow-hidden bg-secondary p-10 lg:flex">
        <FlowScene className="w-full" />
        <p className="absolute right-10 bottom-8 left-10 text-sm text-muted-foreground">{t('auth.sceneCaption')}</p>
      </aside>
    </div>
  )
}
