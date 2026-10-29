import { zodResolver } from '@hookform/resolvers/zod'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Archive, ArchiveRestore, Pencil, Plus, Search, Tags } from 'lucide-react'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { z } from 'zod'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { FormField } from '@/components/form-field'
import { EmptyState, ListSkeleton, PageHeader, PaginationBar, TableFrame, Page } from '@/components/page-parts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { CURRENCIES } from '@/features/orders/api'
import { useWorkspace } from '@/features/workspace/workspace-context'
import { useDebouncedValue } from '@/hooks/use-debounced-value'
import { useErrorToast } from '@/hooks/use-api-toast'
import { formatMoney } from '@/lib/format'
import { catalogApi, type CatalogService } from './api'

const schema = z.object({
  name: z.string().trim().min(1, 'validation.required').max(150, 'validation.tooLong'),
  description: z.string().trim().max(1000, 'validation.tooLong'),
  unit: z.string().trim().min(1, 'validation.required').max(20, 'validation.tooLong'),
  price: z.string().trim().transform((v) => v.replace(',', '.').replace(/\s/g, ''))
    .pipe(z.string().regex(/^\d{1,12}(\.\d{1,2})?$/, 'validation.amount')),
  currency: z.enum(CURRENCIES),
})
type FormInput = z.input<typeof schema>
type FormValues = z.output<typeof schema>

