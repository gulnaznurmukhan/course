import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '@/i18n'
import { mockBackend, renderApp } from '@/test/render'

const WS = 'w1'

function me(role: 'OWNER' | 'MANAGER' | 'EMPLOYEE') {
  return {
    id: 'u1',
    fullName: 'Aigerim',
    email: 'aigerim@example.com',
    preferredLanguage: 'en',
    theme: 'SYSTEM',
    workspaces: [{ id: WS, name: 'Studio', role }],
  }
}

const order = {
  id: 'o1',
  orderNumber: 'ORD-000001',
  title: 'Website redesign',
  description: null,
  totalAmount: 150000.5,
  currency: 'KZT',
  status: 'CONFIRMED',
  deadline: '2026-01-10',
  overdue: true,
  client: { id: 'c1', name: 'Kaspi Shop' },
  assignedUser: null,
  archived: false,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
  version: 3,
  allowedTransitions: ['IN_PROGRESS', 'CANCELLED'],
  canEdit: true,
}

const page = (items: unknown[]) => ({ items, page: 0, size: 20, totalItems: items.length, totalPages: 1 })

beforeEach(async () => {
  vi.unstubAllGlobals()
  localStorage.clear()
  await i18n.changeLanguage('en')
})

describe('orders page', () => {
  it('lists orders with translated status and overdue badge', async () => {
    mockBackend((url) => {
      if (url.endsWith('/users/me')) return { status: 200, body: me('OWNER') }
      if (url.includes(`/workspaces/${WS}/orders`)) return { status: 200, body: page([order]) }
      return { status: 404 }
    })
    renderApp('/orders')

    expect(await screen.findByText('Website redesign')).toBeInTheDocument()
    expect(screen.getByText('Confirmed')).toBeInTheDocument()
    expect(screen.getAllByText('Overdue').length).toBeGreaterThan(0)
    expect(screen.getByText('Kaspi Shop')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'New order' })).toBeInTheDocument()
  })

  it('hides management actions from employees', async () => {
    mockBackend((url) => {
      if (url.endsWith('/users/me')) return { status: 200, body: me('EMPLOYEE') }
      return { status: 200, body: page([]) }
    })
    renderApp('/orders')

    expect(await screen.findByRole('heading', { name: 'Orders' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'New order' })).not.toBeInTheDocument()
  })
})

describe('order detail page', () => {
  it('sends the current version when changing the status', async () => {
    const fetchMock = mockBackend((url, init) => {
      if (url.endsWith('/users/me')) return { status: 200, body: me('MANAGER') }
      if (url.endsWith('/orders/o1/status') && init?.method === 'POST') {
        return { status: 200, body: { ...order, status: 'IN_PROGRESS', version: 4 } }
      }
      if (url.endsWith('/orders/o1')) return { status: 200, body: order }
      if (url.endsWith('/orders/o1/notes')) return { status: 200, body: [] }
      if (url.includes('/orders/o1/activity')) return { status: 200, body: page([]) }
      return { status: 404 }
    })
    renderApp('/orders/o1')

    await userEvent.click(await screen.findByRole('button', { name: 'Start work' }))

    await waitFor(() => {
      const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/orders/o1/status'))
      expect(call).toBeDefined()
      expect(JSON.parse(String(call![1]?.body))).toEqual({ status: 'IN_PROGRESS', version: 3 })
    })
  })

  it('shows a friendly page for an order that does not exist', async () => {
    mockBackend((url) => {
      if (url.endsWith('/users/me')) return { status: 200, body: me('OWNER') }
      return { status: 404, body: { status: 404, code: 'NOT_FOUND' } }
    })
    renderApp('/orders/missing')

    expect(await screen.findByText('Order not found')).toBeInTheDocument()
  })
})

describe('team page', () => {
  const members = {
    members: [
      { userId: 'u1', fullName: 'Aigerim', email: 'aigerim@example.com', role: 'OWNER', joinedAt: '2026-01-01T10:00:00Z' },
      { userId: 'u2', fullName: 'Dana', email: 'dana@example.com', role: 'EMPLOYEE', joinedAt: '2026-01-02T10:00:00Z' },
    ],
    invitations: [],
  }

  it('lets the owner invite members', async () => {
    const fetchMock = mockBackend((url, init) => {
      if (url.endsWith('/users/me')) return { status: 200, body: me('OWNER') }
      if (url.endsWith('/members') && init?.method === 'POST') return { status: 202 }
      if (url.endsWith('/members')) return { status: 200, body: members }
      return { status: 404 }
    })
    renderApp('/team')

    await userEvent.click(await screen.findByRole('button', { name: 'Invite' }))
    await userEvent.type(screen.getByLabelText('Email'), 'new@example.com')
    await userEvent.click(screen.getAllByRole('button', { name: 'Invite' }).at(-1)!)

    await waitFor(() => {
      const call = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/members') && init?.method === 'POST')
      expect(JSON.parse(String(call![1]?.body))).toEqual({ email: 'new@example.com', role: 'EMPLOYEE' })
    })
  })

  it('shows members read-only to non-owners', async () => {
    mockBackend((url) => {
      if (url.endsWith('/users/me')) return { status: 200, body: me('MANAGER') }
      if (url.endsWith('/members')) return { status: 200, body: members }
      return { status: 404 }
    })
    renderApp('/team')

    expect(await screen.findByText('Dana')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Invite' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Remove/ })).not.toBeInTheDocument()
  })
})
