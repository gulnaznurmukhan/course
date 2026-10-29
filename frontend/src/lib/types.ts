/** Shapes shared by several API modules. */

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface Ref {
  id: string
  name: string
}

export interface Activity {
  id: number
  action: string
  entityType: 'CLIENT' | 'ORDER' | 'MEMBER' | 'WORKSPACE'
  entityId: string | null
  actor: { id: string; fullName: string } | null
  metadata: Record<string, unknown>
  createdAt: string
}

/** Builds a query string, skipping empty values. */
export function query(params: Record<string, string | number | boolean | undefined | null>) {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}
