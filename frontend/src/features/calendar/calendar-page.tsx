import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { PageHeader, Page } from '@/components/page-parts'
import { Button } from '@/components/ui/button'
import type { OrderStatus } from '@/features/orders/api'
import { StatusDot } from '@/features/orders/order-status-badge'
import { useWorkspace } from '@/features/workspace/workspace-context'
import { apiFetch } from '@/lib/api'
import { todayIso } from '@/lib/format'
import { cn } from '@/lib/utils'
import i18n from '@/i18n'

interface Entry {
  id: string
  orderNumber: string
  title: string
  clientName: string
  deadline: string
  status: OrderStatus
  overdue: boolean
}

const pad = (n: number) => String(n).padStart(2, '0')
const iso = (y: number, m: number, d: number) => `${y}-${pad(m + 1)}-${pad(d)}`
const MAX_PER_DAY = 3

/** Month calendar of order deadlines (Monday first). */
export function CalendarPage() {
  const { t } = useTranslation()
  const { workspaceId } = useWorkspace()
  const today = todayIso()
  const [cursor, setCursor] = useState(() => ({ year: Number(today.slice(0, 4)), month: Number(today.slice(5, 7)) - 1 }))
  const { year, month } = cursor
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const from = iso(year, month, 1)
  const to = iso(year, month, daysInMonth)

  const entries = useQuery({
    queryKey: ['ws', workspaceId, 'calendar', from],
    queryFn: () => apiFetch<Entry[]>(`/workspaces/${workspaceId}/calendar?from=${from}&to=${to}`),
    placeholderData: keepPreviousData,
  })
  const byDay = new Map<string, Entry[]>()
  for (const e of entries.data ?? []) byDay.set(e.deadline, [...(byDay.get(e.deadline) ?? []), e])

  const locale = i18n.resolvedLanguage === 'kk' ? 'kk-KZ' : i18n.resolvedLanguage === 'en' ? 'en-US' : 'ru-RU'
  const title = new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric' }).format(new Date(year, month, 1))
  const weekdays = Array.from({ length: 7 }, (_, i) =>
    new Intl.DateTimeFormat(locale, { weekday: 'short' }).format(new Date(2024, 0, 1 + i))) // 2024-01-01 is a Monday
  const offset = (new Date(year, month, 1).getDay() + 6) % 7
  const cells = [...Array<null>(offset).fill(null), ...Array.from({ length: daysInMonth }, (_, i) => i + 1)]

  const shift = (delta: number) => setCursor(({ year: y, month: m }) => {
    const d = new Date(y, m + delta, 1)
    return { year: d.getFullYear(), month: d.getMonth() }
  })

  return (
    <Page>
      <PageHeader
        title={t('calendar.title')}
        actions={
          <div className="flex items-center gap-1">
            <Button variant="outline" size="icon-sm" aria-label={t('calendar.prev')} onClick={() => shift(-1)}><ChevronLeft /></Button>
            <Button variant="outline" size="sm" onClick={() => setCursor({ year: Number(today.slice(0, 4)), month: Number(today.slice(5, 7)) - 1 })}>
              {t('calendar.today')}
            </Button>
            <Button variant="outline" size="icon-sm" aria-label={t('calendar.next')} onClick={() => shift(1)}><ChevronRight /></Button>
          </div>
        }
      />
      <h2 className="mb-3 text-lg font-semibold first-letter:uppercase" aria-live="polite">{title}</h2>
      <ApiErrorAlert error={entries.error} />

      {/* Desktop: month grid */}
      <div className="hidden overflow-hidden rounded-xl bg-card shadow-[var(--shadow-card)] ring-1 ring-foreground/10 sm:block">
        <div className="grid grid-cols-7 border-b bg-muted/50 text-center text-xs font-medium tracking-wide text-muted-foreground uppercase">
          {weekdays.map((d) => <div key={d} className="py-2 capitalize">{d}</div>)}
        </div>
        <div className="grid grid-cols-7">
          {cells.map((day, i) => {
            if (day === null) return <div key={`e${i}`} className="min-h-24 border-r border-b bg-muted/30" />
            const date = iso(year, month, day)
            const list = byDay.get(date) ?? []
            return (
              <div key={date} className={cn('min-h-24 border-r border-b border-border/70 p-1.5 transition-colors hover:bg-muted/40', date === today && 'bg-accent/40')}>
                <span className={cn('inline-flex size-6 items-center justify-center rounded-full text-xs',
                  date === today && 'bg-primary font-semibold text-primary-foreground')}>{day}</span>
                <ul className="mt-1 grid gap-1">
                  {list.slice(0, MAX_PER_DAY).map((e) => (
                    <li key={e.id}>
                      <Link to={`/orders/${e.id}`} title={`${e.orderNumber} · ${e.title} · ${e.clientName}`}
                            className={cn('flex items-center gap-1 truncate rounded-md px-1 py-0.5 text-xs transition-colors hover:bg-accent hover:text-accent-foreground',
                              e.overdue && 'text-destructive')}>
                        <StatusDot status={e.status} />
                        <span className="truncate">{e.title}</span>
                        <span className="sr-only">({t(`orderStatus.${e.status}`)})</span>
                      </Link>
                    </li>
                  ))}
                  {list.length > MAX_PER_DAY ? (
                    <li className="px-1 text-xs text-muted-foreground">{t('calendar.more', { count: list.length - MAX_PER_DAY })}</li>
                  ) : null}
                </ul>
              </div>
            )
          })}
        </div>
      </div>

      {/* Mobile: agenda list */}
      <ul className="grid gap-2 sm:hidden">
        {(entries.data ?? []).length === 0 ? <li className="text-sm text-muted-foreground">{t('calendar.empty')}</li> : null}
        {[...byDay.entries()].map(([date, list]) => (
          <li key={date} className="rounded-xl bg-card p-3 shadow-[var(--shadow-card)] ring-1 ring-foreground/10">
            <p className={cn('mb-1 text-sm font-medium', date === today && 'text-primary')}>
              {new Intl.DateTimeFormat(locale, { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date(`${date}T00:00`))}
            </p>
            {list.map((e) => (
              <Link key={e.id} to={`/orders/${e.id}`} className={cn('flex items-center gap-2 py-1 text-sm', e.overdue && 'text-destructive')}>
                <StatusDot status={e.status} />
                <span className="font-mono text-xs text-muted-foreground">{e.orderNumber}</span>
                <span className="truncate">{e.title}</span>
              </Link>
            ))}
          </li>
        ))}
      </ul>
      {entries.data && entries.data.length === 0 ? (
        <p className="mt-3 hidden text-sm text-muted-foreground sm:block">{t('calendar.empty')}</p>
      ) : null}
    </Page>
  )
}
