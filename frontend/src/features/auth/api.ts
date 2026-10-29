import { apiFetch } from '@/lib/api'

export type WorkspaceRole = 'OWNER' | 'MANAGER' | 'EMPLOYEE'

export interface Me {
  id: string
  fullName: string
  email: string
  preferredLanguage: 'kk' | 'ru' | 'en'
  theme: 'LIGHT' | 'DARK' | 'SYSTEM'
  notifications: { deadlines: boolean; overdue: boolean; orderChanges: boolean; payments: boolean }
  avatarUpdatedAt: string | null
  workspaces: { id: string; name: string; role: WorkspaceRole }[]
}

const post = <T = void>(path: string, body: unknown) =>
  apiFetch<T>(path, { method: 'POST', body: JSON.stringify(body) })

export const authApi = {
  me: () => apiFetch<Me>('/users/me'),
  login: (email: string, password: string) => post<Me>('/auth/login', { email, password }),
  logout: () => apiFetch<void>('/auth/logout', { method: 'POST' }),
  register: (body: { fullName: string; email: string; password: string; language: string }) =>
    post('/auth/register', body),
  verifyEmail: (token: string) => post('/auth/verify-email', { token }),
  resendVerification: (email: string) => post('/auth/resend-verification', { email }),
  forgotPassword: (email: string) => post('/auth/forgot-password', { email }),
  resetPassword: (token: string, newPassword: string) =>
    post('/auth/reset-password', { token, newPassword }),
}