function ServiceDialog({ open, onOpenChange, service }: {
  open: boolean
  onOpenChange: (open: boolean) => void
  service?: CatalogService
}) {
  const { t } = useTranslation()
  const { workspaceId } = useWorkspace()
  const queryClient = useQueryClient()
  const form = useForm<FormInput, unknown, FormValues>({
    resolver: zodResolver(schema),
    values: {
      name: service?.name ?? '',
      description: service?.description ?? '',
      unit: service?.unit ?? '',
      price: service ? String(service.price) : '',
      currency: (service?.currency as FormValues['currency']) ?? 'KZT',
    },
  })
  const save = useMutation({
    mutationFn: (v: FormValues) =>
      service ? catalogApi.update(workspaceId, service.id, v) : catalogApi.create(workspaceId, v),
    onSuccess: () => {
      toast.success(t(service ? 'services.saved' : 'services.created'))
      void queryClient.invalidateQueries({ queryKey: ['ws', workspaceId, 'services'] })
      onOpenChange(false)
    },
  })
  const { errors, isSubmitting } = form.formState
  const err = (m?: string) => (m ? t(m) : undefined)

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!next) save.reset(); onOpenChange(next) }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t(service ? 'services.editTitle' : 'services.new')}</DialogTitle>
        </DialogHeader>
        <form noValidate className="grid gap-4"
              onSubmit={form.handleSubmit(async (v) => { await save.mutateAsync(v).catch(() => undefined) })}>
          <ApiErrorAlert error={save.error} />
          <FormField label={t('services.name')} error={err(errors.name?.message)} {...form.register('name')} />
          <FormField label={`${t('services.description')} (${t('common.optional')})`}
                     error={err(errors.description?.message)} {...form.register('description')} />
          <div className="grid gap-4 sm:grid-cols-[1fr_1fr_7rem]">
            <FormField label={t('services.unit')} hint={t('services.unitHint')}
                       error={err(errors.unit?.message)} {...form.register('unit')} />
            <FormField label={t('services.price')} inputMode="decimal"
                       error={err(errors.price?.message)} {...form.register('price')} />
            <div className="grid content-start gap-2">
              <Label htmlFor="service-currency">{t('services.currency')}</Label>
              <Controller control={form.control} name="currency" render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="service-currency" className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {CURRENCIES.map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}
                  </SelectContent>
                </Select>
              )} />
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
            <Button type="submit" disabled={isSubmitting}>{t(service ? 'common.save' : 'common.create')}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export function ServicesPage() {
  const { t } = useTranslation()
  const { workspaceId, canManageRecords } = useWorkspace()
  const queryClient = useQueryClient()
  const showError = useErrorToast()
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<'ACTIVE' | 'ARCHIVED' | 'ALL'>('ACTIVE')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<CatalogService | 'new' | null>(null)
  const debounced = useDebouncedValue(search)

  const list = useQuery({
    queryKey: ['ws', workspaceId, 'services', debounced, status, page],
    queryFn: () => catalogApi.list(workspaceId, { search: debounced, status, page }),
    placeholderData: keepPreviousData,
  })
  const toggle = useMutation({
    mutationFn: (s: CatalogService) =>
      s.archived ? catalogApi.restore(workspaceId, s.id) : catalogApi.archive(workspaceId, s.id),
    onSuccess: (s) => {
      toast.success(t(s.archived ? 'services.archivedToast' : 'services.restoredToast'))
      void queryClient.invalidateQueries({ queryKey: ['ws', workspaceId, 'services'] })
    },
    onError: showError,
  })

  return (
    <Page>
      <PageHeader
        title={t('services.title')}
        actions={canManageRecords ? (
          <Button onClick={() => setEditing('new')}><Plus />{t('services.new')}</Button>
        ) : null}
      />
      <div className="mb-4 flex flex-wrap gap-2">
        <div className="relative min-w-56 flex-1">
          <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input type="search" className="pl-9" aria-label={t('common.search')} placeholder={t('common.search')}
                 value={search} onChange={(e) => { setSearch(e.target.value); setPage(0) }} />
        </div>
        <Select value={status} onValueChange={(v) => { setStatus(v as typeof status); setPage(0) }}>
          <SelectTrigger className="w-40" aria-label={t('orders.status')}><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="ACTIVE">{t('common.active')}</SelectItem>
            <SelectItem value="ARCHIVED">{t('common.archived')}</SelectItem>
            <SelectItem value="ALL">{t('common.all')}</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <ApiErrorAlert error={list.error} />
      {list.isPending ? <ListSkeleton /> : list.data?.totalItems === 0 ? (
        <EmptyState icon={Tags} title={debounced ? t('common.noResults') : t('services.empty')}
                    hint={debounced ? undefined : t('services.emptyHint')} />
      ) : list.data ? (
        <>
          <TableFrame>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('services.name')}</TableHead>
                  <TableHead className="hidden sm:table-cell">{t('services.unit')}</TableHead>
                  <TableHead className="text-right">{t('services.price')}</TableHead>
                  {canManageRecords ? <TableHead className="w-24"><span className="sr-only">{t('common.actions')}</span></TableHead> : null}
                </TableRow>
              </TableHeader>
              <TableBody>
                {list.data.items.map((s) => (
                  <TableRow key={s.id} className={s.archived ? 'text-muted-foreground' : undefined}>
                    <TableCell>
                      <span className="font-medium">{s.name}</span>
                      {s.archived ? <Badge variant="outline" className="ml-2">{t('common.archived')}</Badge> : null}
                      {s.description ? <p className="text-xs text-muted-foreground">{s.description}</p> : null}
                    </TableCell>
                    <TableCell className="hidden sm:table-cell">{s.unit}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatMoney(s.price, s.currency)}</TableCell>
                    {canManageRecords ? (
                      <TableCell>
                        <div className="flex justify-end gap-1">
                          <Button variant="ghost" size="icon-sm" aria-label={`${t('common.edit')}: ${s.name}`}
                                  onClick={() => setEditing(s)}><Pencil /></Button>
                          <Button variant="ghost" size="icon-sm"
                                  aria-label={`${t(s.archived ? 'common.restore' : 'common.archive')}: ${s.name}`}
                                  onClick={() => toggle.mutate(s)}>
                            {s.archived ? <ArchiveRestore /> : <Archive />}
                          </Button>
                        </div>
                      </TableCell>
                    ) : null}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableFrame>
          <PaginationBar page={page} totalPages={list.data.totalPages} onChange={setPage} />
        </>
      ) : null}

      <ServiceDialog open={editing !== null} onOpenChange={(o) => !o && setEditing(null)}
                     service={editing && editing !== 'new' ? editing : undefined} />
    </Page>
  )
}
