import type { OrderStatus } from '@/features/orders/api'
import type { PaymentState } from '@/features/payments/api'
import { apiFetch } from '@/lib/api'

export interface OrderItem {
  id: string
  serviceId: string | null
  name: string
  unit: string
  quantity: number
  unitPrice: number
  lineTotal: number
}

export interface OrderItems {
  items: OrderItem[]
  totalAmount: number
  currency: string
  version: number
}

export interface ItemInput {
  serviceId?: string | null
  name?: string
  unit?: string
  quantity: string
  unitPrice?: string
}

export interface Requisites {
  legalName: string | null
  bin: string | null
  address: string | null
  phone: string | null
  email: string | null
  bankName: string | null
  iban: string | null
  bik: string | null
}

export interface Invoice {
  number: string
  issueDate: string
  dueDate: string | null
  workspaceName: string
  seller: Requisites
  buyer: { name: string; company: string | null; address: string | null; phone: string | null; email: string | null }
  currency: string
  lines: { number: number; name: string; unit: string; quantity: number; unitPrice: number; lineTotal: number }[]
  totalAmount: number
  paidAmount: number
  balance: number
  paymentState: PaymentState
  orderStatus: OrderStatus
}

export interface PublicOrder {
  sellerName: string
  orderNumber: string
  title: string
  status: OrderStatus
  deadline: string | null
  currency: string
  totalAmount: number
  paidAmount: number
  balance: number
  paymentState: PaymentState
  items: { name: string; unit: string; quantity: number; lineTotal: number }[]
  payments: { date: string; amount: number }[]
  updatedAt: string
}

const order = (ws: string, id: string) => `/workspaces/${ws}/orders/${id}`

export const orderExtrasApi = {
  items: (ws: string, id: string) => apiFetch<OrderItems>(`${order(ws, id)}/items`),
  replaceItems: (ws: string, id: string, version: number, items: ItemInput[]) =>
    apiFetch<OrderItems>(`${order(ws, id)}/items`, { method: 'PUT', body: JSON.stringify({ version, items }) }),
  invoice: (ws: string, id: string) => apiFetch<Invoice>(`${order(ws, id)}/invoice`),
  shareStatus: (ws: string, id: string) =>
    apiFetch<{ active: boolean; createdAt: string | null; expiresAt: string | null }>(`${order(ws, id)}/share-link`),
  createShare: (ws: string, id: string) =>
    apiFetch<{ url: string; expiresAt: string }>(`${order(ws, id)}/share-link`, { method: 'POST' }),
  revokeShare: (ws: string, id: string) => apiFetch<void>(`${order(ws, id)}/share-link`, { method: 'DELETE' }),
  requisites: (ws: string) => apiFetch<Requisites>(`/workspaces/${ws}/requisites`),
  updateRequisites: (ws: string, r: Requisites) =>
    apiFetch<Requisites>(`/workspaces/${ws}/requisites`, { method: 'PUT', body: JSON.stringify(r) }),
  publicOrder: (token: string) => apiFetch<PublicOrder>(`/public/orders/${encodeURIComponent(token)}`),
}
