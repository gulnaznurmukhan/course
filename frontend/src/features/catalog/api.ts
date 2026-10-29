import { apiFetch } from '@/lib/api'
import { query, type Page } from '@/lib/types'

export interface CatalogService {
  id: string
  name: string
  description: string | null
  unit: string
  price: number
  currency: string
  archived: boolean
}

export interface ServiceInput {
  name: string
  description?: string
  unit: string
  price: string
  currency: string
}

const base = (ws: string) => `/workspaces/${ws}/services`

export const catalogApi = {
  list: (ws: string, params: { search?: string; status?: 'ACTIVE' | 'ARCHIVED' | 'ALL'; currency?: string; page?: number }) =>
    apiFetch<Page<CatalogService>>(`${base(ws)}${query({ ...params, size: 50 })}`),
  create: (ws: string, input: ServiceInput) =>
    apiFetch<CatalogService>(base(ws), { method: 'POST', body: JSON.stringify(input) }),
  update: (ws: string, id: string, input: ServiceInput) =>
    apiFetch<CatalogService>(`${base(ws)}/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
  archive: (ws: string, id: string) => apiFetch<CatalogService>(`${base(ws)}/${id}/archive`, { method: 'POST' }),
  restore: (ws: string, id: string) => apiFetch<CatalogService>(`${base(ws)}/${id}/restore`, { method: 'POST' }),
}
