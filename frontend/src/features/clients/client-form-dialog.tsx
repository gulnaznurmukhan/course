import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { TriangleAlert } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { FormField } from '@/components/form-field'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { useWorkspace } from '@/features/workspace/workspace-context'
import { clientsApi, type Client, type DuplicateClient } from './api'

const optionalText = (max: number) => z.string().trim().max(max, 'validation.tooLong')

const clientSchema = z.object({
  name: z.string().trim().min(1, 'validation.required').max(150, 'validation.tooLong'),
  email: z.union([z.literal(''), z.string().trim().pipe(z.email('validation.email').max(254, 'validation.tooLong'))]),
  phone: optionalText(30).regex(/^[0-9+()\-\s]*$/, 'validation.phone'),
  company: optionalText(150),
  address: optionalText(300),
  notes: optionalText(5000),
})
type ClientValues = z.infer<typeof clientSchema>

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** When given, the dialog edits this client; otherwise it creates a new one. */
  client?: Client
  onSaved?: (client: Client) => void
}

export function ClientFormDialog({ open, onOpenChange, client, onSaved }: Props) {
  const { t } = useTranslation()
  const { workspaceId } = useWorkspace()
  const queryClient = useQueryClient()
  const [duplicates, setDuplicates] = useState<DuplicateClient[]>([])

  const form = useForm<ClientValues>({
    resolver: zodResolver(clientSchema),
    values: {
      name: client?.name ?? '',
      email: client?.email ?? '',
      phone: client?.phone ?? '',
      company: client?.company ?? '',
      address: client?.address ?? '',
      notes: client?.notes ?? '',
    },
  })
  const { errors, isSubmitting } = form.formState
  const err = (message?: string) => (message ? t(message) : undefined)

  const save = useMutation({
    mutationFn: (values: ClientValues) =>
      client ? clientsApi.update(workspaceId, client.id, values) : clientsApi.create(workspaceId, values),
    onSuccess: (saved) => {
      void queryClient.invalidateQueries({ queryKey: ['ws', workspaceId, 'clients'] })
      toast.success(t(client ? 'clients.saved' : 'clients.created'))
      onOpenChange(false)
      onSaved?.(saved)
    },
  })

  /** Warns (but does not block) when the email or phone is already used by another client. */
  const checkDuplicates = async () => {
    const { email, phone } = form.getValues()
    if (!email.trim() && phone.replace(/\D/g, '').length < 6) {
      setDuplicates([])
      return
    }
    try {
      setDuplicates(await clientsApi.duplicates(workspaceId, email.trim(), phone.trim(), client?.id))
    } catch {
      setDuplicates([])
    }
  }

  const onSubmit = form.handleSubmit(async (values) => {
    await save.mutateAsync(values).catch(() => undefined)
  })

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) {
          save.reset()
          setDuplicates([])
        }
        onOpenChange(next)
      }}
    >
      <DialogContent className="max-h-[90svh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{t(client ? 'clients.editTitle' : 'clients.new')}</DialogTitle>
        </DialogHeader>
        <form onSubmit={onSubmit} noValidate className="grid gap-4">
          <ApiErrorAlert error={save.error} />
          <FormField label={t('clients.name')} error={err(errors.name?.message)} {...form.register('name')} />
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField
              label={t('clients.email')}
              type="email"
              error={err(errors.email?.message)}
              {...form.register('email', { onBlur: checkDuplicates })}
            />
            <FormField
              label={t('clients.phone')}
              type="tel"
              error={err(errors.phone?.message)}
              {...form.register('phone', { onBlur: checkDuplicates })}
            />
          </div>

          {duplicates.length > 0 ? (
            <Alert role="status">
              <TriangleAlert />
              <AlertTitle>{t('clients.duplicatesTitle')}</AlertTitle>
              <AlertDescription>
                <p>{t('clients.duplicatesText')}</p>
                <ul className="mt-1 list-disc pl-4">
                  {duplicates.map((d) => (
                    <li key={d.id}>
                      <Link to={`/clients/${d.id}`} className="underline" onClick={() => onOpenChange(false)}>
                        {d.name}
                      </Link>{' '}
                      {[d.email, d.phone].filter(Boolean).join(', ')}
                      {d.archived ? ` (${t('common.archived')})` : ''}
                    </li>
                  ))}
                </ul>
              </AlertDescription>
            </Alert>
          ) : null}

          <FormField label={t('clients.company')} error={err(errors.company?.message)} {...form.register('company')} />
          <FormField label={t('clients.address')} error={err(errors.address?.message)} {...form.register('address')} />
          <div className="grid gap-2">
            <Label htmlFor="client-notes">{t('clients.notes')}</Label>
            <Textarea id="client-notes" rows={3} aria-invalid={errors.notes ? true : undefined} {...form.register('notes')} />
            {errors.notes?.message ? <p className="text-sm text-destructive">{t(errors.notes.message)}</p> : null}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {t(client ? 'common.save' : 'common.create')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
