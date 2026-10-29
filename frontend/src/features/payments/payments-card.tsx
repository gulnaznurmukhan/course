import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Ban, Plus } from 'lucide-react'
import { useRef, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { z } from 'zod'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { FormField } from '@/components/form-field'
import { ListSkeleton } from '@/components/page-parts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { useWorkspace } from '@/features/workspace/workspace-context'
import { ApiError } from '@/lib/api'
import { formatDate, formatMoney, todayIso } from '@/lib/format'
import { PAYMENT_METHODS, paymentsApi, type OrderPayments, type Payment } from './api'
import { PaymentStateBadge } from './payment-state-badge'

const paymentSchema = z.object({
  amount: z
    .string()
    .trim()
    .transform((v) => v.replace(',', '.').replace(/\s/g, ''))
    .pipe(z.string().regex(/^\d{1,12}(\.\d{1,2})?$/, 'validation.amount').refine((v) => Number(v) > 0, 'validation.amount')),
  paymentMethod: z.enum(PAYMENT_METHODS),
  paymentDate: z.string().min(1, 'validation.required'),
  reference: z.string().trim().max(100, 'validation.tooLong'),
})
type PaymentFormInput = z.input<typeof paymentSchema>
type PaymentValues = z.output<typeof paymentSchema>

function useRefreshPayments(orderId: string) {
  const { workspaceId } = useWorkspace()
  const queryClient = useQueryClient()
  return () => {
    void queryClient.invalidateQueries({ queryKey: ['ws', workspaceId, 'payments', orderId] })
    void queryClient.invalidateQueries({ queryKey: ['ws', workspaceId, 'orders'] })
  }
}

function RecordPaymentDialog({ orderId, data, open, onOpenChange }: {
  orderId: string
  data: OrderPayments
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { t } = useTranslation()
  const { workspaceId } = useWorkspace()
  const refresh = useRefreshPayments(orderId)
  // One key per submission. It is kept when the same form is submitted again
  // (double click, retry after a network error) and replaced after success.
  const idempotencyKey = useRef(crypto.randomUUID())

  const form = useForm<PaymentFormInput, unknown, PaymentValues>({
    resolver: zodResolver(paymentSchema),
    values: { amount: String(data.balance), paymentMethod: 'CASH', paymentDate: todayIso(), reference: '' },
  })
  const { errors, isSubmitting } = form.formState

  const record = useMutation({
    mutationFn: (v: PaymentValues) =>
      paymentsApi.record(workspaceId, orderId, { ...v, reference: v.reference || undefined }, idempotencyKey.current),
    onSuccess: () => {
      idempotencyKey.current = crypto.randomUUID()
      toast.success(t('payments.recorded'))
      refresh()
      onOpenChange(false)
    },
    onError: (error) => {
      // A business rejection means this submission is finished: the next attempt is a new one
      if (error instanceof ApiError && error.status !== 0) idempotencyKey.current = crypto.randomUUID()
      refresh()
    },
  })

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) record.reset()
        onOpenChange(next)
      }}
    >
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('payments.recordTitle')}</DialogTitle>
          <DialogDescription>
            {t('payments.maxAmount', { amount: formatMoney(data.balance, data.currency) })}
          </DialogDescription>
        </DialogHeader>
        <form
          noValidate
          className="grid gap-4"
          onSubmit={form.handleSubmit(async (v) => {
            await record.mutateAsync(v).catch(() => undefined)
          })}
        >
          <ApiErrorAlert error={record.error} />
          <FormField
            label={`${t('payments.amount')}, ${data.currency}`}
            inputMode="decimal"
            autoComplete="off"
            error={errors.amount?.message && t(errors.amount.message)}
            {...form.register('amount')}
          />
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="grid gap-2">
              <Label htmlFor="payment-method">{t('payments.method')}</Label>
              <Controller
                control={form.control}
                name="paymentMethod"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="payment-method" className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {PAYMENT_METHODS.map((m) => (
                        <SelectItem key={m} value={m}>
                          {t(`paymentMethod.${m}`)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
            <FormField
              label={t('payments.date')}
              type="date"
              max={todayIso()}
              error={errors.paymentDate?.message && t(errors.paymentDate.message)}
              {...form.register('paymentDate')}
            />
          </div>
          <FormField
            label={`${t('payments.reference')} (${t('common.optional')})`}
            hint={t('payments.referenceHint')}
            error={errors.reference?.message && t(errors.reference.message)}
            {...form.register('reference')}
          />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {t('payments.record')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function VoidPaymentDialog({ orderId, payment, currency, onClose }: {
  orderId: string
  payment: Payment | null
  currency: string
  onClose: () => void
}) {
  const { t } = useTranslation()
  const { workspaceId } = useWorkspace()
  const refresh = useRefreshPayments(orderId)
  const [reason, setReason] = useState('')

  const voidPayment = useMutation({
    mutationFn: () => paymentsApi.void(workspaceId, orderId, payment!.id, reason.trim()),
    onSuccess: () => {
      toast.success(t('payments.voidedToast'))
      refresh()
      setReason('')
      onClose()
    },
  })

  return (
    <Dialog
      open={payment !== null}
      onOpenChange={(next) => {
        if (!next) {
          voidPayment.reset()
          setReason('')
          onClose()
        }
      }}
    >
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>
            {t('payments.voidTitle')} {payment ? formatMoney(payment.amount, currency) : ''}
          </DialogTitle>
          <DialogDescription>{t('payments.voidText')}</DialogDescription>
        </DialogHeader>
        <form
          className="grid gap-4"
          onSubmit={(e) => {
            e.preventDefault()
            if (reason.trim()) voidPayment.mutate()
          }}
        >
          <ApiErrorAlert error={voidPayment.error} />
          <div className="grid gap-2">
            <Label htmlFor="void-reason">{t('payments.voidReason')}</Label>
            <Textarea
              id="void-reason"
              rows={2}
              maxLength={300}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" variant="destructive" disabled={!reason.trim() || voidPayment.isPending}>
              {t('payments.void')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

/** Payment summary and history of one order. */
export function PaymentsCard({ orderId }: { orderId: string }) {
  const { t } = useTranslation()
  const { workspaceId } = useWorkspace()
  const [recording, setRecording] = useState(false)
  const [voiding, setVoiding] = useState<Payment | null>(null)

  const query = useQuery({
    queryKey: ['ws', workspaceId, 'payments', orderId],
    queryFn: () => paymentsApi.list(workspaceId, orderId),
  })

  if (query.isPending) return <ListSkeleton rows={3} />
  if (!query.data) return <ApiErrorAlert error={query.error} />
  const data = query.data
  const percent = data.totalAmount > 0 ? Math.min(100, (data.paidAmount / data.totalAmount) * 100) : 0

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          {t('payments.title')}
          <PaymentStateBadge state={data.paymentState} />
        </CardTitle>
        {data.canRecord && data.balance > 0 ? (
          <CardAction>
            <Button size="sm" onClick={() => setRecording(true)}>
              <Plus />
              {t('payments.record')}
            </Button>
          </CardAction>
        ) : null}
      </CardHeader>
      <CardContent className="grid gap-4">
        <dl className="grid grid-cols-3 gap-2 text-sm">
          <div>
            <dt className="text-muted-foreground">{t('payments.total')}</dt>
            <dd className="font-medium tabular-nums">{formatMoney(data.totalAmount, data.currency)}</dd>
          </div>
          <div>
            <dt className="text-muted-foreground">{t('payments.paid')}</dt>
            <dd className="font-medium text-success tabular-nums">
              {formatMoney(data.paidAmount, data.currency)}
            </dd>
          </div>
          <div>
            <dt className="text-muted-foreground">{t('payments.balance')}</dt>
            <dd className="font-semibold tabular-nums">{formatMoney(data.balance, data.currency)}</dd>
          </div>
        </dl>
        <div
          className="h-2 overflow-hidden rounded-full bg-muted"
          role="progressbar"
          aria-label={t('payments.paid')}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={Math.round(percent)}
        >
          <div
            className="h-full origin-left rounded-full bg-success transition-transform duration-700 ease-[var(--ease-out-expo)]"
            style={{ transform: `scaleX(${percent / 100})` }}
          />
        </div>
        {data.paymentState === 'PAID' ? <p className="text-sm text-muted-foreground">{t('payments.fullyPaid')}</p> : null}

        {data.payments.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('payments.empty')}</p>
        ) : (
          <ul className="grid gap-2">
            {data.payments.map((p) => (
              <li key={p.id} className="flex flex-wrap items-start gap-3 rounded-md border p-3">
                <div className="min-w-0 flex-1">
                  <p className={p.voided ? 'font-medium line-through text-muted-foreground' : 'font-medium'}>
                    {formatMoney(p.amount, data.currency)}{' '}
                    <span className="text-sm font-normal text-muted-foreground">
                      · {t(`paymentMethod.${p.paymentMethod}`)} · {formatDate(p.paymentDate)}
                    </span>
                  </p>
                  {p.reference ? <p className="text-sm break-all">{p.reference}</p> : null}
                  <p className="text-xs text-muted-foreground">
                    {t('payments.recordedBy', { name: p.createdBy?.name ?? t('common.deletedUser') })}
                  </p>
                  {p.voided ? (
                    <p className="mt-1 text-xs text-destructive">
                      {t('payments.voidedBy', {
                        name: p.voidedBy?.name ?? t('common.deletedUser'),
                        reason: p.voidReason,
                      })}
                    </p>
                  ) : null}
                </div>
                {p.voided ? (
                  <Badge variant="outline">{t('payments.voided')}</Badge>
                ) : data.canRecord ? (
                  <Button variant="ghost" size="sm" onClick={() => setVoiding(p)}>
                    <Ban />
                    {t('payments.void')}
                  </Button>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </CardContent>

      {data.canRecord ? (
        <RecordPaymentDialog orderId={orderId} data={data} open={recording} onOpenChange={setRecording} />
      ) : null}
      <VoidPaymentDialog orderId={orderId} payment={voiding} currency={data.currency} onClose={() => setVoiding(null)} />
    </Card>
  )
}
