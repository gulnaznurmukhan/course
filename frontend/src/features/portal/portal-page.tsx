import { useQuery } from '@tanstack/react-query'
import { Check, LinkIcon, XCircle } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'

import { Logo } from '@/components/logo'
import { LanguageSwitcher, ThemeSwitcher } from '@/components/preference-switchers'
import { FullPageLoader } from '@/components/full-page-loader'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { OrderStatusBadge } from '@/features/orders/order-status-badge'
import { orderExtrasApi } from '@/features/orders/extras-api'
import { PaymentStateBadge } from '@/features/payments/payment-state-badge'
import { formatDate, formatDateTime, formatMoney } from '@/lib/format'
import { cn } from '@/lib/utils'

const STEPS = ['NEW', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED'] as const

/** Public page opened from a customer link. No account needed; read-only. */
export function PortalPage() {
  const { t } = useTranslation()
  const { token = '' } = useParams()
  const order = useQuery({
    queryKey: ['public-order', token],
    queryFn: () => orderExtrasApi.publicOrder(token),
    retry: false,
  })

  const header = (
    <header className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 bg-background px-4 py-3">
      <Logo />
      <div className="flex flex-wrap gap-2"><LanguageSwitcher /><ThemeSwitcher /></div>
    </header>
  )

  if (order.isPending) return <FullPageLoader />
  if (!order.data) {
    return (
      <div className="min-h-svh bg-background">
        {header}
        <main className="mx-auto max-w-md p-4 pt-16 text-center">
          <LinkIcon className="mx-auto mb-3 size-10 text-muted-foreground" aria-hidden />
          <h1 className="text-xl font-semibold">{t('portal.notFoundTitle')}</h1>
          <p className="mt-2 text-sm text-muted-foreground">{t('portal.notFoundText')}</p>
        </main>
      </div>
    )
  }

  const o = order.data
  const money = (v: number) => formatMoney(v, o.currency)
  const current = STEPS.indexOf(o.status as (typeof STEPS)[number])
  const percent = o.totalAmount > 0 ? Math.min(100, (o.paidAmount / o.totalAmount) * 100) : 0

  return (
    <div className="min-h-svh bg-background">
      {header}
      <main className="cf-stagger mx-auto grid max-w-2xl gap-4 p-4 sm:p-6">
        <Card>
          <CardHeader>
            <CardDescription>{t('portal.from', { seller: o.sellerName })}</CardDescription>
            <CardTitle className="text-2xl"><h1>{t('portal.title', { number: o.orderNumber })}</h1></CardTitle>
            <p className="text-lg">{o.title}</p>
            <div className="flex flex-wrap items-center gap-2">
              <OrderStatusBadge status={o.status} />
              <PaymentStateBadge state={o.paymentState} />
              {o.deadline ? <span className="text-sm text-muted-foreground">{t('portal.deadline', { date: formatDate(o.deadline) })}</span> : null}
            </div>
          </CardHeader>
          <CardContent>
            <h2 className="mb-3 text-sm font-medium text-muted-foreground">{t('portal.progress')}</h2>
            {o.status === 'CANCELLED' ? (
              <p className="flex items-center gap-2 text-sm"><XCircle className="size-4 text-destructive" />{t('portal.cancelled')}</p>
            ) : (
              <ol className="grid grid-cols-4 gap-2">
                {STEPS.map((step, i) => (
                  <li key={step} className="grid justify-items-center gap-1 text-center text-xs">
                    <span className={cn('flex size-8 items-center justify-center rounded-full border-2',
                      i <= current ? 'border-primary bg-primary text-primary-foreground' : 'border-muted-foreground/30 text-muted-foreground')}
                          aria-hidden>
                      {i < current || o.status === 'COMPLETED' ? <Check className="size-4" /> : i + 1}
                    </span>
                    <span className={i === current ? 'font-semibold' : 'text-muted-foreground'}>{t(`orderStatus.${step}`)}</span>
                  </li>
                ))}
              </ol>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle>{t('portal.payments')}</CardTitle></CardHeader>
          <CardContent className="grid gap-3">
            <dl className="grid grid-cols-3 gap-2 text-sm">
              <div><dt className="text-muted-foreground">{t('payments.total')}</dt><dd className="font-medium">{money(o.totalAmount)}</dd></div>
              <div><dt className="text-muted-foreground">{t('payments.paid')}</dt><dd className="font-medium">{money(o.paidAmount)}</dd></div>
              <div><dt className="text-muted-foreground">{t('payments.balance')}</dt><dd className="font-semibold">{money(o.balance)}</dd></div>
            </dl>
            <div className="h-2 overflow-hidden rounded-full bg-muted" role="progressbar" aria-label={t('payments.paid')}
                 aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(percent)}>
              <div className="h-full bg-emerald-500" style={{ width: `${percent}%` }} />
            </div>
            {o.payments.length === 0 ? <p className="text-sm text-muted-foreground">{t('portal.noPayments')}</p> : (
              <ul className="grid gap-1 text-sm">
                {o.payments.map((p, i) => (
                  <li key={i} className="flex justify-between"><span>{formatDate(p.date)}</span><span className="tabular-nums">{money(p.amount)}</span></li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>

        {o.items.length > 0 ? (
          <Card>
            <CardHeader><CardTitle>{t('portal.items')}</CardTitle></CardHeader>
            <CardContent>
              <ul className="grid gap-2 text-sm">
                {o.items.map((item, i) => (
                  <li key={i} className="flex justify-between gap-3 border-b pb-2 last:border-0">
                    <span>{item.name} <span className="text-muted-foreground">× {item.quantity} {item.unit}</span></span>
                    <span className="tabular-nums">{money(item.lineTotal)}</span>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        ) : null}

        <p className="text-center text-xs text-muted-foreground">
          {t('portal.updated', { date: formatDateTime(o.updatedAt) })} · {t('portal.poweredBy')}
        </p>
      </main>
    </div>
  )
}
