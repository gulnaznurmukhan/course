import { useQuery } from '@tanstack/react-query'
import { ArrowRight, CornerDownLeft, Languages, Package, Palette, Search, User } from 'lucide-react'
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'

import { THEMES } from '@/components/theme-provider'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { clientsApi } from '@/features/clients/api'
import { ordersApi } from '@/features/orders/api'
import { OrderStatusBadge } from '@/features/orders/order-status-badge'
import { usePreferences } from '@/features/settings/use-preferences'
import { useWorkspace } from '@/features/workspace/workspace-context'
import { SUPPORTED_LANGUAGES } from '@/i18n'
import { formatMoney } from '@/lib/format'
import { cn } from '@/lib/utils'

export interface PaletteNavItem {
  to: string
  labelKey: string
  icon: typeof Search
}

interface Entry {
  id: string
  group: string
  icon: ReactNode
  label: ReactNode
  hint?: ReactNode
  run: () => void
}

/** Debounced copy of a value, so the API is not called on every keystroke. */
function useDebounced<T>(value: T, ms: number) {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const id = window.setTimeout(() => setDebounced(value), ms)
    return () => window.clearTimeout(id)
  }, [value, ms])
  return debounced
}

const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform)

/** Button in the header that looks like a search field and opens the palette. */
export function CommandPaletteTrigger({ onOpen }: { onOpen: () => void }) {
  const { t } = useTranslation()
  return (
    <button
      type="button"
      onClick={onOpen}
      className="group flex h-9 w-full max-w-md items-center gap-2 rounded-full bg-muted/70 px-3 text-sm text-muted-foreground ring-1 ring-foreground/5 transition-colors outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring"
    >
      <Search className="size-4 shrink-0" aria-hidden />
      <span className="min-w-0 flex-1 truncate text-left">{t('palette.trigger')}</span>
      <kbd className="hidden shrink-0 rounded-md bg-card px-1.5 py-0.5 font-mono text-[10px] text-muted-foreground ring-1 ring-foreground/10 sm:inline">
        {isMac ? '⌘' : 'Ctrl'} K
      </kbd>
    </button>
  )
}

/**
 * Ctrl/⌘+K palette: one input that finds orders and clients, opens any page and
 * switches the theme or language. Arrow keys move, Enter runs, Esc closes.
 */
