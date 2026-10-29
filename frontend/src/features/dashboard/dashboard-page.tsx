import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { AlertTriangle, ArrowUpRight, CheckCircle2, Clock, Package, Users } from 'lucide-react'
import { useState, type CSSProperties, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { ActivityList } from '@/components/activity-list'
import { ApiErrorAlert } from '@/components/api-error-alert'
import { ListSkeleton, Page, PageHeader } from '@/components/page-parts'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { OrderStatusBadge } from '@/features/orders/order-status-badge'
import { useWorkspace } from '@/features/workspace/workspace-context'
import { formatDate, formatMoney, todayIso } from '@/lib/format'
import { cn } from '@/lib/utils'
import { reportsApi, type CurrencyAmount } from './api'
import { RevenueChart } from './revenue-chart'
import { StatusBarChart } from './status-bar-chart'

type Period = 'thisMonth' | 'last30' | 'thisYear'

function periodRange(period: Period): { from: string; to: string } {
  const to = todayIso()
  const now = new Date()
  const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  if (period === 'thisMonth') return { from: iso(new Date(now.getFullYear(), now.getMonth(), 1)), to }
  if (period === 'thisYear') return { from: iso(new Date(now.getFullYear(), 0, 1)), to }
  return { from: iso(new Date(now.getFullYear(), now.getMonth(), now.getDate() - 29)), to }
}

const delay = (i: number) => ({ '--cf-i': i }) as CSSProperties

function StatTile({ label, value, icon: Icon, to, tone, index }: {
  label: string
  value: number
  icon: typeof Users
  to?: string
  tone?: 'warning'
  index: number
}) {
  const warn = tone === 'warning' && value > 0
  const content = (
    <Card
      className={cn('cf-lift h-full', to && 'group cursor-pointer')}
      style={delay(index)}
    >
      <CardHeader>
        <CardDescription className="flex items-center gap-2">
          <span className={cn('flex size-7 items-center justify-center rounded-md', warn ? 'bg-destructive/10 text-destructive' : 'bg-accent text-accent-foreground')}>
            <Icon className="size-4" aria-hidden />
          </span>
          <span className="min-w-0 truncate">{label}</span>
          {to ? <ArrowUpRight className="ml-auto size-4 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100" aria-hidden /> : null}
        </CardDescription>
        <CardTitle className={cn('cf-num mt-1 text-[2rem] leading-none font-semibold', warn && 'text-destructive')}>
          {value}
        </CardTitle>
      </CardHeader>
    </Card>
  )
  return to ? <Link to={to} className="block rounded-xl outline-none focus-visible:ring-2 focus-visible:ring-ring">{content}</Link> : content
}

/** Money tile: one line per currency (amounts in different currencies are never added up). */
function MoneyTile({ title, items, countKey, action, index }: {
  title: string
  items: CurrencyAmount[]
  countKey: string
  action?: ReactNode
  index: number
}) {
  const { t } = useTranslation()
  return (
    <Card style={delay(index)}>
      <CardHeader>
        <CardDescription>{title}</CardDescription>
        {action}
      </CardHeader>
      <CardContent>
        {items.length === 0 ? (
          <p className="text-2xl font-semibold text-muted-foreground">{t('dashboard.nothing')}</p>
        ) : (
          <ul className="grid gap-2">
            {items.map((item) => (
              <li key={item.currency} className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
                <span className="cf-num text-2xl font-semibold tracking-tight">{formatMoney(item.amount, item.currency)}</span>
                <span className="text-sm text-muted-foreground">{t(countKey, { count: item.count })}</span>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

export function DashboardPage() {
  const { t } = useTranslation()
  const { workspaceId } = useWorkspace()
  const [period, setPeriod] = useState<Period>('thisMonth')
  const range = periodRange(period)

  const dashboard = useQuery({
    queryKey: ['ws', workspaceId, 'dashboard', range],
    queryFn: () => reportsApi.dashboard(workspaceId, range.from, range.to),
    placeholderData: keepPreviousData,
  })

  if (dashboard.isPending) return <ListSkeleton rows={8} />
  if (!dashboard.data) return <ApiErrorAlert error={dashboard.error} />
  const d = dashboard.data

  return (
    <Page>
      <PageHeader
        title={t('dashboard.title')}
        description={d.onlyAssignedOrders ? t('dashboard.onlyAssigned') : undefined}
      />

      <div className="cf-stagger grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatTile index={0} label={t('dashboard.clients')} value={d.totalClients} icon={Users} to="/clients" />
        <StatTile index={1} label={t('dashboard.activeOrders')} value={d.activeOrders} icon={Package} to="/orders" />
        <StatTile index={2} label={t('dashboard.completedOrders')} value={d.completedOrders} icon={CheckCircle2} />
        <StatTile index={3} label={t('dashboard.overdueOrders')} value={d.overdueOrders} icon={AlertTriangle} tone="warning" to="/orders" />
      </div>

      <div className="cf-stagger mt-4 grid gap-4 lg:grid-cols-2">
        <MoneyTile
          index={4}
          title={t('dashboard.paymentsReceived')}
          items={d.paymentsReceived}
          countKey="dashboard.paymentsCount"
          action={
            <Select value={period} onValueChange={(v) => setPeriod(v as Period)}>
              <SelectTrigger size="sm" className="w-44 justify-self-start" aria-label={t('dashboard.period')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="thisMonth">{t('dashboard.thisMonth')}</SelectItem>
                <SelectItem value="last30">{t('dashboard.last30')}</SelectItem>
                <SelectItem value="thisYear">{t('dashboard.thisYear')}</SelectItem>
              </SelectContent>
            </Select>
          }
        />
        <MoneyTile index={5} title={t('dashboard.outstanding')} items={d.outstandingBalances} countKey="dashboard.ordersCount" />
      </div>

      <div className="cf-stagger mt-4" style={delay(6)}>
        <RevenueChart />
      </div>

      <div className="cf-stagger mt-4 grid gap-4 lg:grid-cols-2">
        <Card style={delay(7)}>
          <CardHeader>
            <CardTitle>{t('dashboard.byStatus')}</CardTitle>
            <CardDescription>{t('dashboard.byStatusHint')}</CardDescription>
          </CardHeader>
          <CardContent>
            <StatusBarChart counts={d.ordersByStatus} />
          </CardContent>
        </Card>

        <Card style={delay(8)}>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Clock className="size-4 text-muted-foreground" aria-hidden />
              {t('dashboard.upcoming')}
            </CardTitle>
            <CardDescription>{t('dashboard.upcomingHint')}</CardDescription>
          </CardHeader>
          <CardContent>
            {d.upcomingDeadlines.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t('dashboard.noUpcoming')}</p>
            ) : (
              <ul className="cf-stagger grid gap-2">
                {d.upcomingDeadlines.map((o, i) => (
                  <li key={o.id} style={delay(i)}>
                    <Link
                      to={`/orders/${o.id}`}
                      className="flex min-w-0 flex-wrap items-center gap-2 rounded-lg bg-muted/50 px-3 py-2 text-sm ring-1 ring-foreground/5 transition-[background-color,transform] duration-150 hover:bg-accent hover:text-accent-foreground active:scale-[0.99]"
                    >
                      <span className="font-mono text-xs text-muted-foreground" translate="no">{o.orderNumber}</span>
                      <span className="min-w-0 flex-1 truncate font-medium">{o.title}</span>
                      <OrderStatusBadge status={o.status} />
                      <span className="tabular-nums">{formatDate(o.deadline)}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>

      {d.recentActivity ? (
        <Card className="cf-stagger mt-4" style={delay(9)}>
          <CardHeader>
            <CardTitle>{t('dashboard.recent')}</CardTitle>
          </CardHeader>
          <CardContent>
            <ActivityList items={d.recentActivity} />
          </CardContent>
        </Card>
      ) : null}
    </Page>
  )
}
