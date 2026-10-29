import { apiFetch } from '@/lib/api'
import type { Ref } from '@/lib/types'

export const PAYMENT_METHODS = ['CASH', 'CARD', 'BANK_TRANSFER', 'OTHER'] as const
export type PaymentMethod = (typeof PAYMENT_METHODS)[number]
export type PaymentState = 'UNPAID' | 'PARTIALLY_PAID' | 'PAID'

export interface Payment {
  id: string
  amount: number
  paymentMethod: PaymentMethod
  paymentDate: string
  reference: string | null
  createdBy: Ref | null
  createdAt: string
  voided: boolean
  voidedAt: string | null
  voidedBy: Ref | null
  voidReason: string | null
}

/** Totals are calculated by the backend with BigDecimal; the UI only displays them. */
export interface OrderPayments {
  currency: string
  totalAmount: number
  paidAmount: number
  balance: number
  paymentState: PaymentState
  canRecord: boolean
  payments: Payment[]
}

export interface PaymentInput {
  amount: string
  paymentMethod: PaymentMethod
  paymentDate: string
  reference?: string
}

const base = (ws: string, orderId: string) => `/workspaces/${ws}/orders/${orderId}/payments`

export const paymentsApi = {
  list: (ws: string, orderId: string) => apiFetch<OrderPayments>(base(ws, orderId)),
  /**
   * The same idempotency key must be sent again when the same submission is retried,
   * so a double click or a network retry never creates a second payment.
   */
  record: (ws: string, orderId: string, input: PaymentInput, idempotencyKey: string) =>
    apiFetch<Payment>(base(ws, orderId), {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify(input),
    }),
  void: (ws: string, orderId: string, paymentId: string, reason: string) =>
    apiFetch<Payment>(`${base(ws, orderId)}/${paymentId}/void`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    }),
}
