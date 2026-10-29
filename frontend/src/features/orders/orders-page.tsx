import { KanbanSquare, List, Plus, Search } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'

import { PageHeader, Page } from '@/components/page-parts'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useWorkspace } from '@/features/workspace/workspace-context'
import { useDebouncedValue } from '@/hooks/use-debounced-value'
import type { PaymentState } from '@/features/payments/api'
import { ORDER_STATUSES, type OrderListParams, type OrderStatus } from './api'
import { OrderBoard } from './order-board'
import { OrderFormDialog } from './order-form-dialog'
import { OrdersTable } from './orders-table'

const ANY = 'any'
const VIEW_KEY = 'cf_orders_view'
const SORTS = ['CREATED_AT', 'UPDATED_AT', 'DEADLINE', 'TOTAL_AMOUNT', 'ORDER_NUMBER'] as const

export function OrdersPage() {
  const { t } = useTranslation()
  const { canManageRecords } = useWorkspace()
  const [view, setView] = useState(() => localStorage.getItem(VIEW_KEY) ?? 'table')
  const [searchParams] = useSearchParams()
  const urlSearch = searchParams.get('search') ?? ''
  const [search, setSearch] = useState(urlSearch)
  const [lastUrlSearch, setLastUrlSearch] = useState(urlSearch)
  // A new search from the header replaces the current text
  if (urlSearch !== lastUrlSearch) {
    setLastUrlSearch(urlSearch)
    setSearch(urlSearch)
  }
  const [status, setStatus] = useState<string>(ANY)
  const [archive, setArchive] = useState<NonNullable<OrderListParams['archive']>>('ACTIVE')
  const [overdue, setOverdue] = useState(false)
  const [paymentState, setPaymentState] = useState<string>(ANY)
  const [sort, setSort] = useState<(typeof SORTS)[number]>('CREATED_AT')
  const [creating, setCreating] = useState(false)
  const debouncedSearch = useDebouncedValue(search)

  const filters: OrderListParams = {
    search: debouncedSearch,
    status: status === ANY ? undefined : (status as OrderStatus),
    archive,
    overdue: overdue || undefined,
    paymentState: paymentState === ANY ? undefined : (paymentState as PaymentState),
    sort,
    direction: sort === 'DEADLINE' || sort === 'ORDER_NUMBER' ? 'ASC' : 'DESC',
  }

  return (
    <Page>
      <PageHeader
        title={t('orders.title')}
        actions={
          canManageRecords ? (
            <Button onClick={() => setCreating(true)}>
              <Plus />
              {t('orders.new')}
            </Button>
          ) : null
        }
      />

      <Tabs
        value={view}
        onValueChange={(v) => {
          setView(v)
          localStorage.setItem(VIEW_KEY, v)
        }}
      >
        <div className="mb-4 flex flex-wrap items-center gap-2">
          <TabsList>
            <TabsTrigger value="table">
              <List />
              {t('orders.table')}
            </TabsTrigger>
            <TabsTrigger value="board">
              <KanbanSquare />
              {t('orders.board')}
            </TabsTrigger>
          </TabsList>
          <div className="relative min-w-56 flex-1">
            <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
            <Input
              type="search"
              name="search"
              autoComplete="off"
              className="pl-9"
              placeholder={t('orders.searchPlaceholder')}
              aria-label={t('common.search')}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
        </div>

        <TabsContent value="table">
          <div className="mb-4 flex flex-wrap items-center gap-2">
            <Select value={status} onValueChange={setStatus}>
              <SelectTrigger className="w-44" aria-label={t('orders.status')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ANY}>{t('orders.anyStatus')}</SelectItem>
                {ORDER_STATUSES.map((s) => (
                  <SelectItem key={s} value={s}>
                    {t(`orderStatus.${s}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={paymentState} onValueChange={setPaymentState}>
              <SelectTrigger className="w-48" aria-label={t('payments.title')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ANY}>{t('payments.anyState')}</SelectItem>
                {(['UNPAID', 'PARTIALLY_PAID', 'PAID'] as const).map((s) => (
                  <SelectItem key={s} value={s}>
                    {t(`paymentState.${s}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={archive} onValueChange={(v) => setArchive(v as typeof archive)}>
              <SelectTrigger className="w-36" aria-label={t('common.archived')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ACTIVE">{t('common.active')}</SelectItem>
                <SelectItem value="ARCHIVED">{t('common.archived')}</SelectItem>
                <SelectItem value="ALL">{t('common.all')}</SelectItem>
              </SelectContent>
            </Select>
            <Select value={sort} onValueChange={(v) => setSort(v as typeof sort)}>
              <SelectTrigger className="w-48" aria-label={t('common.sortBy')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {SORTS.map((s) => (
                  <SelectItem key={s} value={s}>
                    {t(`orders.sort.${s}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <div className="flex items-center gap-2">
              <input
                id="only-overdue"
                type="checkbox"
                className="size-4 accent-primary"
                checked={overdue}
                onChange={(e) => setOverdue(e.target.checked)}
              />
              <Label htmlFor="only-overdue">{t('orders.onlyOverdue')}</Label>
            </div>
          </div>
          <OrdersTable filters={filters} />
        </TabsContent>

        <TabsContent value="board">
          <OrderBoard search={debouncedSearch} />
        </TabsContent>
      </Tabs>

      <OrderFormDialog open={creating} onOpenChange={setCreating} />
    </Page>
  )
}
