import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ListPlus, Pencil, Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { catalogApi } from '@/features/catalog/api'
import { useWorkspace } from '@/features/workspace/workspace-context'
import { formatMoney } from '@/lib/format'
import { formatHundredths, multiplyHalfUp, toHundredths } from '@/lib/money'
import type { Order } from './api'
import { orderExtrasApi, type OrderItem } from './extras-api'

const CUSTOM = 'custom'

interface Row {
  key: number
  serviceId: string // CUSTOM for a custom line
  name: string
  unit: string
  quantity: string
  unitPrice: string
}

let nextKey = 1
const toRow = (item: OrderItem): Row => ({
  key: nextKey++,
  serviceId: item.serviceId ?? CUSTOM,
  name: item.name,
  unit: item.unit,
  quantity: String(item.quantity),
  unitPrice: String(item.unitPrice),
})

function ItemsEditor({ order, items, open, onOpenChange }: {
  order: Order
  items: OrderItem[]
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { t } = useTranslation()
  const { workspaceId } = useWorkspace()
  const queryClient = useQueryClient()
  const [rows, setRows] = useState<Row[]>(() => items.map(toRow))

  const services = useQuery({
    queryKey: ['ws', workspaceId, 'services', 'for-order', order.currency],
    queryFn: () => catalogApi.list(workspaceId, { status: 'ACTIVE', currency: order.currency }),
    enabled: open,
  })
  const save = useMutation({
    mutationFn: () => orderExtrasApi.replaceItems(workspaceId, order.id, order.version, rows.map((r) =>
      r.serviceId === CUSTOM
        ? { name: r.name, unit: r.unit, quantity: r.quantity.replace(',', '.'), unitPrice: r.unitPrice.replace(',', '.') }
        : { serviceId: r.serviceId, quantity: r.quantity.replace(',', '.') })),
    onSuccess: () => {
      toast.success(t('items.saved'))
      void queryClient.invalidateQueries({ queryKey: ['ws', workspaceId, 'orders'] })
      void queryClient.invalidateQueries({ queryKey: ['ws', workspaceId, 'payments', order.id] })
      onOpenChange(false)
    },
    onError: () => void queryClient.invalidateQueries({ queryKey: ['ws', workspaceId, 'orders'] }),
  })

  const update = (key: number, patch: Partial<Row>) =>
    setRows((current) => current.map((r) => (r.key === key ? { ...r, ...patch } : r)))

  // Preview only (exact integer arithmetic); the server calculates the real amounts
  const lineTotal = (r: Row) => {
    const qty = toHundredths(r.quantity)
    const price = toHundredths(r.unitPrice)
    return qty === null || price === null ? null : multiplyHalfUp(qty, price)
  }
  const total = rows.reduce<bigint | null>((sum, r) => {
    const line = lineTotal(r)
    return sum === null || line === null ? null : sum + line
  }, 0n)

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!next) save.reset(); onOpenChange(next) }}>
      <DialogContent className="max-h-[90svh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{t('items.editTitle')} · {order.orderNumber}</DialogTitle>
          <DialogDescription>{t('items.serverNote')}</DialogDescription>
        </DialogHeader>
        <ApiErrorAlert error={save.error} />
        <div className="grid gap-3">
          {rows.map((r, index) => {
            const custom = r.serviceId === CUSTOM
            const line = lineTotal(r)
            return (
              <div key={r.key} className="grid gap-2 rounded-md border p-3 sm:grid-cols-[1fr_5rem_7rem_7rem_auto] sm:items-end">
                <div className="grid gap-2">
                  <Select
                    value={r.serviceId}
                    onValueChange={(value) => {
                      const service = services.data?.items.find((s) => s.id === value)
                      update(r.key, service
                        ? { serviceId: value, name: service.name, unit: service.unit, unitPrice: String(service.price) }
                        : { serviceId: CUSTOM })
                    }}
                  >
                    <SelectTrigger className="w-full" aria-label={`${t('items.name')} ${index + 1}`}>
                      <SelectValue placeholder={t('items.selectService')} />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={CUSTOM}>{t('items.custom')}</SelectItem>
                      {services.data?.items.map((s) => (
                        <SelectItem key={s.id} value={s.id}>
                          {s.name} — {formatMoney(s.price, s.currency)} / {s.unit}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  {custom ? (
                    <div className="grid grid-cols-[1fr_5rem] gap-2">
                      <Input aria-label={`${t('items.name')} ${index + 1}`} placeholder={t('items.name')}
                             value={r.name} maxLength={200} onChange={(e) => update(r.key, { name: e.target.value })} />
                      <Input aria-label={`${t('items.unit')} ${index + 1}`} placeholder={t('items.unit')}
                             value={r.unit} maxLength={20} onChange={(e) => update(r.key, { unit: e.target.value })} />
                    </div>
                  ) : null}
                </div>
                <Input aria-label={`${t('items.qty')} ${index + 1}`} inputMode="decimal" value={r.quantity}
                       onChange={(e) => update(r.key, { quantity: e.target.value })} />
                <Input aria-label={`${t('items.unitPrice')} ${index + 1}`} inputMode="decimal" value={r.unitPrice}
                       readOnly={!custom} className={custom ? undefined : 'bg-muted'}
                       onChange={(e) => update(r.key, { unitPrice: e.target.value })} />
                <p className="text-right text-sm font-medium tabular-nums sm:pb-2">
                  {line === null ? '—' : formatMoney(formatHundredths(line), order.currency)}
                </p>
                <Button variant="ghost" size="icon-sm" aria-label={`${t('items.remove')} ${index + 1}`}
                        onClick={() => setRows((current) => current.filter((x) => x.key !== r.key))}>
                  <Trash2 />
                </Button>
              </div>
            )
          })}
          <Button variant="outline" className="justify-self-start" disabled={rows.length >= 100}
                  onClick={() => setRows((current) => [...current,
                    { key: nextKey++, serviceId: CUSTOM, name: '', unit: '', quantity: '1', unitPrice: '' }])}>
            <Plus />{t('items.add')}
          </Button>
          <p className="text-right text-lg font-semibold tabular-nums">
            {t('items.total')}: {total === null ? '—' : formatMoney(formatHundredths(total), order.currency)}
          </p>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
          <Button onClick={() => save.mutate()} disabled={save.isPending}>{t('common.save')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export function ItemsCard({ order }: { order: Order }) {
  const { t } = useTranslation()
  const { workspaceId } = useWorkspace()
  const [editing, setEditing] = useState(false)
  const items = useQuery({
    queryKey: ['ws', workspaceId, 'orders', 'items', order.id],
    queryFn: () => orderExtrasApi.items(workspaceId, order.id),
  })
  const list = items.data?.items ?? []

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('items.title')}</CardTitle>
        {order.canEdit ? (
          <CardAction>
            <Button size="sm" variant="outline" onClick={() => setEditing(true)}>
              {list.length > 0 ? <Pencil /> : <ListPlus />}
              {t('items.edit')}
            </Button>
          </CardAction>
        ) : null}
      </CardHeader>
      <CardContent>
        <ApiErrorAlert error={items.error} />
        {list.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('items.empty')}</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('items.name')}</TableHead>
                <TableHead className="text-right">{t('items.qty')}</TableHead>
                <TableHead className="hidden text-right sm:table-cell">{t('items.unitPrice')}</TableHead>
                <TableHead className="text-right">{t('items.lineTotal')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {list.map((item) => (
                <TableRow key={item.id}>
                  <TableCell className="whitespace-normal">{item.name}</TableCell>
                  <TableCell className="text-right tabular-nums">{item.quantity} {item.unit}</TableCell>
                  <TableCell className="hidden text-right tabular-nums sm:table-cell">
                    {formatMoney(item.unitPrice, order.currency)}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{formatMoney(item.lineTotal, order.currency)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
            <TableFooter>
              <TableRow>
                <TableCell colSpan={2} className="sm:hidden">{t('items.total')}</TableCell>
                <TableCell colSpan={3} className="hidden sm:table-cell">{t('items.total')}</TableCell>
                <TableCell className="text-right font-semibold tabular-nums">
                  {formatMoney(order.totalAmount, order.currency)}
                </TableCell>
              </TableRow>
            </TableFooter>
          </Table>
        )}
      </CardContent>
      {editing && items.data ? (
        <ItemsEditor order={order} items={items.data.items} open={editing} onOpenChange={setEditing} />
      ) : null}
    </Card>
  )
}
