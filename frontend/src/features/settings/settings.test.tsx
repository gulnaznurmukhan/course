import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '@/i18n'
import { mockBackend, renderApp } from '@/test/render'

const me = {
  id: 'u1', fullName: 'Aigerim', email: 'a@example.com', preferredLanguage: 'en', theme: 'SYSTEM',
  notifications: { deadlines: true, overdue: true, orderChanges: true, payments: true },
  avatarUpdatedAt: null,
  workspaces: [{ id: 'w1', name: 'Studio', role: 'OWNER' }, { id: 'w2', name: 'Agency', role: 'EMPLOYEE' }],
}

const sessions = [
  { key: 'k1', createdAt: '2026-01-01T10:00:00Z', lastAccessedAt: '2026-01-02T10:00:00Z', userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:130.0) Gecko/20100101 Firefox/130.0', ipAddress: '10.0.0.1', current: true },
  { key: 'k2', createdAt: '2026-01-01T10:00:00Z', lastAccessedAt: '2026-01-01T12:00:00Z', userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1', ipAddress: '10.0.0.2', current: false },
]

type Handler = Parameters<typeof mockBackend>[0]

function backend(extra: Handler = () => ({ status: 0 })) {
  return mockBackend((url, init) => {
    const custom = extra(url, init)
    if (custom.status !== 0) return custom
    if (url.endsWith('/users/me') && (!init?.method || init.method === 'GET')) return { status: 200, body: me }
    if (url.endsWith('/users/me') && init?.method === 'PATCH') {
      return { status: 200, body: { ...me, ...JSON.parse(String(init.body)) } }
    }
    if (url.endsWith('/users/me/sessions')) return { status: 200, body: sessions }
    if (url.includes('/unread-count')) return { status: 200, body: { count: 0 } }
    return { status: 404 }
  })
}

const calls = (fetchMock: ReturnType<typeof mockBackend>, suffix: string, method: string) =>
  fetchMock.mock.calls.filter(([url, init]) => String(url).endsWith(suffix) && init?.method === method)

beforeEach(async () => {
  vi.unstubAllGlobals()
  localStorage.clear()
  await i18n.changeLanguage('en')
})

describe('settings', () => {
  it('saves the language to the account when it is changed', async () => {
    const fetchMock = backend()
    renderApp('/settings')

    await userEvent.click(await screen.findByRole('button', { name: 'Русский' }))

    await waitFor(() => {
      const [call] = calls(fetchMock, '/users/me', 'PATCH')
      expect(JSON.parse(String(call[1]?.body))).toEqual({ preferredLanguage: 'ru' })
    })
    expect(await screen.findByRole('heading', { name: 'Настройки' })).toBeInTheDocument()
  })

  it('applies the language stored in the account after sign-in', async () => {
    mockBackend((url) => {
      if (url.endsWith('/users/me')) return { status: 200, body: { ...me, preferredLanguage: 'kk' } }
      if (url.endsWith('/users/me/sessions')) return { status: 200, body: sessions }
      return { status: 200, body: { count: 0 } }
    })
    renderApp('/settings')

    expect(await screen.findByRole('heading', { name: 'Баптаулар' })).toBeInTheDocument()
  })

  it('lists sessions and signs out another device', async () => {
    const fetchMock = backend((url, init) =>
      url.endsWith('/users/me/sessions/k2') && init?.method === 'DELETE' ? { status: 204 } : { status: 0 },
    )
    renderApp('/settings')

    // Raw user agents are shown as readable device names
    expect(await screen.findByText('Safari · iOS')).toBeInTheDocument()
    expect(screen.getByText('Firefox · Windows')).toBeInTheDocument()
    expect(screen.getByText('This device')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    await waitFor(() => expect(calls(fetchMock, '/users/me/sessions/k2', 'DELETE')).toHaveLength(1))
  })

  it('requires the password and a confirmation before deleting the account', async () => {
    const fetchMock = backend((url, init) =>
      url.endsWith('/users/me') && init?.method === 'DELETE' ? { status: 204 } : { status: 0 },
    )
    renderApp('/settings')

    await userEvent.click(await screen.findByRole('button', { name: 'Delete my account' }))
    // The owned workspace that will be deleted is listed in the dialog
    expect(within(screen.getByRole('dialog')).getByText('Studio')).toBeInTheDocument()
    const confirm = screen.getAllByRole('button', { name: 'Delete my account' }).at(-1)!
    expect(confirm).toBeDisabled()

    await userEvent.type(screen.getByLabelText('Enter your password to confirm'), 'Str0ngPassw0rd!')
    expect(confirm).toBeDisabled()
    await userEvent.click(screen.getByLabelText('I understand that this cannot be undone'))
    await userEvent.click(confirm)

    await waitFor(() => {
      const [call] = calls(fetchMock, '/users/me', 'DELETE')
      expect(JSON.parse(String(call[1]?.body))).toEqual({ password: 'Str0ngPassw0rd!' })
    })
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })
})
