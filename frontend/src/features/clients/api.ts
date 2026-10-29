import { apiFetch } from '@/lib/api'
import { query, type Page } from '@/lib/types'

export interface Client {
  id: string
  name: string
  email: string | null
  phone: string | null
  company: string | null
  address: string | null
  notes: string | null
  archived: boolean
  archivedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface ClientInput {
  name: string
  email?: string
  phone?: string
  company?: string
  address?: string
  notes?: string
}

export interface ClientListParams {
  search?: string
  status?: 'ACTIVE' | 'ARCHIVED' | 'ALL'
  sort?: 'NAME' | 'CREATED_AT' | 'UPDATED_AT'
  direction?: 'ASC' | 'DESC'
  page?: number
  size?: number
}

export interface DuplicateClient {
  id: string
  name: string
  email: string | null
  phone: string | null
  archived: boolean
}

const base = (ws: string) => `/workspaces/${ws}/clients`
const json = (body: unknown) => JSON.stringify(body)

export const clientsApi = {
  list: (ws: string, params: ClientListParams) => apiFetch<Page<Client>>(`${base(ws)}${query({ ...params })}`),
  get: (ws: string, id: string) =>
    apiFetch<{ client: Client; totalOrders: number; activeOrders: number }>(`${base(ws)}/${id}`),
  create: (ws: string, input: ClientInput) => apiFetch<Client>(base(ws), { method: 'POST', body: json(input) }),
  update: (ws: string, id: string, input: ClientInput) =>
    apiFetch<Client>(`${base(ws)}/${id}`, { method: 'PUT', body: json(input) }),
  archive: (ws: string, id: string) => apiFetch<Client>(`${base(ws)}/${id}/archive`, { method: 'POST' }),
  restore: (ws: string, id: string) => apiFetch<Client>(`${base(ws)}/${id}/restore`, { method: 'POST' }),
  duplicates: (ws: string, email?: string, phone?: string, excludeId?: string) =>
    apiFetch<DuplicateClient[]>(`${base(ws)}/duplicates${query({ email, phone, excludeId })}`),
}
