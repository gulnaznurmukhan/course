import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '@/i18n'
import { mockBackend, renderApp } from '@/test/render'

const signedOut = { status: 401, body: { status: 401, code: 'UNAUTHORIZED' } }
const me = {
  id: '1',
  fullName: 'Aigerim',
  email: 'aigerim@example.com',
  preferredLanguage: 'en',
  theme: 'SYSTEM',
  workspaces: [{ id: 'w1', name: 'Aigerim', role: 'OWNER' }],
}

const emptyPage = { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 }

/** Signed-in backend: /users/me answers with `me`, lists are empty. */
const dashboard = {
  from: '2026-01-01', to: '2026-01-31', onlyAssignedOrders: false, totalClients: 0, activeOrders: 0,
  completedOrders: 0, overdueOrders: 0,
  ordersByStatus: { NEW: 0, CONFIRMED: 0, IN_PROGRESS: 0, COMPLETED: 0, CANCELLED: 0 },
  paymentsReceived: [], outstandingBalances: [], upcomingDeadlines: [], recentActivity: [],
}

const signedInBackend = (url: string) => {
  if (url.includes('/dashboard')) return { status: 200, body: dashboard }
  if (url.includes('/unread-count')) return { status: 200, body: { count: 0 } }
  if (url.includes('/workspaces/')) return { status: 200, body: emptyPage }
  return { status: 200, body: me }
}

const callsTo = (fetchMock: ReturnType<typeof mockBackend>, path: string) =>
  fetchMock.mock.calls.filter(([url]) => String(url).endsWith(path))

beforeEach(async () => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  await i18n.changeLanguage('en')
})

describe('protected routes', () => {
  it('redirects signed-out visitors to the sign-in page', async () => {
    mockBackend(() => signedOut)
    renderApp('/')
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('opens the dashboard for a signed-in user', async () => {
    mockBackend(signedInBackend)
    renderApp('/')
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(screen.getAllByText('Owner').length).toBeGreaterThan(0)
  })
})

describe('server unavailable', () => {
  it('shows a retry screen instead of looping the loader', async () => {
    const fetchMock = mockBackend(() => ({ status: 502, body: 'Bad gateway' }))
    renderApp('/login')
    expect(await screen.findByRole('heading', { name: 'The server is not responding' }, { timeout: 4000 })).toBeInTheDocument()
    // the error screen stays put: no more requests (it used to loop forever)
    const calls = callsTo(fetchMock, '/users/me').length
    await new Promise((r) => setTimeout(r, 500))
    expect(callsTo(fetchMock, '/users/me')).toHaveLength(calls)

    mockBackend(() => signedOut)
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })
})

describe('login page', () => {
  it('validates fields before calling the API', async () => {
    const fetchMock = mockBackend(() => signedOut)
    renderApp('/login')
    await userEvent.click(await screen.findByRole('button', { name: 'Sign in' }))

    expect(await screen.findAllByText('This field is required.')).toHaveLength(2)
    expect(callsTo(fetchMock, '/auth/login')).toHaveLength(0)
  })

  it('shows a translated error for wrong credentials', async () => {
    mockBackend((url) =>
      url.endsWith('/auth/login')
        ? { status: 401, body: { status: 401, code: 'INVALID_CREDENTIALS' } }
        : signedOut,
    )
    renderApp('/login')
    await userEvent.type(await screen.findByLabelText('Email'), 'aigerim@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Incorrect email or password.')).toBeInTheDocument()
  })

  it('sends the CSRF token and opens the home page after signing in', async () => {
    let signedIn = false
    const fetchMock = mockBackend((url) => {
      if (url.endsWith('/auth/login')) {
        signedIn = true
        return { status: 200, body: me }
      }
      return signedIn ? signedInBackend(url) : signedOut
    })
    renderApp('/login')
    await userEvent.type(await screen.findByLabelText('Email'), 'aigerim@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'Str0ngPassw0rd!')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    const [, init] = callsTo(fetchMock, '/auth/login')[0]
    expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('test-csrf-token')
    expect(init?.credentials).toBe('include')
  })

  it('offers a new verification link when the email is not confirmed', async () => {
    mockBackend((url) => {
      if (url.endsWith('/auth/login')) {
        return { status: 403, body: { status: 403, code: 'EMAIL_NOT_VERIFIED' } }
      }
      if (url.endsWith('/auth/resend-verification')) return { status: 202 }
      return signedOut
    })
    renderApp('/login')
    await userEvent.type(await screen.findByLabelText('Email'), 'aigerim@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'Str0ngPassw0rd!')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Send a new link' }))

    expect(await screen.findByRole('heading', { name: 'Check your email' })).toBeInTheDocument()
  })
})

describe('registration', () => {
  it('registers with the current interface language and asks to check email', async () => {
    const fetchMock = mockBackend((url) =>
      url.endsWith('/auth/register') ? { status: 202 } : signedOut,
    )
    renderApp('/register')
    await userEvent.type(await screen.findByLabelText('Full name'), 'Aigerim')
    await userEvent.type(screen.getByLabelText('Email'), 'aigerim@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'Str0ngPassw0rd!')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))

    expect(
      await screen.findByText(/We sent a confirmation link to aigerim@example.com/),
    ).toBeInTheDocument()
    const [, init] = callsTo(fetchMock, '/auth/register')[0]
    expect(JSON.parse(String(init?.body))).toMatchObject({
      email: 'aigerim@example.com',
      language: 'en',
    })
  })

  it('rejects a short password', async () => {
    mockBackend(() => signedOut)
    renderApp('/register')
    await userEvent.type(await screen.findByLabelText('Password'), 'short')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))

    expect(
      await screen.findByText('The password must be at least 8 characters long.'),
    ).toBeInTheDocument()
  })
})

describe('email links', () => {
  it('confirms the email once and removes the token from the address bar', async () => {
    window.history.replaceState(null, '', '/verify-email?token=abc123')
    const fetchMock = mockBackend((url) =>
      url.endsWith('/auth/verify-email') ? { status: 204 } : signedOut,
    )
    renderApp('/verify-email?token=abc123')

    expect(await screen.findByRole('heading', { name: 'Email confirmed' })).toBeInTheDocument()
    expect(window.location.search).toBe('')
    await waitFor(() => expect(callsTo(fetchMock, '/auth/verify-email')).toHaveLength(1))
  })

  it('reports an invalid reset link', async () => {
    window.history.replaceState(null, '', '/reset-password?token=used')
    mockBackend((url) =>
      url.endsWith('/auth/reset-password')
        ? { status: 400, body: { status: 400, code: 'INVALID_TOKEN' } }
        : signedOut,
    )
    renderApp('/reset-password?token=used')
    await userEvent.type(await screen.findByLabelText('New password'), 'BrandNewPass9')
    await userEvent.type(screen.getByLabelText('Repeat password'), 'BrandNewPass9')
    await userEvent.click(screen.getByRole('button', { name: 'Save password' }))

    expect(
      await screen.findByText('The link has expired or has already been used.'),
    ).toBeInTheDocument()
  })
})
