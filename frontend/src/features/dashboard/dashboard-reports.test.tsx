import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '@/i18n'
import { mockBackend, renderApp } from '@/test/render'

function me(role: 'OWNER' | 'EMPLOYEE') {
  return {
    id: 'u1', fullName: 'Aigerim', email: 'a@example.com', preferredLanguage: 'en', theme: 'SYSTEM',
    workspaces: [{ id: 'w1', name: 'Studio', role }],
  }
}

const dashboard = {
  from: '2026-01-01', to: '2026-01-31', onlyAssignedOrders: false,
  totalClients: 12, activeOrders: 5, completedOrders: 7, overdueOrders: 2,
  ordersByStatus: { NEW: 2, CONFIRMED: 1, IN_PROGRESS: 2, COMPLETED: 7, CANCELLED: 0 },
  paymentsReceived: [{ currency: 'KZT', amount: 250000, count: 3 }, { currency: 'USD', amount: 120, count: 1 }],
  outstandingBalances: [{ currency: 'KZT', amount: 90000.5, count: 2 }],
  upcomingDeadlines: [{ id: 'o1', orderNumber: 'ORD-000004', title: 'Logo', clientName: 'Kaspi', deadline: '2026-02-03', status: 'IN_PROGRESS' }],
  recentActivity: [],
}

const notifications = {
  items: [
    { id: 'n1', type: 'ORDER_OVERDUE', orderId: 'o1', read: false, createdAt: '2026-01-05T10:00:00Z',
      payload: { orderNumber: 'ORD-000004', title: 'Logo', deadline: '2026-01-04' } },
    { id: 'n2', type: 'ORDER_CHANGED', orderId: 'o1', read: true, createdAt: '2026-01-04T10:00:00Z',
      payload: { orderNumber: 'ORD-000004', title: 'Logo', change: 'STATUS', actor: 'Dana', from: 'CONFIRMED', to: 'IN_PROGRESS' } },
  ],
  page: 0, size: 15, totalItems: 2, totalPages: 1,
}

beforeEach(async () => {
  vi.unstubAllGlobals()
  localStorage.clear()
  await i18n.changeLanguage('en')
})

describe('dashboard', () => {
  it('shows real figures from the API, money per currency and the status chart', async () => {
    mockBackend((url) => {
      if (url.endsWith('/users/me')) return { status: 200, body: me('OWNER') }
      if (url.includes('/dashboard')) return { status: 200, body: dashboard }
      if (url.includes('/unread-count')) return { status: 200, body: { count: 0 } }
      return { status: 404 }
    })
    renderApp('/dashboard')

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(screen.getByText('12')).toBeInTheDocument()
    expect(screen.getByText('3 payments')).toBeInTheDocument()
    expect(screen.getByText('1 payment')).toBeInTheDocument()
    expect(screen.getByText('2 orders')).toBeInTheDocument()
    // Chart bars are reachable by keyboard and describe their value
    expect(screen.getByLabelText('Completed: 7 of 12 (58%)')).toBeInTheDocument()
    expect(screen.getByText('Logo')).toBeInTheDocument()
  })

  it('requests the selected payment period', async () => {
    const fetchMock = mockBackend((url) => {
      if (url.endsWith('/users/me')) return { status: 200, body: me('OWNER') }
      if (url.includes('/dashboard')) return { status: 200, body: dashboard }
      return { status: 200, body: { count: 0 } }
    })
    renderApp('/dashboard')
    await screen.findByRole('heading', { name: 'Dashboard' })

    await userEvent.click(screen.getByRole('combobox', { name: 'Period' }))
    await userEvent.click(await screen.findByRole('option', { name: 'This year' }))

    const year = new Date().getFullYear()
    await waitFor(() =>
      expect(fetchMock.mock.calls.some(([url]) => String(url).includes(`/dashboard?from=${year}-01-01`))).toBe(true),
    )
  })
})

describe('reports', () => {
  it('links the CSV export to the same filters and hides reports from employees', async () => {
    mockBackend((url) => {
      if (url.endsWith('/users/me')) return { status: 200, body: me('OWNER') }
      if (url.includes('/reports/orders')) {
        return { status: 200, body: { summary: [], items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 } }
      }
      if (url.includes('/clients')) return { status: 200, body: { items: [], page: 0, size: 100, totalItems: 0, totalPages: 0 } }
      return { status: 200, body: { count: 0 } }
    })
    renderApp('/reports')

    const link = await screen.findByRole('link', { name: 'Export CSV' })
    expect(link.getAttribute('href')).toMatch(/^\/api\/v1\/workspaces\/w1\/reports\/orders\.csv\?from=\d{4}-\d{2}-01&to=/)
    expect(await screen.findByText('No records for the selected filters')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Reports' })).toBeInTheDocument()
  })

  it('does not show the reports menu item to employees', async () => {
    mockBackend((url) => {
      if (url.endsWith('/users/me')) return { status: 200, body: me('EMPLOYEE') }
      if (url.includes('/dashboard')) return { status: 200, body: { ...dashboard, onlyAssignedOrders: true, recentActivity: null } }
      return { status: 200, body: { count: 0 } }
    })
    renderApp('/dashboard')

    expect(await screen.findByText('Figures cover the orders assigned to you.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Reports' })).not.toBeInTheDocument()
  })
})

describe('notification bell', () => {
  it('shows the unread count, translated messages and marks a notification as read', async () => {
    const fetchMock = mockBackend((url, init) => {
      if (url.endsWith('/users/me')) return { status: 200, body: me('OWNER') }
      if (url.includes('/dashboard')) return { status: 200, body: dashboard }
      if (url.includes('/unread-count')) return { status: 200, body: { count: 1 } }
      if (url.endsWith('/notifications/n1/read') && init?.method === 'POST') return { status: 204 }
      if (url.includes('/notifications')) return { status: 200, body: notifications }
      return { status: 404 }
    })
    renderApp('/dashboard')

    const bell = await screen.findByRole('button', { name: '1 unread notification' })
    await userEvent.click(bell)
    const menu = await screen.findByRole('menu')
    expect(within(menu).getByText(/Order ORD-000004 «Logo» is overdue/)).toBeInTheDocument()
    expect(within(menu).getByText('Dana changed the status of ORD-000004: Confirmed → In progress')).toBeInTheDocument()

    await userEvent.click(within(menu).getByText(/is overdue/))
    await waitFor(() =>
      expect(fetchMock.mock.calls.some(([url, i]) => String(url).endsWith('/n1/read') && i?.method === 'POST')).toBe(true),
    )
  })
})
