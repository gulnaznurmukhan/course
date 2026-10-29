import { useTranslation } from 'react-i18next'

import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import type { OrderStatus } from './api'

/*
 * Status = a coloured dot + the status word on a neutral chip. The dot carries
 * the colour; the text stays in the foreground colour, so it reads in both themes.
 */
const DOT: Record<OrderStatus, string> = {
  NEW: 'bg-sky-500',
  CONFIRMED: 'bg-primary',
  IN_PROGRESS: 'bg-warning',
  COMPLETED: 'bg-success',
  CANCELLED: 'bg-muted-foreground/60',
}

export function StatusDot({ status, className }: { status: OrderStatus; className?: string }) {
  return <span className={cn('inline-block size-2 shrink-0 rounded-full', DOT[status], className)} aria-hidden />
}

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  const { t } = useTranslation()
  return (
    <Badge variant="outline" className="gap-1.5 bg-card pl-1.5 text-foreground">
      <StatusDot status={status} />
      {t(`orderStatus.${status}`)}
    </Badge>
  )
}

export function OverdueBadge() {
  const { t } = useTranslation()
  return <Badge variant="destructive">{t('orders.overdue')}</Badge>
}
