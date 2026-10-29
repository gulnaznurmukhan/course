import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '@/i18n'
import { mockBackend, renderApp } from '@/test/render'

const me = {
  id: 'u1', fullName: 'Aigerim', email: 'a@example.com', preferredLanguage: 'en', theme: 'SYSTEM',
  notifications: { deadlines: true, overdue: true, orderChanges: true, payments: true }, avatarUpdatedAt: null,
  workspaces: [{ id: 'w1', name: 'Studio', role: 'OWNER' }],
}

const order = {
  id: 'o1', orderNumber: 'ORD-000001', title: 'Website', description: null, totalAmount: 1000, currency: 'KZT',
  status: 'CONFIRMED', deadline: null, overdue: false, client: { id: 'c1', name: 'Client' }, assignedUser: null,
  archived: false, createdAt: '2026-01-01T10:00:00Z', updatedAt: '2026-01-01T10:00:00Z', version: 4,
  paidAmount: 0, balance: 1000, paymentState: 'UNPAID', allowedTransitions: [], canEdit: true,
}

const emptyPage = { items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 }

type Handler = Parameters<typeof mockBackend>[0]
const withDefaults = (extra: Handler): Handler => (url, init) => {
  const custom = extra(url, init)
  if (custom.status !== 0) return custom
  if (url.endsWith('/users/me')) return { status: 200, body: me }
  if (url.includes('/unread-count')) return { status: 200, body: { count: 0 } }
  return { status: 404 }
}
const posts = (fetchMock: ReturnType<typeof mockBackend>, part: string, method = 'POST') =>
  fetchMock.mock.calls.filter(([url, init]) => String(url).includes(part) && init?.method === method)

beforeEach(async () => {
  vi.unstubAllGlobals()
  localStorage.clear()
  await i18n.changeLanguage('en')
})

