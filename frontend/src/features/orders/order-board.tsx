import { useQuery } from '@tanstack/react-query'
import { MoreHorizontal } from 'lucide-react'
import { useState, type DragEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { toast } from 'sonner'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { ListSkeleton } from '@/components/page-parts'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useWorkspace } from '@/features/workspace/workspace-context'
import { formatDate, formatMoney } from '@/lib/format'
import { cn } from '@/lib/utils'
import { ordersApi, type Order, type OrderStatus } from './api'
import { PaymentStateBadge } from '@/features/payments/payment-state-badge'
import { OverdueBadge, StatusDot } from './order-status-badge'
import { useOrderActions } from './use-order-actions'

/**
 * Kanban board. Cards can be dragged to another column (desktop) or moved with
 * the "Move to" menu (keyboard, touch). Only transitions the backend reports as
 * allowed for the current user are offered; the backend validates them again.
 */
export function OrderBoard({ search }: { search: string }) {
  const { t } = useTranslation()
  const { workspaceId } = useWorkspace()
  const { changeStatus } = useOrderActions()
  const [dragged, setDragged] = useState<Order | null>(null)
  const [dropTarget, setDropTarget] = useState<OrderStatus | null>(null)

  const board = useQuery({
    queryKey: ['ws', workspaceId, 'orders', 'board', search],
    queryFn: () => ordersApi.board(workspaceId, search),
  })

  if (board.isPending) return <ListSkeleton rows={4} />
  if (!board.data) return <ApiErrorAlert error={board.error} />

  const move = (order: Order, status: OrderStatus) => {
    if (!order.allowedTransitions.includes(status)) {
      toast.error(t('orders.dropNotAllowed'))
      return
    }
    changeStatus.mutate({ order, status })
  }

  const onDrop = (status: OrderStatus) => (e: DragEvent) => {
    e.preventDefault()
    setDropTarget(null)
    if (dragged && dragged.status !== status) move(dragged, status)
    setDragged(null)
  }

  return (
    <div className="flex gap-4 overflow-x-auto pb-4">
      {board.data.map((column) => {
        const canDrop = dragged?.allowedTransitions.includes(column.status) ?? false
        return (
          <section
            key={column.status}
            aria-label={t(`orderStatus.${column.status}`)}
            className={cn(
              'flex w-72 shrink-0 flex-col rounded-xl bg-muted/60 p-2 ring-1 ring-foreground/5 transition-colors',
              dropTarget === column.status && canDrop && 'border-primary bg-primary/5',
            )}
            onDragOver={(e) => {
              if (canDrop) {
                e.preventDefault()
                setDropTarget(column.status)
              }
            }}
            onDragLeave={() => setDropTarget(null)}
            onDrop={onDrop(column.status)}
          >
            <h2 className="flex items-center justify-between px-2 py-1 text-sm font-semibold">
              <span className="flex items-center gap-2"><StatusDot status={column.status} />{t(`orderStatus.${column.status}`)}</span>
              <span className="rounded-full bg-card px-2 py-0.5 text-xs text-muted-foreground tabular-nums ring-1 ring-foreground/10">{column.total}</span>
            </h2>
            <ul className="cf-stagger mt-2 grid gap-2">
              {column.items.length === 0 ? (
                <li className="px-2 py-4 text-center text-xs text-muted-foreground">{t('orders.boardEmpty')}</li>
              ) : null}
              {column.items.map((order) => (
                <li
                  key={order.id}
                  draggable={order.allowedTransitions.length > 0}
                  onDragStart={() => setDragged(order)}
                  onDragEnd={() => setDragged(null)}
                  className={cn(
                    'cf-lift min-w-0 overflow-hidden rounded-lg border bg-card p-3 shadow-[var(--shadow-card)]',
                    order.allowedTransitions.length > 0 && 'cursor-grab active:cursor-grabbing',
                  )}
                >
                  <div className="flex items-start justify-between gap-2">
                    <Link to={`/orders/${order.id}`} className="min-w-0 hover:underline">
                      <span className="block font-mono text-xs text-muted-foreground">{order.orderNumber}</span>
                      <span className="line-clamp-2 break-words font-medium">{order.title}</span>
                    </Link>
                    {order.allowedTransitions.length > 0 ? (
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon-sm" aria-label={`${t('orders.moveTo')}: ${order.orderNumber}`}>
                            <MoreHorizontal />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuLabel>{t('orders.moveTo')}</DropdownMenuLabel>
                          {order.allowedTransitions.map((status) => (
                            <DropdownMenuItem key={status} onSelect={() => move(order, status)}>
                              {t(`orderStatus.${status}`)}
                            </DropdownMenuItem>
                          ))}
                        </DropdownMenuContent>
                      </DropdownMenu>
                    ) : null}
                  </div>
                  <p className="mt-1 truncate text-sm text-muted-foreground" title={order.client.name}>{order.client.name}</p>
                  <div className="mt-2">
                    <PaymentStateBadge state={order.paymentState} />
                  </div>
                  <div className="mt-2 flex flex-wrap items-center justify-between gap-1 text-sm">
                    <span className="tabular-nums">{formatMoney(order.totalAmount, order.currency)}</span>
                    {order.overdue ? (
                      <OverdueBadge />
                    ) : order.deadline ? (
                      <span className="text-xs text-muted-foreground">{formatDate(order.deadline)}</span>
                    ) : null}
                  </div>
                </li>
              ))}
            </ul>
            {column.total > column.items.length ? (
              <p className="mt-2 px-2 text-xs text-muted-foreground">
                {t('orders.boardMore', { count: column.total - column.items.length })}
              </p>
            ) : null}
          </section>
        )
      })}
    </div>
  )
}
