import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '@/i18n'
import { mockBackend, renderApp } from '@/test/render'

const me = {
  id: 'u1',
  fullName: 'Aigerim',
  email: 'aigerim@example.com',
  preferredLanguage: 'en',
  theme: 'SYSTEM',
  workspaces: [{ id: 'w1', name: 'Studio', role: 'OWNER' }],
}

const order = {
  id: 'o1', orderNumber: 'ORD-000001', title: 'Website', description: null, totalAmount: 1000, currency: 'KZT',
  status: 'CONFIRMED', deadline: null, overdue: false, client: { id: 'c1', name: 'Client' }, assignedUser: null,
  archived: false, createdAt: '2026-01-01T10:00:00Z', updatedAt: '2026-01-01T10:00:00Z', version: 1,
  paidAmount: 400, balance: 600, paymentState: 'PARTIALLY_PAID', allowedTransitions: [], canEdit: true,
}

const payment = {
  id: 'p1', amount: 400, paymentMethod: 'CARD', paymentDate: '2026-01-02', reference: 'CHK-77',
  createdBy: { id: 'u1', name: 'Aigerim' }, createdAt: '2026-01-02T10:00:00Z',
  voided: false, voidedAt: null, voidedBy: null, voidReason: null,
}

const payments = {
  currency: 'KZT', totalAmount: 1000, paidAmount: 400, balance: 600, paymentState: 'PARTIALLY_PAID',
  canRecord: true, payments: [payment],
}

type Handler = Parameters<typeof mockBackend>[0]

function backend(extra: Handler) {
  return mockBackend((url, init) => {
    const custom = extra(url, init)
    if (custom.status !== 0) return custom
    if (url.endsWith('/users/me')) return { status: 200, body: me }
    if (url.endsWith('/orders/o1/payments')) return { status: 200, body: payments }
    if (url.endsWith('/orders/o1')) return { status: 200, body: order }
    if (url.endsWith('/orders/o1/notes')) return { status: 200, body: [] }
    if (url.includes('/orders/o1/activity')) return { status: 200, body: { items: [], page: 0, size: 100, totalItems: 0, totalPages: 0 } }
    return { status: 404 }
  })
}

const postsTo = (fetchMock: ReturnType<typeof mockBackend>, suffix: string) =>
  fetchMock.mock.calls.filter(([url, init]) => String(url).endsWith(suffix) && init?.method === 'POST')

beforeEach(async () => {
  vi.unstubAllGlobals()
  localStorage.clear()
  await i18n.changeLanguage('en')
})

describe('payments on the order page', () => {
  it('shows totals, state and the payment history', async () => {
    backend(() => ({ status: 0 }))
    renderApp('/orders/o1')

    expect(await screen.findByText('Partially paid')).toBeInTheDocument()
    expect(screen.getByText('CHK-77')).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '40')
  })

  it('reuses the idempotency key when a failed submission is retried', async () => {
    let attempts = 0
    const fetchMock = backend((url, init) => {
      if (url.endsWith('/orders/o1/payments') && init?.method === 'POST') {
        attempts += 1
        // First attempt: the server is unreachable (e.g. connection dropped)
        if (attempts === 1) throw new TypeError('Failed to fetch')
        return { status: 201, body: { ...payment, id: 'p2', amount: 600 } }
      }
      return { status: 0 }
    })
    renderApp('/orders/o1')

    await userEvent.click(await screen.findByRole('button', { name: 'Record payment' }))
    // The amount is prefilled with the remaining balance
    expect(screen.getByLabelText('Amount, KZT')).toHaveValue('600')

    const submit = screen.getAllByRole('button', { name: 'Record payment' }).at(-1)!
    await userEvent.click(submit)
    expect(await screen.findByText('Unable to reach the server. Check your connection.')).toBeInTheDocument()
    await userEvent.click(submit)

    await waitFor(() => expect(attempts).toBe(2))
    const [first, second] = postsTo(fetchMock, '/orders/o1/payments')
    const key = (call: (typeof first)) => new Headers(call[1]?.headers).get('Idempotency-Key')
    expect(key(first)).toMatch(/^[0-9a-f-]{36}$/)
    expect(key(second)).toBe(key(first))
    expect(JSON.parse(String(second[1]?.body))).toMatchObject({ amount: '600', paymentMethod: 'CASH' })
  })

  it('shows the translated overpayment error from the backend', async () => {
    backend((url, init) => {
      if (url.endsWith('/orders/o1/payments') && init?.method === 'POST') {
        return { status: 409, body: { status: 409, code: 'OVERPAYMENT' } }
      }
      return { status: 0 }
    })
    renderApp('/orders/o1')

    await userEvent.click(await screen.findByRole('button', { name: 'Record payment' }))
    const amount = screen.getByLabelText('Amount, KZT')
    await userEvent.clear(amount)
    await userEvent.type(amount, '700')
    await userEvent.click(screen.getAllByRole('button', { name: 'Record payment' }).at(-1)!)

    expect(await screen.findByText('The payment is larger than the remaining balance.')).toBeInTheDocument()
  })

  it('requires a reason to void a payment', async () => {
    const fetchMock = backend((url, init) => {
      if (url.endsWith('/payments/p1/void') && init?.method === 'POST') {
        return { status: 200, body: { ...payment, voided: true } }
      }
      return { status: 0 }
    })
    renderApp('/orders/o1')

    await userEvent.click(await screen.findByRole('button', { name: 'Void' }))
    const confirm = screen.getAllByRole('button', { name: 'Void' }).at(-1)!
    expect(confirm).toBeDisabled()

    await userEvent.type(screen.getByLabelText('Reason'), 'Wrong amount')
    await userEvent.click(confirm)

    await waitFor(() => {
      const [call] = postsTo(fetchMock, '/payments/p1/void')
      expect(JSON.parse(String(call[1]?.body))).toEqual({ reason: 'Wrong amount' })
    })
  })
})
