import type { Me } from '@/features/auth/api'
import { apiFetch } from '@/lib/api'

export interface SessionInfo {
  key: string
  createdAt: string
  lastAccessedAt: string
  userAgent: string | null
  ipAddress: string | null
  current: boolean
}

export interface NotificationSettings {
  deadlines: boolean
  overdue: boolean
  orderChanges: boolean
  payments: boolean
}

const json = (body: unknown) => JSON.stringify(body)

export const settingsApi = {
  updateProfile: (patch: { fullName?: string; preferredLanguage?: string; theme?: Me['theme'] }) =>
    apiFetch<Me>('/users/me', { method: 'PATCH', body: json(patch) }),
  updateNotifications: (settings: NotificationSettings) =>
    apiFetch<Me>('/users/me/notification-settings', { method: 'PUT', body: json(settings) }),
  changePassword: (currentPassword: string, newPassword: string) =>
    apiFetch<void>('/users/me/password', { method: 'POST', body: json({ currentPassword, newPassword }) }),
  uploadAvatar: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return apiFetch<void>('/users/me/avatar', { method: 'PUT', body: form })
  },
  deleteAvatar: () => apiFetch<void>('/users/me/avatar', { method: 'DELETE' }),
  sessions: () => apiFetch<SessionInfo[]>('/users/me/sessions'),
  revokeSession: (key: string) => apiFetch<void>(`/users/me/sessions/${key}`, { method: 'DELETE' }),
  revokeOtherSessions: () => apiFetch<void>('/users/me/sessions/revoke-others', { method: 'POST' }),
  deleteAccount: (password: string) => apiFetch<void>('/users/me', { method: 'DELETE', body: json({ password }) }),
  renameWorkspace: (workspaceId: string, name: string) =>
    apiFetch<unknown>(`/workspaces/${workspaceId}`, { method: 'PATCH', body: json({ name }) }),
}
