import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Download, FileBarChart } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { EmptyState, ListSkeleton, PageHeader, PaginationBar, TableFrame, Page } from '@/components/page-parts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { clientsApi } from '@/features/clients/api'
import {
  reportsApi,
  type OrderReportFilters,
  type PaymentReportFilters,
} from '@/features/dashboard/api'
import { ORDER_STATUSES, type OrderStatus } from '@/features/orders/api'
import { OrderStatusBadge } from '@/features/orders/order-status-badge'
import { PAYMENT_METHODS, type PaymentMethod, type PaymentState } from '@/features/payments/api'
import { PaymentStateBadge } from '@/features/payments/payment-state-badge'
import { useWorkspace } from '@/features/workspace/workspace-context'
import { API_URL } from '@/lib/api'
import { formatDate, formatMoney, todayIso } from '@/lib/format'

const ALL = 'all'

function firstDayOfMonth() {
  return `${todayIso().slice(0, 8)}01`
}

/** Period + client filters shared by both reports. */
function CommonFilters({ from, to, clientId, onChange }: {
  from: string
  to: string
  clientId: string
  onChange: (patch: { from?: string; to?: string; clientId?: string }) => void
}) {
  const { t } = useTranslation()
  const { workspaceId } = useWorkspace()
  const clients = useQuery({
    queryKey: ['ws', workspaceId, 'clients', 'list', 'for-reports'],
    queryFn: () => clientsApi.list(workspaceId, { status: 'ALL', sort: 'NAME', direction: 'ASC', size: 100 }),
  })
  return (
    <>
      <div className="grid gap-1">
        <Label htmlFor="report-from">{t('reports.from')}</Label>
        <Input id="report-from" type="date" className="w-40" value={from} max={to}
               onChange={(e) => e.target.value && onChange({ from: e.target.value })} />
      </div>
      <div className="grid gap-1">
        <Label htmlFor="report-to">{t('reports.to')}</Label>
        <Input id="report-to" type="date" className="w-40" value={to} min={from}
               onChange={(e) => e.target.value && onChange({ to: e.target.value })} />
      </div>
      <div className="grid gap-1">
        <Label htmlFor="report-client">{t('reports.client')}</Label>
        <Select value={clientId} onValueChange={(v) => onChange({ clientId: v })}>
          <SelectTrigger id="report-client" className="w-52">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>{t('reports.anyClient')}</SelectItem>
            {clients.data?.items.map((c) => (
              <SelectItem key={c.id} value={c.id}>
                {c.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
    </>
  )
}

function ExportButton({ href }: { href: string }) {
  const { t } = useTranslation()
  return (
    <Button asChild variant="outline" className="self-end">
      <a href={href} download>
        <Download />
        {t('reports.export')}
      </a>
    </Button>
  )
}

function OrdersReport() {
  const { t } = useTranslation()
  const { workspaceId } = useWorkspace()
  const [from, setFrom] = useState(firstDayOfMonth)
  const [to, setTo] = useState(todayIso)
  const [clientId, setClientId] = useState(ALL)
  const [status, setStatus] = useState(ALL)
  const [paymentState, setPaymentState] = useState(ALL)
  const [page, setPage] = useState(0)

  const filters: OrderReportFilters = {
    from,
    to,
    clientId: clientId === ALL ? undefined : clientId,
    status: status === ALL ? undefined : (status as OrderStatus),
    paymentState: paymentState === ALL ? undefined : (paymentState as PaymentState),
  }
  const report = useQuery({
    queryKey: ['ws', workspaceId, 'reports', 'orders', filters, page],
    queryFn: () => reportsApi.orders(workspaceId, filters, page),
    placeholderData: keepPreviousData,
  })
  const reset = <T,>(set: (v: T) => void) => (v: T) => {
    set(v)
    setPage(0)
  }

  return (
    <>
      <div className="mb-4 flex flex-wrap gap-3">
        <CommonFilters
          from={from}
          to={to}
          clientId={clientId}
          onChange={(p) => {
            if (p.from) setFrom(p.from)
            if (p.to) setTo(p.to)
            if (p.clientId) setClientId(p.clientId)
            setPage(0)
          }}
        />
        <div className="grid gap-1">
          <Label htmlFor="report-status">{t('orders.status')}</Label>
          <Select value={status} onValueChange={reset(setStatus)}>
            <SelectTrigger id="report-status" className="w-40">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>{t('orders.anyStatus')}</SelectItem>
              {ORDER_STATUSES.map((s) => (
                <SelectItem key={s} value={s}>{t(`orderStatus.${s}`)}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="grid gap-1">
          <Label htmlFor="report-payment">{t('payments.title')}</Label>
          <Select value={paymentState} onValueChange={reset(setPaymentState)}>
            <SelectTrigger id="report-payment" className="w-48">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>{t('payments.anyState')}</SelectItem>
              {(['UNPAID', 'PARTIALLY_PAID', 'PAID'] as const).map((s) => (
                <SelectItem key={s} value={s}>{t(`paymentState.${s}`)}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <ExportButton href={reportsApi.csvUrl(API_URL, workspaceId, 'orders', filters)} />
      </div>

      <ApiErrorAlert error={report.error} />
      {report.isPending ? <ListSkeleton /> : null}
      {report.data ? (
        <>
          <p className="mb-3 text-sm text-muted-foreground">{t('reports.ordersHint')}</p>
          <div className="mb-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {report.data.summary.map((s) => (
              <Card key={s.currency}>
                <CardHeader>
                  <CardDescription>
                    {t('reports.summary')} · {s.currency} · {t('dashboard.ordersCount', { count: s.count })}
                  </CardDescription>
                  <CardTitle className="text-2xl">{formatMoney(s.totalAmount, s.currency)}</CardTitle>
                </CardHeader>
                <CardContent className="grid grid-cols-2 gap-2 text-sm">
                  <div>
                    <p className="text-muted-foreground">{t('payments.paid')}</p>
                    <p className="font-medium">{formatMoney(s.paidAmount, s.currency)}</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground">{t('payments.balance')}</p>
                    <p className="font-medium">{formatMoney(s.balance, s.currency)}</p>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
          {report.data.totalItems === 0 ? (
            <EmptyState icon={FileBarChart} title={t('reports.empty')} />
          ) : (
            <>
              <TableFrame>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t('orders.number')}</TableHead>
                      <TableHead className="hidden sm:table-cell">{t('reports.created')}</TableHead>
                      <TableHead>{t('orders.client')}</TableHead>
                      <TableHead>{t('orders.status')}</TableHead>
                      <TableHead className="text-right">{t('orders.amount')}</TableHead>
                      <TableHead className="hidden text-right md:table-cell">{t('payments.paid')}</TableHead>
                      <TableHead className="text-right">{t('payments.balance')}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {report.data.items.map((r) => (
                      <TableRow key={r.id}>
                        <TableCell className="font-mono text-xs">
                          <Link to={`/orders/${r.id}`} className="hover:underline">{r.orderNumber}</Link>
                        </TableCell>
                        <TableCell className="hidden sm:table-cell">{formatDate(r.createdDate)}</TableCell>
                        <TableCell>{r.clientName}</TableCell>
                        <TableCell>
                          <div className="flex flex-wrap gap-1">
                            <OrderStatusBadge status={r.status} />
                            <PaymentStateBadge state={r.paymentState} />
                          </div>
                        </TableCell>
                        <TableCell className="text-right tabular-nums">{formatMoney(r.totalAmount, r.currency)}</TableCell>
                        <TableCell className="hidden text-right tabular-nums md:table-cell">
                          {formatMoney(r.paidAmount, r.currency)}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          {r.status === 'CANCELLED' ? '—' : formatMoney(r.balance, r.currency)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableFrame>
              <PaginationBar page={page} totalPages={report.data.totalPages} onChange={setPage} />
            </>
          )}
        </>
      ) : null}
    </>
  )
}

function PaymentsReport() {
  const { t } = useTranslation()
  const { workspaceId } = useWorkspace()
  const [from, setFrom] = useState(firstDayOfMonth)
  const [to, setTo] = useState(todayIso)
  const [clientId, setClientId] = useState(ALL)
  const [method, setMethod] = useState(ALL)
  const [page, setPage] = useState(0)

  const filters: PaymentReportFilters = {
    from,
    to,
    clientId: clientId === ALL ? undefined : clientId,
    method: method === ALL ? undefined : (method as PaymentMethod),
  }
  const report = useQuery({
    queryKey: ['ws', workspaceId, 'reports', 'payments', filters, page],
    queryFn: () => reportsApi.payments(workspaceId, filters, page),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <div className="mb-4 flex flex-wrap gap-3">
        <CommonFilters
          from={from}
          to={to}
          clientId={clientId}
          onChange={(p) => {
            if (p.from) setFrom(p.from)
            if (p.to) setTo(p.to)
            if (p.clientId) setClientId(p.clientId)
            setPage(0)
          }}
        />
        <div className="grid gap-1">
          <Label htmlFor="report-method">{t('payments.method')}</Label>
          <Select value={method} onValueChange={(v) => { setMethod(v); setPage(0) }}>
            <SelectTrigger id="report-method" className="w-44">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>{t('reports.anyMethod')}</SelectItem>
              {PAYMENT_METHODS.map((m) => (
                <SelectItem key={m} value={m}>{t(`paymentMethod.${m}`)}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <ExportButton href={reportsApi.csvUrl(API_URL, workspaceId, 'payments', filters)} />
      </div>

      <ApiErrorAlert error={report.error} />
      {report.isPending ? <ListSkeleton /> : null}
      {report.data ? (
        <>
          <p className="mb-3 text-sm text-muted-foreground">
            {t('reports.paymentsHint')}. {t('reports.voidedNote')}
          </p>
          <div className="mb-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {report.data.summary.map((s) => (
              <Card key={s.currency}>
                <CardHeader>
                  <CardDescription>
                    {t('reports.summary')} · {t('dashboard.paymentsCount', { count: s.count })}
                  </CardDescription>
                  <CardTitle className="text-2xl">{formatMoney(s.amount, s.currency)}</CardTitle>
                </CardHeader>
              </Card>
            ))}
          </div>
          {report.data.totalItems === 0 ? (
            <EmptyState icon={FileBarChart} title={t('reports.empty')} />
          ) : (
            <>
              <TableFrame>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t('payments.date')}</TableHead>
                      <TableHead>{t('orders.number')}</TableHead>
                      <TableHead className="hidden sm:table-cell">{t('orders.client')}</TableHead>
                      <TableHead className="text-right">{t('payments.amount')}</TableHead>
                      <TableHead className="hidden md:table-cell">{t('payments.method')}</TableHead>
                      <TableHead className="hidden lg:table-cell">{t('payments.reference')}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {report.data.items.map((r) => (
                      <TableRow key={r.id} className={r.voided ? 'text-muted-foreground' : undefined}>
                        <TableCell>{formatDate(r.paymentDate)}</TableCell>
                        <TableCell className="font-mono text-xs">
                          <Link to={`/orders/${r.orderId}`} className="hover:underline">{r.orderNumber}</Link>
                        </TableCell>
                        <TableCell className="hidden sm:table-cell">{r.clientName}</TableCell>
                        <TableCell className="text-right tabular-nums">
                          <span className={r.voided ? 'line-through' : undefined}>{formatMoney(r.amount, r.currency)}</span>
                          {r.voided ? <Badge variant="outline" className="ml-2">{t('payments.voided')}</Badge> : null}
                        </TableCell>
                        <TableCell className="hidden md:table-cell">{t(`paymentMethod.${r.method}`)}</TableCell>
                        <TableCell className="hidden max-w-48 truncate lg:table-cell">{r.reference ?? '—'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableFrame>
              <PaginationBar page={page} totalPages={report.data.totalPages} onChange={setPage} />
            </>
          )}
        </>
      ) : null}
    </>
  )
}

export function ReportsPage() {
  const { t } = useTranslation()
  return (
    <Page>
      <PageHeader title={t('reports.title')} />
      <Tabs defaultValue="orders">
        <TabsList className="mb-4">
          <TabsTrigger value="orders">{t('reports.orders')}</TabsTrigger>
          <TabsTrigger value="payments">{t('reports.payments')}</TabsTrigger>
        </TabsList>
        <TabsContent value="orders">
          <OrdersReport />
        </TabsContent>
        <TabsContent value="payments">
          <PaymentsReport />
        </TabsContent>
      </Tabs>
    </Page>
  )
}
