import { useQuery } from '@tanstack/react-query'
import { useState, type CSSProperties } from 'react'
import { useTranslation } from 'react-i18next'

import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useWorkspace } from '@/features/workspace/workspace-context'
import { apiFetch } from '@/lib/api'
import { formatMoney } from '@/lib/format'
import i18n from '@/i18n'

interface Revenue {
  months: string[] // "2026-03"
  series: Record<string, number[]> // currency -> 12 amounts
}

function monthLabel(month: string, style: 'short' | 'long') {
  const [y, m] = month.split('-').map(Number)
  const locale = i18n.resolvedLanguage === 'kk' ? 'kk-KZ' : i18n.resolvedLanguage === 'en' ? 'en-US' : 'ru-RU'
  return new Intl.DateTimeFormat(locale, style === 'short' ? { month: 'short' } : { month: 'long', year: 'numeric' })
    .format(new Date(y, m - 1, 1))
}

/**
 * Column chart, one series (one currency at a time, amounts in different
 * currencies are never added). Spec: <= 24px columns, 4px rounded top, square
 * at the baseline, one hue, hairline baseline, value labels only on the highest
 * and the current month, hover/focus tooltip on every column. Columns grow from
 * the baseline on mount, left to right.
 */
export function RevenueChart() {
  const { t } = useTranslation()
  const { workspaceId } = useWorkspace()
  const [currency, setCurrency] = useState<string | null>(null)
  const [active, setActive] = useState<number | null>(null)

  const revenue = useQuery({
    queryKey: ['ws', workspaceId, 'dashboard', 'revenue'],
    queryFn: () => apiFetch<Revenue>(`/workspaces/${workspaceId}/dashboard/revenue`),
  })
  const currencies = Object.keys(revenue.data?.series ?? {})
  const selected = currency && currencies.includes(currency) ? currency : currencies[0]
  const values = selected ? revenue.data!.series[selected] : []
  const max = Math.max(0, ...values)
  const maxIndex = values.indexOf(max)
  const lastIndex = values.length - 1
  // On narrow screens every other month label is hidden so they never collide
  const showLabel = (i: number) => i % 2 === lastIndex % 2

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('revenue.title')}</CardTitle>
        <CardDescription>{t('revenue.hint')}</CardDescription>
        {currencies.length > 1 ? (
          <CardAction>
            <Select value={selected} onValueChange={setCurrency}>
              <SelectTrigger size="sm" className="w-24" aria-label={t('revenue.currency')}><SelectValue /></SelectTrigger>
              <SelectContent>{currencies.map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}</SelectContent>
            </Select>
          </CardAction>
        ) : null}
      </CardHeader>
      <CardContent>
        {!selected || max === 0 ? (
          <p className="text-sm text-muted-foreground">{t('revenue.empty')}</p>
        ) : (
          <div className="relative min-w-0">
            <ul className="flex h-44 items-end gap-1.5 border-b border-border sm:gap-3" aria-label={t('revenue.title')}>
              {values.map((value, i) => {
                const month = revenue.data!.months[i]
                const label = `${monthLabel(month, 'long')}: ${formatMoney(value, selected)}`
                const showValue = value > 0 && (i === maxIndex || i === lastIndex)
                return (
                  <li key={month} className="relative flex h-full min-w-0 flex-1 flex-col items-center justify-end">
                    {showValue ? (
                      <span className="mb-1 hidden text-[10px] whitespace-nowrap text-muted-foreground tabular-nums sm:block">
                        {new Intl.NumberFormat(undefined, { notation: 'compact' }).format(value)}
                      </span>
                    ) : null}
                    <div
                      tabIndex={0}
                      aria-label={label}
                      onMouseEnter={() => setActive(i)}
                      onMouseLeave={() => setActive(null)}
                      onFocus={() => setActive(i)}
                      onBlur={() => setActive(null)}
                      className="cf-grow-y w-full max-w-6 rounded-t-[4px] bg-[var(--viz-series-1)] outline-none transition-opacity duration-150 focus-visible:ring-2 focus-visible:ring-ring"
                      style={{
                        height: `${Math.max(value > 0 ? 2 : 0, (value / max) * 85)}%`,
                        opacity: active !== null && active !== i ? 0.4 : 1,
                        '--cf-i': i,
                      } as CSSProperties}
                    />
                    {active === i ? (
                      <span role="tooltip"
                            className="pointer-events-none absolute bottom-full left-1/2 z-10 mb-1 -translate-x-1/2 rounded-md border bg-popover px-2 py-1 text-xs whitespace-nowrap text-popover-foreground shadow-[var(--shadow-raised)]">
                        {label}
                      </span>
                    ) : null}
                  </li>
                )
              })}
            </ul>
            <div className="mt-1.5 flex gap-1.5 sm:gap-3" aria-hidden>
              {revenue.data!.months.map((month, i) => (
                <span key={month} className={`min-w-0 flex-1 truncate text-center text-[10px] text-muted-foreground ${showLabel(i) ? '' : 'invisible sm:visible'}`}>
                  {monthLabel(month, 'short')}
                </span>
              ))}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
