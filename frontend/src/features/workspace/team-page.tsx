import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { MailPlus, Trash2, UserPlus, X } from 'lucide-react'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { z } from 'zod'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { ConfirmDialog } from '@/components/confirm-dialog'
import { FormField } from '@/components/form-field'
import { ListSkeleton, PageHeader, TableFrame, Page } from '@/components/page-parts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { WorkspaceRole } from '@/features/auth/api'
import { useCurrentUser } from '@/features/auth/use-auth'
import { useErrorToast } from '@/hooks/use-api-toast'
import { formatDateTime } from '@/lib/format'
import { workspaceApi } from './api'
import { useWorkspace } from './workspace-context'

const INVITABLE_ROLES = ['MANAGER', 'EMPLOYEE'] as const

const inviteSchema = z.object({
  email: z.string().trim().min(1, 'validation.required').pipe(z.email('validation.email')),
  role: z.enum(INVITABLE_ROLES),
})
type InviteValues = z.infer<typeof inviteSchema>

function InviteDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const { t } = useTranslation()
  const { workspaceId } = useWorkspace()
  const queryClient = useQueryClient()
  const form = useForm<InviteValues>({
    resolver: zodResolver(inviteSchema),
    defaultValues: { email: '', role: 'EMPLOYEE' },
  })
  const invite = useMutation({
    mutationFn: (v: InviteValues) => workspaceApi.invite(workspaceId, v.email, v.role),
    onSuccess: () => {
      toast.success(t('team.invited'))
      void queryClient.invalidateQueries({ queryKey: ['ws', workspaceId, 'members'] })
      form.reset()
      onOpenChange(false)
    },
  })
  const { errors, isSubmitting } = form.formState

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('team.inviteTitle')}</DialogTitle>
          <DialogDescription>{t('team.inviteHint')}</DialogDescription>
        </DialogHeader>
        <form
          noValidate
          className="grid gap-4"
          onSubmit={form.handleSubmit(async (v) => {
            await invite.mutateAsync(v).catch(() => undefined)
          })}
        >
          <ApiErrorAlert error={invite.error} />
          <FormField
            label={t('common.email')}
            type="email"
            error={errors.email?.message && t(errors.email.message)}
            {...form.register('email')}
          />
          <div className="grid gap-2">
            <Label htmlFor="invite-role">{t('team.role')}</Label>
            <Controller
              control={form.control}
              name="role"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="invite-role" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {INVITABLE_ROLES.map((r) => (
                      <SelectItem key={r} value={r}>
                        {t(`roles.${r}`)} — {t(`team.roleHelp.${r}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              <MailPlus />
              {t('team.invite')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export function TeamPage() {
  const { t } = useTranslation()
  const { workspaceId, canManageMembers } = useWorkspace()
  const { data: me } = useCurrentUser()
  const queryClient = useQueryClient()
  const showError = useErrorToast()
  const [inviting, setInviting] = useState(false)

  const data = useQuery({
    queryKey: ['ws', workspaceId, 'members'],
    queryFn: () => workspaceApi.members(workspaceId),
  })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['ws', workspaceId] })

  const changeRole = useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: WorkspaceRole }) =>
      workspaceApi.changeRole(workspaceId, userId, role),
    onSuccess: () => {
      toast.success(t('team.roleChanged'))
      void refresh()
    },
    onError: showError,
  })
  const remove = useMutation({
    mutationFn: (userId: string) => workspaceApi.removeMember(workspaceId, userId),
    onSuccess: () => {
      toast.success(t('team.removed'))
      void refresh()
    },
    onError: showError,
  })
  const cancelInvitation = useMutation({
    mutationFn: (id: string) => workspaceApi.cancelInvitation(workspaceId, id),
    onSuccess: () => {
      toast.success(t('team.invitationCancelled'))
      void refresh()
    },
    onError: showError,
  })

  return (
    <Page>
      <PageHeader
        title={t('team.title')}
        actions={
          canManageMembers ? (
            <Button onClick={() => setInviting(true)}>
              <UserPlus />
              {t('team.invite')}
            </Button>
          ) : null
        }
      />
      <ApiErrorAlert error={data.error} />
      {data.isPending ? <ListSkeleton rows={3} /> : null}

      {data.data ? (
        <div className="grid gap-8">
          <section>
            <h2 className="mb-3 text-lg font-semibold">{t('team.members')}</h2>
            <TableFrame>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('common.fullName')}</TableHead>
                    <TableHead className="hidden sm:table-cell">{t('common.email')}</TableHead>
                    <TableHead>{t('team.role')}</TableHead>
                    <TableHead className="hidden md:table-cell">{t('team.joined')}</TableHead>
                    {canManageMembers ? <TableHead className="w-12"><span className="sr-only">{t('common.actions')}</span></TableHead> : null}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.data.members.map((m) => {
                    const isMe = m.userId === me?.id
                    const editable = canManageMembers && !isMe && m.role !== 'OWNER'
                    return (
                      <TableRow key={m.userId}>
                        <TableCell className="font-medium">
                          {m.fullName}
                          {isMe ? <span className="ml-1 text-muted-foreground">({t('common.you')})</span> : null}
                        </TableCell>
                        <TableCell className="hidden sm:table-cell">{m.email}</TableCell>
                        <TableCell>
                          {editable ? (
                            <Select
                              value={m.role}
                              onValueChange={(role) => changeRole.mutate({ userId: m.userId, role: role as WorkspaceRole })}
                            >
                              <SelectTrigger size="sm" className="w-36" aria-label={`${t('team.role')}: ${m.fullName}`}>
                                <SelectValue />
                              </SelectTrigger>
                              <SelectContent>
                                {INVITABLE_ROLES.map((r) => (
                                  <SelectItem key={r} value={r}>
                                    {t(`roles.${r}`)}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          ) : (
                            <Badge variant="secondary">{t(`roles.${m.role}`)}</Badge>
                          )}
                        </TableCell>
                        <TableCell className="hidden text-muted-foreground md:table-cell">
                          {formatDateTime(m.joinedAt)}
                        </TableCell>
                        {canManageMembers ? (
                          <TableCell>
                            {editable ? (
                              <ConfirmDialog
                                trigger={
                                  <Button variant="ghost" size="icon-sm" aria-label={`${t('common.remove')}: ${m.fullName}`}>
                                    <Trash2 />
                                  </Button>
                                }
                                title={t('team.removeTitle', { name: m.fullName })}
                                description={t('team.removeText')}
                                confirmLabel={t('common.remove')}
                                destructive
                                onConfirm={() => remove.mutate(m.userId)}
                              />
                            ) : null}
                          </TableCell>
                        ) : null}
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </TableFrame>
          </section>

          {canManageMembers && data.data.invitations.length > 0 ? (
            <section>
              <h2 className="mb-3 text-lg font-semibold">{t('team.invitations')}</h2>
              <ul className="grid gap-2">
                {data.data.invitations.map((inv) => (
                  <li key={inv.id} className="flex flex-wrap items-center gap-3 rounded-lg border p-3">
                    <span className="flex-1 font-medium break-all">{inv.email}</span>
                    <Badge variant="secondary">{t(`roles.${inv.role}`)}</Badge>
                    <span className="text-sm text-muted-foreground">
                      {t('team.invitedAt')}: {formatDateTime(inv.createdAt)}
                    </span>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label={`${t('team.cancelInvitation')}: ${inv.email}`}
                      onClick={() => cancelInvitation.mutate(inv.id)}
                    >
                      <X />
                    </Button>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
        </div>
      ) : null}

      <InviteDialog open={inviting} onOpenChange={setInviting} />
    </Page>
  )
}
