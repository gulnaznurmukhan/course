import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Laptop, LogOut, Trash2, Upload } from 'lucide-react'
import { useRef, useState, type ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { FormField } from '@/components/form-field'
import { PageHeader, Page } from '@/components/page-parts'
import { LanguageSwitcher, ThemeSwitcher } from '@/components/preference-switchers'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { ME_QUERY_KEY, useCurrentUser } from '@/features/auth/use-auth'
import { useWorkspace } from '@/features/workspace/workspace-context'
import { useErrorToast } from '@/hooks/use-api-toast'
import { API_URL } from '@/lib/api'
import { formatDateTime, initials } from '@/lib/format'
import { settingsApi, type NotificationSettings } from './api'
import { resetPreferencesSync } from './use-preferences'
import { RequisitesSection } from './requisites-section'

/** "Mozilla/5.0 (Windows NT 10.0…) Chrome/131…" -> "Chrome · Windows" */
function describeDevice(ua: string | null): string | null {
  if (!ua) return null
  const browser = /Edg\//.test(ua) ? 'Edge' : /OPR\//.test(ua) ? 'Opera' : /Firefox\//.test(ua) ? 'Firefox'
    : /Chrome\//.test(ua) ? 'Chrome' : /Safari\//.test(ua) ? 'Safari' : null
  const os = /Windows/.test(ua) ? 'Windows' : /iPhone|iPad/.test(ua) ? 'iOS' : /Android/.test(ua) ? 'Android'
    : /Mac OS/.test(ua) ? 'macOS' : /Linux/.test(ua) ? 'Linux' : null
  return [browser, os].filter(Boolean).join(' · ') || null
}

function Section({ title, description, children }: { title: string; description?: string; children: ReactNode }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        {description ? <CardDescription>{description}</CardDescription> : null}
      </CardHeader>
      <CardContent className="grid gap-4">{children}</CardContent>
    </Card>
  )
}

function ProfileSection() {
  const { t } = useTranslation()
  const { data: me } = useCurrentUser()
  const queryClient = useQueryClient()
  const showError = useErrorToast()
  const fileInput = useRef<HTMLInputElement>(null)
  const [name, setName] = useState(me?.fullName ?? '')

  const refreshMe = () => queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY })
  const saveName = useMutation({
    mutationFn: () => settingsApi.updateProfile({ fullName: name.trim() }),
    onSuccess: (updated) => {
      queryClient.setQueryData(ME_QUERY_KEY, updated)
      toast.success(t('settings.saved'))
    },
    onError: showError,
  })
  const upload = useMutation({ mutationFn: settingsApi.uploadAvatar, onSuccess: refreshMe, onError: showError })
  const removeAvatar = useMutation({ mutationFn: settingsApi.deleteAvatar, onSuccess: refreshMe, onError: showError })

  if (!me) return null
  return (
    <Section title={t('settings.profile')} description={t('settings.profileHint')}>
      <div className="flex flex-wrap items-center gap-4">
        <Avatar className="size-16">
          {me.avatarUpdatedAt ? (
            <AvatarImage src={`${API_URL}/users/me/avatar?v=${encodeURIComponent(me.avatarUpdatedAt)}`} alt="" />
          ) : null}
          <AvatarFallback className="text-lg">{initials(me.fullName)}</AvatarFallback>
        </Avatar>
        <div className="grid gap-2">
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" size="sm" onClick={() => fileInput.current?.click()} disabled={upload.isPending}>
              <Upload />
              {t('settings.upload')}
            </Button>
            {me.avatarUpdatedAt ? (
              <Button variant="ghost" size="sm" onClick={() => removeAvatar.mutate()}>
                {t('settings.removeAvatar')}
              </Button>
            ) : null}
          </div>
          <p className="text-xs text-muted-foreground">{t('settings.avatarHint')}</p>
          <input
            ref={fileInput}
            type="file"
            accept="image/png,image/jpeg,image/webp"
            className="hidden"
            aria-label={t('settings.avatar')}
            onChange={(e) => {
              const file = e.target.files?.[0]
              if (file) upload.mutate(file)
              e.target.value = ''
            }}
          />
        </div>
      </div>
      <form
        className="flex flex-wrap items-end gap-2"
        onSubmit={(e) => {
          e.preventDefault()
          if (name.trim()) saveName.mutate()
        }}
      >
        <div className="min-w-60 flex-1">
          <FormField name="fullName" label={t('common.fullName')} value={name} maxLength={100}
                     onChange={(e) => setName(e.target.value)} />
        </div>
        <Button type="submit" disabled={!name.trim() || name.trim() === me.fullName || saveName.isPending}>
          {t('common.save')}
        </Button>
      </form>
      <p className="text-sm text-muted-foreground">{me.email}</p>
    </Section>
  )
}

