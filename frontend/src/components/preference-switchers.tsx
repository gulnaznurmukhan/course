import { Flower2, Monitor, Moon, Sun } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { THEMES, type Theme } from '@/components/theme-provider'
import { usePreferences } from '@/features/settings/use-preferences'
import { SUPPORTED_LANGUAGES } from '@/i18n'
import { cn } from '@/lib/utils'

/** Segmented control: one pill moves between the options (transform only). */
function Segmented<T extends string>({ label, value, options, onChange, tone = 'default' }: {
  label: string
  value: T
  options: { value: T; label: string; icon?: typeof Sun; title?: string }[]
  onChange: (value: T) => void
  tone?: 'default' | 'inverse'
}) {
  const index = Math.max(0, options.findIndex((o) => o.value === value))
  const inverse = tone === 'inverse'
  return (
    <div
      role="group"
      aria-label={label}
      className={cn(
        'relative grid h-9 rounded-full p-1',
        inverse ? 'bg-white/10 ring-1 ring-white/10' : 'bg-secondary/70 ring-1 ring-foreground/5',
      )}
      style={{ gridTemplateColumns: `repeat(${options.length}, minmax(0, 1fr))` }}
    >
      <span
        aria-hidden
        className={cn(
          'absolute top-1 bottom-1 left-1 rounded-full transition-transform duration-250 ease-[var(--ease-out-expo)]',
          inverse ? 'bg-white/15' : 'bg-card shadow-[var(--shadow-button)] ring-1 ring-foreground/10',
        )}
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
              'relative z-10 flex items-center justify-center gap-1.5 rounded-full px-3 font-mono text-xs font-medium tracking-wide outline-none transition-colors duration-150 focus-visible:ring-2 focus-visible:ring-ring',
              inverse
                ? active ? 'text-white' : 'text-white/60 hover:text-white'
                : active ? 'text-foreground' : 'text-muted-foreground hover:text-foreground',
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

export function LanguageSwitcher({ tone }: { tone?: 'default' | 'inverse' }) {
  const { t } = useTranslation()
  const { language, setLanguage } = usePreferences()
  return (
    <Segmented
      tone={tone}
      label={t('language.label')}
      value={language}
      onChange={setLanguage}
      options={SUPPORTED_LANGUAGES.map((lng) => ({ value: lng, label: t(`language.${lng}`) }))}
    />
  )
}

const THEME_ICONS = { light: Sun, dark: Moon, pink: Flower2, system: Monitor } as const

export function ThemeSwitcher({ tone }: { tone?: 'default' | 'inverse' }) {
  const { t } = useTranslation()
  const { theme, setTheme } = usePreferences()
  return (
    <Segmented
      tone={tone}
      label={t('theme.label')}
      value={theme}
      onChange={setTheme}
      options={THEMES.map((option: Theme) => ({
        value: option,
        label: t(`theme.${option}`),
        title: t(`theme.${option}`),
        icon: THEME_ICONS[option],
      }))}
    />
  )
}