describe('order line items', () => {
  it('sends only the service id for price-list lines (the server sets the price)', async () => {
    const fetchMock = mockBackend(withDefaults((url, init) => {
      if (url.endsWith('/orders/o1') && init?.method === 'GET') return { status: 200, body: order }
      if (url.endsWith('/orders/o1/items') && init?.method === 'PUT') {
        return { status: 200, body: { items: [], totalAmount: 30000, currency: 'KZT', version: 5 } }
      }
      if (url.endsWith('/orders/o1/items')) return { status: 200, body: { items: [], totalAmount: 1000, currency: 'KZT', version: 4 } }
      if (url.includes('/services')) {
        return { status: 200, body: { ...emptyPage, totalItems: 1, items: [
          { id: 's1', name: 'Design', description: null, unit: 'hour', price: 15000, currency: 'KZT', archived: false }] } }
      }
      if (url.includes('/payments')) return { status: 200, body: { currency: 'KZT', totalAmount: 1000, paidAmount: 0, balance: 1000, paymentState: 'UNPAID', canRecord: true, payments: [] } }
      if (url.endsWith('/notes')) return { status: 200, body: [] }
      if (url.includes('/activity')) return { status: 200, body: emptyPage }
      if (url.endsWith('/share-link')) return { status: 200, body: { active: false, createdAt: null, expiresAt: null } }
      return { status: 0 }
    }))
    renderApp('/orders/o1')

    await userEvent.click(await screen.findByRole('button', { name: 'Edit items' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add line' }))
    await userEvent.click(within(dialog).getByRole('combobox', { name: 'Name 1' }))
    await userEvent.click(await screen.findByRole('option', { name: /Design/ }))
    const qty = within(dialog).getByLabelText('Qty 1')
    await userEvent.clear(qty)
    await userEvent.type(qty, '2')

    // Exact preview: 2 x 15 000
    expect(within(dialog).getByText(/Total:/).textContent).toMatch(/30[\s ,]?000/)
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    await waitFor(() => {
      const [call] = posts(fetchMock, '/orders/o1/items', 'PUT')
      expect(JSON.parse(String(call[1]?.body))).toEqual({ version: 4, items: [{ serviceId: 's1', quantity: '2' }] })
    })
  }, 20_000)
})

describe('customer portal', () => {
  const publicOrder = {
    sellerName: 'ТОО Студия', orderNumber: 'ORD-000007', title: 'Logo', status: 'IN_PROGRESS', deadline: '2026-02-01',
    currency: 'KZT', totalAmount: 1000, paidAmount: 400, balance: 600, paymentState: 'PARTIALLY_PAID',
    items: [{ name: 'Design', unit: 'hour', quantity: 2, lineTotal: 1000 }],
    payments: [{ date: '2026-01-10', amount: 400 }], updatedAt: '2026-01-10T10:00:00Z',
  }

  it('shows the order without signing in', async () => {
    mockBackend((url) => (url.includes('/public/orders/abc')
      ? { status: 200, body: publicOrder }
      : { status: 401, body: { status: 401, code: 'UNAUTHORIZED' } }))
    renderApp('/p/abc')

    expect(await screen.findByRole('heading', { name: 'Order ORD-000007' })).toBeInTheDocument()
    expect(screen.getByText('from ТОО Студия')).toBeInTheDocument()
    expect(screen.getByText('Partially paid')).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '40')
  })

  it('explains an invalid link', async () => {
    mockBackend(() => ({ status: 404, body: { status: 404, code: 'NOT_FOUND' } }))
    renderApp('/p/expired')
    expect(await screen.findByRole('heading', { name: 'The link is not valid' })).toBeInTheDocument()
  })
})

describe('client import', () => {
  it('previews first and imports only after confirmation', async () => {
    const preview = { total: 3, valid: 1, invalid: 1, duplicates: 1, imported: 0, rows: [
      { line: 2, name: 'Asel', email: 'asel@mail.kz', phone: null, status: 'VALID', errors: [], duplicateOf: null },
      { line: 3, name: null, email: null, phone: null, status: 'INVALID', errors: ['name'], duplicateOf: null },
      { line: 4, name: 'Dana', email: null, phone: '+7 701', status: 'DUPLICATE', errors: [], duplicateOf: 'Dana B.' },
    ] }
    const fetchMock = mockBackend(withDefaults((url) => {
      if (url.includes('/clients/import?dryRun=true')) return { status: 200, body: preview }
      if (url.includes('/clients/import?dryRun=false')) return { status: 200, body: { ...preview, imported: 1 } }
      if (url.includes('/clients')) return { status: 200, body: emptyPage }
      return { status: 0 }
    }))
    renderApp('/clients')

    await userEvent.click(await screen.findByRole('button', { name: 'Import' }))
    const file = new File(['name\nAsel\n'], 'clients.csv', { type: 'text/csv' })
    await userEvent.upload(screen.getByLabelText('Choose CSV file'), file)

    expect(await screen.findByText('already exists: Dana B.')).toBeInTheDocument()
    expect(screen.getByText('check: Name')).toBeInTheDocument()
    expect(posts(fetchMock, 'dryRun=false')).toHaveLength(0)

    await userEvent.click(screen.getByRole('button', { name: 'Import 1 client' }))
    await waitFor(() => expect(posts(fetchMock, 'dryRun=false')).toHaveLength(1))
  })
})

describe('invoice and calendar', () => {
  it('prints the amounts calculated by the server', async () => {
    mockBackend(withDefaults((url) => url.endsWith('/orders/o1/invoice') ? { status: 200, body: {
      number: 'ORD-000001', issueDate: '2026-01-15', dueDate: '2026-01-31', workspaceName: 'Studio',
      seller: { legalName: 'ТОО Студия', bin: '123456789012', address: null, phone: null, email: null, bankName: 'Kaspi', iban: 'KZ86125KZT5004100100', bik: 'CASPKZKA' },
      buyer: { name: 'Client', company: 'ТОО Алма', address: null, phone: null, email: null },
      currency: 'KZT', lines: [{ number: 1, name: 'Design', unit: 'hour', quantity: 2, unitPrice: 500, lineTotal: 1000 }],
      totalAmount: 1000, paidAmount: 400, balance: 600, paymentState: 'PARTIALLY_PAID', orderStatus: 'CONFIRMED',
    } } : { status: 0 }))
    renderApp('/orders/o1/invoice')

    expect(await screen.findByRole('heading', { name: /Invoice No\. ORD-000001/ })).toBeInTheDocument()
    expect(screen.getByText('KZ86125KZT5004100100')).toBeInTheDocument()
    expect(screen.getByText('Amount due:')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Print / Save as PDF' })).toBeInTheDocument()
  })

  it('places deadlines on the calendar', async () => {
    const today = new Date()
    const date = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-15`
    mockBackend(withDefaults((url) => url.includes('/calendar?') ? { status: 200, body: [
      { id: 'o9', orderNumber: 'ORD-000009', title: 'Landing page', clientName: 'Kaspi', deadline: date, status: 'NEW', overdue: false },
    ] } : { status: 0 }))
    renderApp('/calendar')

    expect((await screen.findAllByText('Landing page')).length).toBeGreaterThan(0)
  })
})