function NotificationsSection() {
  const { t } = useTranslation()
  const { data: me } = useCurrentUser()
  const queryClient = useQueryClient()
  const showError = useErrorToast()
  const save = useMutation({
    mutationFn: settingsApi.updateNotifications,
    onSuccess: (updated) => queryClient.setQueryData(ME_QUERY_KEY, updated),
    onError: showError,
  })
  if (!me) return null
  const keys: (keyof NotificationSettings)[] = ['deadlines', 'overdue', 'orderChanges', 'payments']

  return (
    <Section title={t('settings.notifications')} description={t('settings.notificationsHint')}>
      <div className="grid gap-3">
        {keys.map((key) => (
          <div key={key} className="flex items-center gap-3">
            <input
              id={`notify-${key}`}
              type="checkbox"
              className="size-4 accent-primary"
              checked={me.notifications[key]}
              onChange={(e) => save.mutate({ ...me.notifications, [key]: e.target.checked })}
            />
            <Label htmlFor={`notify-${key}`}>{t(`settings.notify.${key}`)}</Label>
          </div>
        ))}
      </div>
    </Section>
  )
}

function WorkspaceSection() {
  const { t } = useTranslation()
  const { workspaceId, workspaceName, canManageMembers } = useWorkspace()
  const queryClient = useQueryClient()
  const showError = useErrorToast()
  const [name, setName] = useState(workspaceName)
  const rename = useMutation({
    mutationFn: () => settingsApi.renameWorkspace(workspaceId, name.trim()),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY })
      toast.success(t('settings.saved'))
    },
    onError: showError,
  })
  if (!canManageMembers) return null
  return (
    <Section title={t('settings.workspace')} description={t('settings.workspaceHint')}>
      <form
        className="flex flex-wrap items-end gap-2"
        onSubmit={(e) => {
          e.preventDefault()
          if (name.trim()) rename.mutate()
        }}
      >
        <div className="min-w-60 flex-1">
          <FormField name="workspaceName" label={t('settings.workspaceName')} value={name} maxLength={100}
                     onChange={(e) => setName(e.target.value)} />
        </div>
        <Button type="submit" disabled={!name.trim() || name.trim() === workspaceName || rename.isPending}>
          {t('common.save')}
        </Button>
      </form>
    </Section>
  )
}

const passwordSchema = z.object({
  currentPassword: z.string().min(1, 'validation.required'),
  newPassword: z.string().min(8, 'validation.passwordMin').max(128, 'validation.passwordMax'),
})

function SecuritySection() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const showError = useErrorToast()
  const form = useForm<z.infer<typeof passwordSchema>>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { currentPassword: '', newPassword: '' },
  })
  const change = useMutation({
    mutationFn: (v: z.infer<typeof passwordSchema>) => settingsApi.changePassword(v.currentPassword, v.newPassword),
    onSuccess: () => {
      form.reset()
      toast.success(t('settings.passwordChanged'))
      void queryClient.invalidateQueries({ queryKey: ['sessions'] })
    },
  })
  const sessions = useQuery({ queryKey: ['sessions'], queryFn: settingsApi.sessions })
  const revoke = useMutation({
    mutationFn: settingsApi.revokeSession,
    onSuccess: () => {
      toast.success(t('settings.sessionRevoked'))
      void sessions.refetch()
    },
    onError: showError,
  })
  const revokeOthers = useMutation({
    mutationFn: settingsApi.revokeOtherSessions,
    onSuccess: () => void sessions.refetch(),
    onError: showError,
  })
  const { errors } = form.formState

  return (
    <>
      <Section title={t('settings.security')}>
        <form
          noValidate
          className="grid max-w-md gap-4"
          onSubmit={form.handleSubmit(async (v) => {
            await change.mutateAsync(v).catch(() => undefined)
          })}
        >
          <ApiErrorAlert error={change.error} />
          <FormField label={t('settings.currentPassword')} type="password" autoComplete="current-password"
                     error={errors.currentPassword?.message && t(errors.currentPassword.message)}
                     {...form.register('currentPassword')} />
          <FormField label={t('settings.newPassword')} type="password" autoComplete="new-password"
                     hint={t('auth.register.passwordHint')}
                     error={errors.newPassword?.message && t(errors.newPassword.message)}
                     {...form.register('newPassword')} />
          <Button type="submit" className="justify-self-start" disabled={change.isPending}>
            {t('settings.changePassword')}
          </Button>
        </form>
      </Section>

      <Section title={t('settings.sessions')} description={t('settings.sessionsHint')}>
        <ul className="grid gap-2">
          {sessions.data?.map((s) => (
            <li key={s.key} className="flex flex-wrap items-center gap-3 rounded-md border p-3">
              <Laptop className="size-5 text-muted-foreground" aria-hidden />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium" title={s.userAgent ?? undefined}>
                  {describeDevice(s.userAgent) ?? t('settings.unknownDevice')}
                </p>
                <p className="text-xs text-muted-foreground">
                  {s.ipAddress} · {t('settings.lastActive', { date: formatDateTime(s.lastAccessedAt) })}
                </p>
              </div>
              {s.current ? (
                <span className="text-xs font-medium text-emerald-700 dark:text-emerald-400">{t('settings.thisDevice')}</span>
              ) : (
                <Button variant="ghost" size="sm" onClick={() => revoke.mutate(s.key)}>
                  <LogOut />
                  {t('settings.signOut')}
                </Button>
              )}
            </li>
          ))}
        </ul>
        {sessions.data && sessions.data.length > 1 ? (
          <Button variant="outline" className="justify-self-start" onClick={() => revokeOthers.mutate()}>
            {t('settings.signOutOthers')}
          </Button>
        ) : null}
      </Section>
    </>
  )
}

