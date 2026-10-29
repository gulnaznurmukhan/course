import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, CheckCheck } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'

import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useWorkspace } from '@/features/workspace/workspace-context'
import { apiFetch } from '@/lib/api'
import { formatDate, formatDateTime, formatMoney } from '@/lib/format'
import type { Page } from '@/lib/types'
import { cn } from '@/lib/utils'

export interface AppNotification {
  id: string
  type: 'DEADLINE_APPROACHING' | 'ORDER_OVERDUE' | 'ORDER_CHANGED' | 'PAYMENT_REMINDER'
  orderId: string | null
  payload: Record<string, string>
  read: boolean
  createdAt: string
}

const base = (ws: string) => `/workspaces/${ws}/notifications`

/** Builds the sentence in the current language from the type and the payload. */
export function useNotificationText() {
  const { t } = useTranslation()
  return (n: AppNotification) => {
    const p = n.payload
    const values = {
      ...p,
      date: p.deadline ? formatDate(p.deadline) : '',
      amount: p.balance ? formatMoney(p.balance, p.currency) : '',
      from: p.from ? t(`orderStatus.${p.from}`) : '',
      to: p.to ? t(`orderStatus.${p.to}`) : '',
    }
    return n.type === 'ORDER_CHANGED'
      ? t(`notifications.${p.change ?? 'UPDATED'}`, values)
      : t(`notifications.${n.type}`, values)
  }
}

export function NotificationBell() {
  const { t } = useTranslation()
  const { workspaceId } = useWorkspace()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const text = useNotificationText()
  const [ringing, setRinging] = useState(false)
  const previousCount = useRef<number | null>(null)

  const unread = useQuery({
    queryKey: ['ws', workspaceId, 'notifications', 'count'],
    queryFn: () => apiFetch<{ count: number }>(`${base(workspaceId)}/unread-count`),
    refetchInterval: 60_000,
  })
  const list = useQuery({
    queryKey: ['ws', workspaceId, 'notifications', 'list'],
    queryFn: () => apiFetch<Page<AppNotification>>(`${base(workspaceId)}?size=15`),
    enabled: false,
  })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['ws', workspaceId, 'notifications'] })

  const markRead = useMutation({
    mutationFn: (id: string) => apiFetch<void>(`${base(workspaceId)}/${id}/read`, { method: 'POST' }),
    onSettled: refresh,
  })
  const markAll = useMutation({
    mutationFn: () => apiFetch<void>(`${base(workspaceId)}/read-all`, { method: 'POST' }),
    onSettled: refresh,
  })

  const count = unread.data?.count ?? 0

  // Shake the bell when the unread count goes up (not on the first load)
  useEffect(() => {
    if (unread.data === undefined) return
    if (previousCount.current !== null && count > previousCount.current) {
      setRinging(true)
      const timer = setTimeout(() => setRinging(false), 1000)
      return () => clearTimeout(timer)
    }
    previousCount.current = count
  }, [count, unread.data])

  return (
    <DropdownMenu onOpenChange={(open) => open && void list.refetch()}>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="relative rounded-full"
          aria-label={count > 0 ? t('notifications.unread', { count }) : t('notifications.title')}
        >
          <Bell className={cn('size-[18px]', ringing && 'cf-ring')} />
          {count > 0 ? (
            <span
              aria-hidden
              className="cf-num absolute -top-0.5 -right-0.5 flex min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[10px] leading-4 font-semibold text-primary-foreground ring-2 ring-background"
            >
              {count > 99 ? '99+' : count}
            </span>
          ) : null}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-80 sm:w-96">
        <div className="flex items-center justify-between gap-2 px-2 py-1.5">
          <DropdownMenuLabel className="p-0">{t('notifications.title')}</DropdownMenuLabel>
          {count > 0 ? (
            <Button variant="ghost" size="sm" onClick={() => markAll.mutate()}>
              <CheckCheck />
              {t('notifications.markAll')}
            </Button>
          ) : null}
        </div>
        <DropdownMenuSeparator />
        <div className="max-h-96 overflow-y-auto overscroll-contain">
          {list.data?.items.length === 0 ? (
            <p className="px-2 py-6 text-center text-sm text-muted-foreground">{t('notifications.empty')}</p>
          ) : null}
          {list.data?.items.map((n) => (
            <DropdownMenuItem
              key={n.id}
              className="flex items-start gap-2 whitespace-normal"
              onSelect={() => {
                if (!n.read) markRead.mutate(n.id)
                if (n.orderId) navigate(`/orders/${n.orderId}`)
              }}
            >
              <span
                className={cn('mt-1.5 size-2 shrink-0 rounded-full', n.read ? 'bg-transparent' : 'bg-primary')}
                aria-hidden
              />
              <span className="grid min-w-0 gap-0.5">
                <span className={cn('text-sm break-words', !n.read && 'font-medium')}>{text(n)}</span>
                <span className="text-xs text-muted-foreground">{formatDateTime(n.createdAt)}</span>
              </span>
            </DropdownMenuItem>
          ))}
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
