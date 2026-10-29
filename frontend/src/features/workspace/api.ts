import type { WorkspaceRole } from '@/features/auth/api'
import { apiFetch } from '@/lib/api'
import { query, type Activity, type Page } from '@/lib/types'

export interface Member {
  userId: string
  fullName: string
  email: string
  role: WorkspaceRole
  joinedAt: string
}

export interface Invitation {
  id: string
  email: string
  role: WorkspaceRole
  createdAt: string
}

const base = (workspaceId: string) => `/workspaces/${workspaceId}`

export const workspaceApi = {
  members: (ws: string) =>
    apiFetch<{ members: Member[]; invitations: Invitation[] }>(`${base(ws)}/members`),
  invite: (ws: string, email: string, role: WorkspaceRole) =>
    apiFetch<void>(`${base(ws)}/members`, { method: 'POST', body: JSON.stringify({ email, role }) }),
  changeRole: (ws: string, userId: string, role: WorkspaceRole) =>
    apiFetch<void>(`${base(ws)}/members/${userId}`, { method: 'PATCH', body: JSON.stringify({ role }) }),
  removeMember: (ws: string, userId: string) =>
    apiFetch<void>(`${base(ws)}/members/${userId}`, { method: 'DELETE' }),
  cancelInvitation: (ws: string, id: string) =>
    apiFetch<void>(`${base(ws)}/invitations/${id}`, { method: 'DELETE' }),
  rename: (ws: string, name: string) =>
    apiFetch<{ id: string; name: string }>(base(ws), { method: 'PATCH', body: JSON.stringify({ name }) }),
  activity: (ws: string, page = 0, size = 20) =>
    apiFetch<Page<Activity>>(`${base(ws)}/activity${query({ page, size })}`),
}
