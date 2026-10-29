import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { FormField } from '@/components/form-field'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { orderExtrasApi, type Requisites } from '@/features/orders/extras-api'
import { useWorkspace } from '@/features/workspace/workspace-context'

const FIELDS: (keyof Requisites)[] = ['legalName', 'bin', 'address', 'phone', 'email', 'bankName', 'iban', 'bik']

/** Company requisites printed on invoices. Editable by the OWNER only (checked by the server). */
export function RequisitesSection() {
  const { t } = useTranslation()
  const { workspaceId, canManageMembers } = useWorkspace()
  const [draft, setDraft] = useState<Requisites | null>(null)
  const current = useQuery({
    queryKey: ['ws', workspaceId, 'requisites'],
    queryFn: () => orderExtrasApi.requisites(workspaceId),
  })
  const values = draft ?? current.data
  const save = useMutation({
    mutationFn: (r: Requisites) => orderExtrasApi.updateRequisites(workspaceId, r),
    onSuccess: (saved) => {
      setDraft(saved)
      toast.success(t('requisites.saved'))
    },
  })
  if (!values) return null

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('requisites.title')}</CardTitle>
        <CardDescription>{t('requisites.hint')}</CardDescription>
      </CardHeader>
      <CardContent>
        <form
          className="grid gap-4 sm:grid-cols-2"
          onSubmit={(e) => {
            e.preventDefault()
            save.mutate(values)
          }}
        >
          <div className="sm:col-span-2"><ApiErrorAlert error={save.error} /></div>
          {FIELDS.map((field) => (
            <FormField
              key={field}
              name={`requisites-${field}`}
              label={t(`requisites.${field}`)}
              value={values[field] ?? ''}
              readOnly={!canManageMembers}
              inputMode={field === 'bin' ? 'numeric' : undefined}
              maxLength={field === 'bin' ? 12 : field === 'address' ? 300 : 200}
              onChange={(e) => setDraft({ ...values, [field]: e.target.value || null })}
            />
          ))}
          {canManageMembers ? (
            <Button type="submit" className="justify-self-start sm:col-span-2" disabled={save.isPending}>
              {t('common.save')}
            </Button>
          ) : null}
        </form>
      </CardContent>
    </Card>
  )
}