export function CommandPalette({ open, onOpenChange, navItems }: {
  open: boolean
  onOpenChange: (open: boolean) => void
  navItems: readonly PaletteNavItem[]
}) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { workspaceId } = useWorkspace()
  const { setTheme, setLanguage } = usePreferences()
  const [text, setText] = useState('')
  const [active, setActive] = useState(0)
  const listRef = useRef<HTMLDivElement>(null)
  const search = useDebounced(text.trim(), 180)

  // Global shortcut
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        onOpenChange(!open)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onOpenChange])

  const orders = useQuery({
    queryKey: ['ws', workspaceId, 'palette', 'orders', search],
    queryFn: () => ordersApi.list(workspaceId, { search, size: 5, archive: 'ALL', sort: 'UPDATED_AT', direction: 'DESC' }),
    enabled: open && search.length > 0,
  })
  const clients = useQuery({
    queryKey: ['ws', workspaceId, 'palette', 'clients', search],
    queryFn: () => clientsApi.list(workspaceId, { search, size: 4, status: 'ALL' }),
    enabled: open && search.length > 0,
  })

  const close = () => {
    onOpenChange(false)
    setText('')
    setActive(0)
  }

  const entries = useMemo<Entry[]>(() => {
    const q = text.trim().toLowerCase()
    const match = (s: string) => !q || s.toLowerCase().includes(q)
    const list: Entry[] = []
    for (const o of orders.data?.items ?? []) {
      list.push({
        id: `o-${o.id}`,
        group: t('palette.orders'),
        icon: <Package className="size-4" />,
        label: (
          <span className="flex min-w-0 items-center gap-2">
            <span className="font-mono text-xs text-muted-foreground" translate="no">{o.orderNumber}</span>
            <span className="truncate">{o.title}</span>
          </span>
        ),
        hint: (
          <span className="flex items-center gap-2">
            <span className="hidden font-mono text-xs tabular-nums sm:inline">{formatMoney(o.totalAmount, o.currency)}</span>
            <OrderStatusBadge status={o.status} />
          </span>
        ),
        run: () => navigate(`/orders/${o.id}`),
      })
    }
    for (const c of clients.data?.items ?? []) {
      list.push({
        id: `c-${c.id}`,
        group: t('palette.clients'),
        icon: <User className="size-4" />,
        label: <span className="truncate">{c.name}</span>,
        hint: <span className="truncate text-xs text-muted-foreground">{c.phone ?? c.email ?? c.company ?? ''}</span>,
        run: () => navigate(`/clients/${c.id}`),
      })
    }
    if (q) {
      list.push({
        id: 'search-orders',
        group: t('palette.orders'),
        icon: <Search className="size-4" />,
        label: t('palette.searchAll', { text: text.trim() }),
        run: () => navigate(`/orders?search=${encodeURIComponent(text.trim())}`),
      })
    }
    for (const item of navItems) {
      const label = t(item.labelKey)
      if (!match(label)) continue
      const Icon = item.icon
      list.push({
        id: `nav-${item.to}`,
        group: t('palette.goTo'),
        icon: <Icon className="size-4" />,
        label,
        hint: <ArrowRight className="size-3.5 text-muted-foreground" />,
        run: () => navigate(item.to),
      })
    }
    for (const theme of THEMES) {
      const label = `${t('theme.label')}: ${t(`theme.${theme}`)}`
      if (!match(label) && !match(t('palette.appearance'))) continue
      list.push({ id: `theme-${theme}`, group: t('palette.appearance'), icon: <Palette className="size-4" />, label, run: () => setTheme(theme) })
    }
    for (const lng of SUPPORTED_LANGUAGES) {
      const label = `${t('language.label')}: ${t(`language.${lng}`)}`
      if (!match(label) && !match(t('palette.appearance'))) continue
      list.push({ id: `lng-${lng}`, group: t('palette.appearance'), icon: <Languages className="size-4" />, label, run: () => setLanguage(lng) })
    }
    return list
  }, [text, orders.data, clients.data, navItems, t, navigate, setTheme, setLanguage])

  const current = Math.min(active, Math.max(0, entries.length - 1))

  useEffect(() => {
    listRef.current?.querySelector<HTMLElement>(`[data-index="${current}"]`)?.scrollIntoView({ block: 'nearest' })
  }, [current])

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setActive((current + 1) % Math.max(1, entries.length))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActive((current - 1 + entries.length) % Math.max(1, entries.length))
    } else if (e.key === 'Enter') {
      e.preventDefault()
      const entry = entries[current]
      if (entry) {
        entry.run()
        close()
      }
    }
  }

  const loading = search.length > 0 && (orders.isFetching || clients.isFetching)
  let lastGroup = ''

  return (
    <Dialog open={open} onOpenChange={(v) => (v ? onOpenChange(true) : close())}>
      <DialogContent
        showCloseButton={false}
        className="top-[14vh] max-w-[calc(100%-1.5rem)] translate-y-0 gap-0 overflow-hidden p-0 sm:max-w-xl"
      >
        <DialogTitle className="sr-only">{t('palette.title')}</DialogTitle>
        <DialogDescription className="sr-only">{t('palette.description')}</DialogDescription>
        <div className="flex items-center gap-3 border-b px-4">
          <Search className={cn('size-4 shrink-0 text-muted-foreground', loading && 'animate-pulse')} aria-hidden />
          <input
            autoFocus
            value={text}
            onChange={(e) => {
              setText(e.target.value)
              setActive(0)
            }}
            onKeyDown={onKeyDown}
            placeholder={t('palette.placeholder')}
            aria-label={t('palette.placeholder')}
            role="combobox"
            aria-expanded
            aria-controls="cf-palette-list"
            aria-activedescendant={entries[current] ? `cf-pal-${entries[current].id}` : undefined}
            spellCheck={false}
            autoComplete="off"
            className="h-13 min-w-0 flex-1 bg-transparent text-[15px] outline-none placeholder:text-muted-foreground"
          />
          <kbd className="rounded-md bg-muted px-1.5 py-0.5 font-mono text-[10px] text-muted-foreground">Esc</kbd>
        </div>
        <div ref={listRef} id="cf-palette-list" role="listbox" className="max-h-[min(60vh,26rem)] overflow-y-auto overscroll-contain p-2">
          {entries.length === 0 ? (
            <p className="px-3 py-8 text-center text-sm text-muted-foreground">{loading ? t('common.loading') : t('palette.empty')}</p>
          ) : (
            entries.map((entry, i) => {
              const header = entry.group !== lastGroup ? entry.group : null
              lastGroup = entry.group
              return (
                <div key={entry.id}>
                  {header ? <p className="cf-eyebrow px-3 pt-3 pb-1.5 first:pt-1">{header}</p> : null}
                  <div
                    id={`cf-pal-${entry.id}`}
                    role="option"
                    aria-selected={i === current}
                    data-index={i}
                    onMouseMove={() => setActive(i)}
                    onClick={() => {
                      entry.run()
                      close()
                    }}
                    className={cn(
                      'flex cursor-pointer items-center gap-3 rounded-lg px-3 py-2 text-sm',
                      i === current ? 'bg-accent text-accent-foreground' : 'text-foreground',
                    )}
                  >
                    <span className={cn('shrink-0', i === current ? '' : 'text-muted-foreground')}>{entry.icon}</span>
                    <span className="min-w-0 flex-1 truncate">{entry.label}</span>
                    {entry.hint ? <span className="shrink-0">{entry.hint}</span> : null}
                    {i === current ? <CornerDownLeft className="size-3.5 shrink-0 opacity-60" aria-hidden /> : null}
                  </div>
                </div>
              )
            })
          )}
        </div>
        <div className="flex items-center gap-4 border-t px-4 py-2 font-mono text-[10px] text-muted-foreground">
          <span>↑↓ {t('palette.move')}</span>
          <span>↵ {t('palette.open')}</span>
          <span className="ml-auto">{isMac ? '⌘' : 'Ctrl'} K</span>
        </div>
      </DialogContent>
    </Dialog>
  )
}
