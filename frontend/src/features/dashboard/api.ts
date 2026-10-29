import type { OrderStatus } from '@/features/orders/api'
import type { PaymentMethod, PaymentState } from '@/features/payments/api'
import { apiFetch } from '@/lib/api'
import { query, type Activity } from '@/lib/types'

export interface CurrencyAmount {
  currency: string
  amount: number
  count: number
}

export interface Dashboard {
  from: string
  to: string
  onlyAssignedOrders: boolean
  totalClients: number
  activeOrders: number
  completedOrders: number
  overdueOrders: number
  ordersByStatus: Record<OrderStatus, number>
  paymentsReceived: CurrencyAmount[]
  outstandingBalances: CurrencyAmount[]
  upcomingDeadlines: { id: string; orderNumber: string; title: string; clientName: string; deadline: string; status: OrderStatus }[]
  recentActivity: Activity[] | null
}

export interface OrderReportRow {
  id: string
  orderNumber: string
  createdDate: string
  clientName: string
  title: string
  status: OrderStatus
  currency: string
  totalAmount: number
  paidAmount: number
  balance: number
  paymentState: PaymentState
  deadline: string | null
  assignee: string | null
  archived: boolean
}

export interface OrderReport {
  summary: { currency: string; count: number; totalAmount: number; paidAmount: number; balance: number }[]
  items: OrderReportRow[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface PaymentReportRow {
  id: string
  paymentDate: string
  orderId: string
  orderNumber: string
  clientName: string
  amount: number
  currency: string
  method: PaymentMethod
  reference: string | null
  recordedBy: string | null
  voided: boolean
  voidReason: string | null
}

export interface PaymentReport {
  summary: CurrencyAmount[]
  items: PaymentReportRow[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface OrderReportFilters {
  from: string
  to: string
  clientId?: string
  status?: OrderStatus
  paymentState?: PaymentState
}

export interface PaymentReportFilters {
  from: string
  to: string
  clientId?: string
  method?: PaymentMethod
}

const base = (ws: string) => `/workspaces/${ws}`

export const reportsApi = {
  dashboard: (ws: string, from: string, to: string) => apiFetch<Dashboard>(`${base(ws)}/dashboard${query({ from, to })}`),
  orders: (ws: string, f: OrderReportFilters, page: number) =>
    apiFetch<OrderReport>(`${base(ws)}/reports/orders${query({ ...f, page, size: 50 })}`),
  payments: (ws: string, f: PaymentReportFilters, page: number) =>
    apiFetch<PaymentReport>(`${base(ws)}/reports/payments${query({ ...f, page, size: 50 })}`),
  /** Same-origin download link; the session cookie authorises it like any other request. */
  csvUrl: (apiUrl: string, ws: string, kind: 'orders' | 'payments', f: OrderReportFilters | PaymentReportFilters) =>
    `${apiUrl}${base(ws)}/reports/${kind}.csv${query({ ...f })}`,
}
