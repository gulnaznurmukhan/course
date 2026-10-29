import { apiFetch } from '@/lib/api'
import type { PaymentState } from '@/features/payments/api'
import { query, type Activity, type Page, type Ref } from '@/lib/types'

export const ORDER_STATUSES = ['NEW', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'] as const
export type OrderStatus = (typeof ORDER_STATUSES)[number]

export const CURRENCIES = ['KZT', 'RUB', 'USD', 'EUR'] as const

export interface Order {
  id: string
  orderNumber: string
  title: string
  description: string | null
  totalAmount: number
  currency: string
  status: OrderStatus
  deadline: string | null
  overdue: boolean
  client: Ref
  assignedUser: Ref | null
  archived: boolean
  createdAt: string
  updatedAt: string
  version: number
  paidAmount: number
  balance: number
  paymentState: PaymentState
  allowedTransitions: OrderStatus[]
  canEdit: boolean
}

export interface OrderInput {
  title: string
  description?: string
  totalAmount: string
  currency: string
  deadline?: string | null
  assignedUserId?: string | null
}

export interface OrderListParams {
  search?: string
  archive?: 'ACTIVE' | 'ARCHIVED' | 'ALL'
  status?: OrderStatus
  clientId?: string
  overdue?: boolean
  paymentState?: PaymentState
  sort?: 'CREATED_AT' | 'UPDATED_AT' | 'DEADLINE' | 'TOTAL_AMOUNT' | 'ORDER_NUMBER'
  direction?: 'ASC' | 'DESC'
  page?: number
  size?: number
}

export interface Note {
  id: string
  body: string
  author: Ref | null
  createdAt: string
}

const base = (ws: string) => `/workspaces/${ws}/orders`
const json = (body: unknown) => JSON.stringify(body)

export const ordersApi = {
  list: (ws: string, params: OrderListParams) => apiFetch<Page<Order>>(`${base(ws)}${query({ ...params })}`),
  board: (ws: string, search?: string) =>
    apiFetch<{ status: OrderStatus; items: Order[]; total: number }[]>(`${base(ws)}/board${query({ search })}`),
  get: (ws: string, id: string) => apiFetch<Order>(`${base(ws)}/${id}`),
  create: (ws: string, input: OrderInput & { clientId: string }) =>
    apiFetch<Order>(base(ws), { method: 'POST', body: json(input) }),
  update: (ws: string, id: string, input: OrderInput & { version: number }) =>
    apiFetch<Order>(`${base(ws)}/${id}`, { method: 'PUT', body: json(input) }),
  changeStatus: (ws: string, id: string, status: OrderStatus, version: number) =>
    apiFetch<Order>(`${base(ws)}/${id}/status`, { method: 'POST', body: json({ status, version }) }),
  archive: (ws: string, id: string) => apiFetch<Order>(`${base(ws)}/${id}/archive`, { method: 'POST' }),
  restore: (ws: string, id: string) => apiFetch<Order>(`${base(ws)}/${id}/restore`, { method: 'POST' }),
  notes: (ws: string, id: string) => apiFetch<Note[]>(`${base(ws)}/${id}/notes`),
  addNote: (ws: string, id: string, body: string) =>
    apiFetch<Note>(`${base(ws)}/${id}/notes`, { method: 'POST', body: json({ body }) }),
  activity: (ws: string, id: string) => apiFetch<Page<Activity>>(`${base(ws)}/${id}/activity?size=100`),
}
