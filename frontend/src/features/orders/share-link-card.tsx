import { useMutation, useQuery } from '@tanstack/react-query'
import { Copy, Link2, Link2Off } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { ConfirmDialog } from '@/components/confirm-dialog'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { useWorkspace } from '@/features/workspace/workspace-context'
import { useErrorToast } from '@/hooks/use-api-toast'
import { formatDate } from '@/lib/format'
import { orderExtrasApi } from './extras-api'

/** Customer link of an order (OWNER / MANAGER). The URL is shown only right after creation. */
export function ShareLinkCard({ orderId }: { orderId: string }) {
  const { t } = useTranslation()
  const { workspaceId } = useWorkspace()
  const showError = useErrorToast()
  const [createdUrl, setCreatedUrl] = useState<string | null>(null)

  const status = useQuery({
    queryKey: ['ws', workspaceId, 'orders', 'share', orderId],
    queryFn: () => orderExtrasApi.shareStatus(workspaceId, orderId),
  })
  const create = useMutation({
    mutationFn: () => orderExtrasApi.createShare(workspaceId, orderId),
    onSuccess: (link) => {
      setCreatedUrl(link.url)
      void status.refetch()
    },
    onError: showError,
  })
  const revoke = useMutation({
    mutationFn: () => orderExtrasApi.revokeShare(workspaceId, orderId),
    onSuccess: () => {
      setCreatedUrl(null)
      toast.success(t('share.revoked'))
      void status.refetch()
    },
    onError: showError,
  })

  const copy = async () => {
    if (!createdUrl) return
    await navigator.clipboard.writeText(createdUrl).catch(() => undefined)
    toast.success(t('share.copied'))
  }

  const active = status.data?.active
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><Link2 className="size-4" aria-hidden />{t('share.title')}</CardTitle>
        <CardDescription>{t('share.hint')}</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3">
        {createdUrl ? (
          <Alert>
            <AlertDescription className="grid gap-2">
              <span>{t('share.onlyOnce')}</span>
              <div className="flex gap-2">
                <Input readOnly value={createdUrl} aria-label={t('share.title')} onFocus={(e) => e.target.select()} />
                <Button size="sm" onClick={copy}><Copy />{t('share.copy')}</Button>
              </div>
            </AlertDescription>
          </Alert>
        ) : null}
        <p className="text-sm text-muted-foreground">
          {active && status.data?.expiresAt
            ? t('share.active', { date: formatDate(status.data.expiresAt.slice(0, 10)) })
            : t('share.inactive')}
        </p>
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant={active ? 'outline' : 'default'} disabled={create.isPending}
                  title={active ? t('share.recreateHint') : undefined} onClick={() => create.mutate()}>
            <Link2 />{t(active ? 'share.recreate' : 'share.create')}
          </Button>
          {active ? (
            <ConfirmDialog
              trigger={<Button size="sm" variant="ghost"><Link2Off />{t('share.revoke')}</Button>}
              title={`${t('share.revoke')}?`}
              description={t('share.hint')}
              confirmLabel={t('share.revoke')}
              destructive
              onConfirm={() => revoke.mutate()}
            />
          ) : null}
        </div>
      </CardContent>
    </Card>
  )
}