function DangerSection() {
  const { t } = useTranslation()
  const { data: me } = useCurrentUser()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [understood, setUnderstood] = useState(false)
  const remove = useMutation({
    mutationFn: () => settingsApi.deleteAccount(password),
    onSuccess: () => {
      resetPreferencesSync()
      queryClient.clear()
      queryClient.setQueryData(ME_QUERY_KEY, null)
      localStorage.removeItem('cf_workspace')
      navigate('/login', { replace: true })
    },
  })
  if (!me) return null
  const owned = me.workspaces.filter((w) => w.role === 'OWNER')
  const memberOf = me.workspaces.filter((w) => w.role !== 'OWNER')

  return (
    <Card className="border-destructive/50">
      <CardHeader>
        <CardTitle className="text-destructive">{t('settings.danger')}</CardTitle>
        <CardDescription>{t('settings.dangerHint')}</CardDescription>
      </CardHeader>
      <CardContent>
        <Dialog onOpenChange={(open) => {
          if (!open) {
            setPassword('')
            setUnderstood(false)
            remove.reset()
          }
        }}>
          <DialogTrigger asChild>
            <Button variant="destructive">
              <Trash2 />
              {t('settings.deleteForever')}
            </Button>
          </DialogTrigger>
          <DialogContent className="sm:max-w-md">
            <DialogHeader>
              <DialogTitle>{t('settings.danger')}</DialogTitle>
              <DialogDescription>{t('settings.dangerHint')}</DialogDescription>
            </DialogHeader>
            <form
              className="grid gap-4"
              onSubmit={(e) => {
                e.preventDefault()
                if (password && understood) remove.mutate()
              }}
            >
              <ApiErrorAlert error={remove.error} />
              {owned.length > 0 ? (
                <div className="text-sm">
                  <p>{t('settings.willDelete')}</p>
                  <ul className="mt-1 list-disc pl-5 font-medium">
                    {owned.map((w) => <li key={w.id}>{w.name}</li>)}
                  </ul>
                </div>
              ) : null}
              {memberOf.length > 0 ? <p className="text-sm">{t('settings.otherMemberships')}</p> : null}
              <FormField name="confirm-password" label={t('settings.confirmPassword')} type="password"
                         autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
              <div className="flex items-center gap-2">
                <input id="understand" type="checkbox" className="size-4 accent-destructive" checked={understood}
                       onChange={(e) => setUnderstood(e.target.checked)} />
                <Label htmlFor="understand">{t('settings.understand')}</Label>
              </div>
              <DialogFooter>
                <Button type="submit" variant="destructive" disabled={!password || !understood || remove.isPending}>
                  {t('settings.deleteForever')}
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
      </CardContent>
    </Card>
  )
}

export function SettingsPage() {
  const { t } = useTranslation()
  return (
    <Page className="mx-auto grid max-w-3xl gap-6">
      <PageHeader title={t('settings.title')} />
      <ProfileSection />
      <Section title={t('settings.preferences')} description={t('settings.preferencesHint')}>
        <div className="flex flex-wrap items-center gap-4">
          <LanguageSwitcher />
          <ThemeSwitcher />
        </div>
      </Section>
      <NotificationsSection />
      <WorkspaceSection />
      <RequisitesSection />
      <SecuritySection />
      <DangerSection />
    </Page>
  )
}
