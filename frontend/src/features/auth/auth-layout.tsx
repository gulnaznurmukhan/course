import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { GalaxyScene } from '@/components/galaxy-scene'
import { LogoMark } from '@/components/logo'
import { LanguageSwitcher, ThemeSwitcher } from '@/components/preference-switchers'
import { formatMoney } from '@/lib/format'

interface AuthLayoutProps {
  title: string
  description?: string
  children: ReactNode
}

const START_TOTAL = 1_240_000

/** Counts from the shown value to the target in ~900 ms (text only). */
function useCountUp(target: number) {
  const [value, setValue] = useState(target)
  const fromRef = useRef(target)
  useEffect(() => {
    const from = fromRef.current
    const start = performance.now()
    let frame = 0
    const tick = (now: number) => {
      const p = Math.min(1, (now - start) / 900)
      setValue(Math.round(from + (target - from) * (1 - Math.pow(1 - p, 3))))
      if (p < 1) frame = requestAnimationFrame(tick)
      else fromRef.current = target
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [target])
  return value
}

/**
 * Sign-in layout: a space panel with the particle galaxy on the left (on top on
 * phones, shorter) and the form on the canvas on the right.
 */
export function AuthLayout({ title, description, children }: AuthLayoutProps) {
  const { t } = useTranslation()
  const [total, setTotal] = useState(START_TOTAL)
  const shown = useCountUp(total)
  const onIncome = useCallback((amount: number) => setTotal((x) => x + amount), [])

  return (
    <div className="min-h-svh lg:grid lg:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)]">
      <aside className="cf-hero relative isolate flex h-[52svh] min-h-[380px] flex-col overflow-hidden lg:h-auto lg:min-h-svh">
        <GalaxyScene onIncome={onIncome} />

        <header className="relative z-10 flex flex-wrap items-center justify-between gap-3 px-5 py-5 sm:px-8">
          <span className="inline-flex items-center gap-2" translate="no">
            <LogoMark className="size-7" />
            <span className="text-base font-medium tracking-tight">
              Client<span className="text-[var(--hero-a)]">Flow</span>
            </span>
          </span>
          <div className="flex items-center gap-2 lg:hidden">
            <LanguageSwitcher tone="inverse" />
            <ThemeSwitcher tone="inverse" />
          </div>
        </header>

        <div className="flex-1" />

        <div className="relative z-10 hidden px-8 pb-10 lg:block xl:px-12 xl:pb-12">
          <p className="font-mono text-[11px] tracking-[0.12em] text-[var(--hero-muted)] uppercase">{t('scene.income')}</p>
          <p className="mt-1.5 font-mono text-[2rem] leading-none font-medium tracking-[-0.04em] text-[var(--hero-fg)] tabular-nums">
            {formatMoney(shown, 'KZT')}
          </p>
          <h2 className="mt-8 max-w-[32rem] text-[2.6rem] leading-[1.02] font-medium tracking-[-0.04em] text-[var(--hero-fg)] xl:text-[3.2rem]">
            {t('scene.headline')}
          </h2>
          <p className="mt-3 max-w-[32rem] text-base text-[var(--hero-muted)]">{t('scene.sub')}</p>
        </div>
      </aside>

      <div className="flex min-h-[54svh] flex-col lg:min-h-svh">
        <header className="hidden items-center justify-end gap-2 px-8 py-5 lg:flex">
          <LanguageSwitcher />
          <ThemeSwitcher />
        </header>
        <main className="flex flex-1 items-center justify-center px-5 py-10 sm:px-10">
          <div className="cf-page w-full max-w-[26rem]">
            <h1 className="text-[2rem] leading-[1.1] font-medium tracking-[-0.04em] sm:text-[2.4rem]">{title}</h1>
            {description ? <p className="mt-2 text-[15px] text-muted-foreground">{description}</p> : null}
            <div className="mt-8">{children}</div>
          </div>
        </main>
        <footer className="px-5 py-5 font-mono text-[11px] tracking-wide text-muted-foreground sm:px-10">{t('app.tagline')}</footer>
      </div>
    </div>
  )
}
