import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Package } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { EmptyState, ListSkeleton, PaginationBar, TableFrame } from '@/components/page-parts'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useWorkspace } from '@/features/workspace/workspace-context'
import { formatDate, formatMoney } from '@/lib/format'
import { cn } from '@/lib/utils'
import { PaymentStateBadge } from '@/features/payments/payment-state-badge'
import { ordersApi, type OrderListParams } from './api'
import { OrderStatusBadge, OverdueBadge } from './order-status-badge'

interface Props {
  filters: Omit<OrderListParams, 'page' | 'size'>
  hideClient?: boolean
}

/** Paged order table; used on the orders page and on client pages. */
export function OrdersTable({ filters, hideClient }: Props) {
  const { t } = useTranslation()
  const { workspaceId } = useWorkspace()
  const [page, setPage] = useState(0)
  const [lastFilters, setLastFilters] = useState(filters)

  // Go back to the first page whenever a filter changes
  if (JSON.stringify(lastFilters) !== JSON.stringify(filters)) {
    setLastFilters(filters)
    setPage(0)
  }

  const params = { ...filters, page, size: 20 }
  const orders = useQuery({
    queryKey: ['ws', workspaceId, 'orders', 'list', params],
    queryFn: () => ordersApi.list(workspaceId, params),
    placeholderData: keepPreviousData,
  })

  if (orders.isPending) return <ListSkeleton />
  if (!orders.data) return <ApiErrorAlert error={orders.error} />
  if (orders.data.totalItems === 0) {
    return <EmptyState icon={Package} title={t('common.noResults')} />
  }

  return (
    <>
      <TableFrame>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('orders.number')}</TableHead>
              <TableHead>{t('orders.orderTitle')}</TableHead>
              {hideClient ? null : <TableHead className="hidden md:table-cell">{t('orders.client')}</TableHead>}
              <TableHead className="text-right">{t('orders.amount')}</TableHead>
              <TableHead>{t('orders.status')}</TableHead>
              <TableHead className="hidden md:table-cell">{t('payments.title')}</TableHead>
              <TableHead className="hidden sm:table-cell">{t('orders.deadline')}</TableHead>
              <TableHead className="hidden lg:table-cell">{t('orders.assignee')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {orders.data.items.map((o) => (
              <TableRow key={o.id} className={cn(o.archived && 'opacity-60')}>
                <TableCell className="font-mono text-xs whitespace-nowrap" translate="no">
                  <Link to={`/orders/${o.id}`} className="hover:underline">
                    {o.orderNumber}
                  </Link>
                </TableCell>
                <TableCell className="min-w-40 font-medium">
                  <Link to={`/orders/${o.id}`} className="hover:underline">
                    {o.title}
                  </Link>
                </TableCell>
                {hideClient ? null : (
                  <TableCell className="hidden md:table-cell">
                    <Link to={`/clients/${o.client.id}`} className="hover:underline">
                      {o.client.name}
                    </Link>
                  </TableCell>
                )}
                <TableCell className="text-right whitespace-nowrap tabular-nums">{formatMoney(o.totalAmount, o.currency)}</TableCell>
                <TableCell className="whitespace-nowrap">
                  <div className="flex flex-wrap gap-1">
                    <OrderStatusBadge status={o.status} />
                    {o.archived ? <Badge variant="outline">{t('common.archived')}</Badge> : null}
                  </div>
                </TableCell>
                <TableCell className="hidden whitespace-nowrap md:table-cell">
                  <PaymentStateBadge state={o.paymentState} />
                  {o.paymentState === 'PARTIALLY_PAID' ? (
                    <span className="mt-1 block text-xs text-muted-foreground tabular-nums">
                      {t('payments.balance')}: {formatMoney(o.balance, o.currency)}
                    </span>
                  ) : null}
                </TableCell>
                <TableCell className="hidden whitespace-nowrap sm:table-cell">
                  {o.deadline ? (
                    <span className={cn(o.overdue && 'font-medium text-destructive')}>{formatDate(o.deadline)}</span>
                  ) : (
                    <span className="text-muted-foreground">—</span>
                  )}
                  {o.overdue ? <span className="ml-2"><OverdueBadge /></span> : null}
                </TableCell>
                <TableCell className="hidden lg:table-cell">
                  {o.assignedUser?.name ?? <span className="text-muted-foreground">{t('orders.unassigned')}</span>}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableFrame>
      <PaginationBar page={page} totalPages={orders.data.totalPages} onChange={setPage} />
    </>
  )
}
