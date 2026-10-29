import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Printer } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { FullPageLoader } from '@/components/full-page-loader'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { useWorkspace } from '@/features/workspace/workspace-context'
import { formatDate, formatMoney } from '@/lib/format'
import { orderExtrasApi } from './extras-api'

/**
 * Printable invoice. "Print / Save as PDF" uses the browser's print dialog,
 * so the PDF looks exactly like the page. All amounts come from the server.
 */
export function InvoicePage() {
  const { t } = useTranslation()
  const { orderId = '' } = useParams()
  const { workspaceId } = useWorkspace()
  const invoice = useQuery({
    queryKey: ['ws', workspaceId, 'orders', 'invoice', orderId],
    queryFn: () => orderExtrasApi.invoice(workspaceId, orderId),
  })

  if (invoice.isPending) return <FullPageLoader />
  if (!invoice.data) return <div className="p-6"><ApiErrorAlert error={invoice.error} /></div>
  const inv = invoice.data
  const s = inv.seller
  const money = (v: number) => formatMoney(v, inv.currency)

  return (
    <div className="min-h-svh bg-muted/40 print:bg-white">
      <div className="mx-auto flex max-w-4xl flex-wrap items-center justify-between gap-2 p-4 print:hidden">
        <Button asChild variant="ghost" size="sm">
          <Link to={`/orders/${orderId}`}><ArrowLeft />{t('invoice.back')}</Link>
        </Button>
        <Button onClick={() => window.print()}><Printer />{t('invoice.print')}</Button>
      </div>
      {!s.legalName || !s.bin || !s.iban ? (
        <div className="mx-auto max-w-4xl px-4 print:hidden">
          <Alert><AlertDescription>{t('invoice.noRequisites')}</AlertDescription></Alert>
        </div>
      ) : null}

      <article className="mx-auto my-4 max-w-4xl bg-white p-8 text-sm text-black shadow-sm print:my-0 print:max-w-none print:p-0 print:shadow-none">
        <table className="mb-6 w-full border border-black text-xs">
          <tbody>
            <tr>
              <td className="border border-black p-2" colSpan={2}>
                <p className="text-[10px] text-zinc-600">{t('invoice.seller')}</p>
                <p className="font-semibold">{s.legalName ?? inv.workspaceName}</p>
                {s.bin ? <p>{t('invoice.bin')}: {s.bin}</p> : null}
              </td>
              <td className="border border-black p-2">
                <p className="text-[10px] text-zinc-600">{t('invoice.iban')}</p>
                <p className="font-mono">{s.iban ?? '—'}</p>
              </td>
            </tr>
            <tr>
              <td className="border border-black p-2" colSpan={2}>
                <p className="text-[10px] text-zinc-600">{t('invoice.bank')}</p>
                <p>{s.bankName ?? '—'}</p>
              </td>
              <td className="border border-black p-2">
                <p className="text-[10px] text-zinc-600">{t('invoice.bik')}</p>
                <p className="font-mono">{s.bik ?? '—'}</p>
              </td>
            </tr>
          </tbody>
        </table>

        <h1 className="mb-4 border-b-2 border-black pb-2 text-xl font-bold">
          {t('invoice.title', { number: inv.number })} {t('invoice.dated', { date: formatDate(inv.issueDate) })}
        </h1>

        <dl className="mb-6 grid grid-cols-[8rem_1fr] gap-x-4 gap-y-2">
          <dt className="text-zinc-600">{t('invoice.seller')}:</dt>
          <dd>
            {s.legalName ?? inv.workspaceName}
            {s.bin ? `, ${t('invoice.bin')} ${s.bin}` : ''}
            {s.address ? `, ${s.address}` : ''}
            {s.phone ? `, ${t('invoice.phone')} ${s.phone}` : ''}
          </dd>
          <dt className="text-zinc-600">{t('invoice.buyer')}:</dt>
          <dd>
            {[inv.buyer.company, inv.buyer.name].filter(Boolean).join(', ')}
            {inv.buyer.address ? `, ${inv.buyer.address}` : ''}
            {inv.buyer.phone ? `, ${t('invoice.phone')} ${inv.buyer.phone}` : ''}
          </dd>
        </dl>

        <table className="w-full border-collapse text-xs">
          <thead>
            <tr className="bg-zinc-100">
              <th className="border border-black p-1.5 text-left">{t('invoice.no')}</th>
              <th className="border border-black p-1.5 text-left">{t('invoice.name')}</th>
              <th className="border border-black p-1.5 text-right">{t('invoice.qty')}</th>
              <th className="border border-black p-1.5 text-left">{t('invoice.unit')}</th>
              <th className="border border-black p-1.5 text-right">{t('invoice.price')}</th>
              <th className="border border-black p-1.5 text-right">{t('invoice.amount')}</th>
            </tr>
          </thead>
          <tbody>
            {inv.lines.map((l) => (
              <tr key={l.number}>
                <td className="border border-black p-1.5">{l.number}</td>
                <td className="border border-black p-1.5">{l.name}</td>
                <td className="border border-black p-1.5 text-right tabular-nums">{l.quantity}</td>
                <td className="border border-black p-1.5">{l.unit}</td>
                <td className="border border-black p-1.5 text-right tabular-nums">{money(l.unitPrice)}</td>
                <td className="border border-black p-1.5 text-right tabular-nums">{money(l.lineTotal)}</td>
              </tr>
            ))}
          </tbody>
        </table>

        <dl className="mt-4 ml-auto grid w-72 grid-cols-2 gap-y-1 text-right">
          <dt className="font-semibold">{t('invoice.total')}:</dt>
          <dd className="font-semibold tabular-nums">{money(inv.totalAmount)}</dd>
          {inv.paidAmount > 0 ? (
            <>
              <dt>{t('invoice.paid')}:</dt>
              <dd className="tabular-nums">{money(inv.paidAmount)}</dd>
            </>
          ) : null}
          <dt className="text-base font-bold">{t('invoice.due')}:</dt>
          <dd className="text-base font-bold tabular-nums">{money(inv.balance)}</dd>
        </dl>
        {inv.dueDate ? <p className="mt-4">{t('invoice.payBy', { date: formatDate(inv.dueDate) })}</p> : null}

        <p className="mt-16 border-t border-black pt-1 text-xs text-zinc-600 sm:w-64">{t('invoice.signature')}</p>
      </article>
    </div>
  )
}
