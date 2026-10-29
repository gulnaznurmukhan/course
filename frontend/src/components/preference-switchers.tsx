import { Monitor, Moon, Sun } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import type { Theme } from '@/components/theme-provider'
import { usePreferences } from '@/features/settings/use-preferences'
import { SUPPORTED_LANGUAGES } from '@/i18n'
import { cn } from '@/lib/utils'

/** Segmented control: one pill moves between the options (transform only). */
function Segmented<T extends string>({ label, value, options, onChange }: {
  label: string
  value: T
  options: { value: T; label: string; icon?: typeof Sun; title?: string }[]
  onChange: (value: T) => void
}) {
  const index = Math.max(0, options.findIndex((o) => o.value === value))
  return (
    <div
      role="group"
      aria-label={label}
      className="relative grid h-9 rounded-full bg-muted p-1 ring-1 ring-foreground/5"
      style={{ gridTemplateColumns: `repeat(${options.length}, minmax(0, 1fr))` }}
    >
      <span
        aria-hidden
        className="absolute top-1 bottom-1 left-1 rounded-full bg-card shadow-[var(--shadow-card)] ring-1 ring-foreground/10 transition-transform duration-250 ease-[var(--ease-out-expo)]"
        style={{ width: `calc((100% - 0.5rem) / ${options.length})`, transform: `translateX(${index * 100}%)` }}
      />
      {options.map((o) => {
        const Icon = o.icon
        const active = o.value === value
        return (
          <button
            key={o.value}
            type="button"
            aria-pressed={active}
            aria-label={o.title ?? o.label}
            title={o.title}
            onClick={() => onChange(o.value)}
            className={cn(
              'relative z-10 flex items-center justify-center gap-1.5 rounded-full px-3 text-sm font-medium outline-none transition-colors duration-150 focus-visible:ring-2 focus-visible:ring-ring',
              active ? 'text-foreground' : 'text-muted-foreground hover:text-foreground',
            )}
          >
            {Icon ? <Icon className="size-4" aria-hidden /> : null}
            {Icon ? null : o.label}
          </button>
        )
      })}
    </div>
  )
}

export function LanguageSwitcher() {
  const { t } = useTranslation()
  const { language, setLanguage } = usePreferences()
  return (
    <Segmented
      label={t('language.label')}
      value={language}
      onChange={setLanguage}
      options={SUPPORTED_LANGUAGES.map((lng) => ({ value: lng, label: t(`language.${lng}`) }))}
    />
  )
}

const THEME_ICONS = { light: Sun, dark: Moon, system: Monitor } as const

export function ThemeSwitcher() {
  const { t } = useTranslation()
  const { theme, setTheme } = usePreferences()
  return (
    <Segmented
      label={t('theme.label')}
      value={theme}
      onChange={setTheme}
      options={(Object.keys(THEME_ICONS) as Theme[]).map((option) => ({
        value: option,
        label: t(`theme.${option}`),
        title: t(`theme.${option}`),
        icon: THEME_ICONS[option],
      }))}
    />
  )
}
