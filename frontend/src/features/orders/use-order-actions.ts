import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { useWorkspace } from '@/features/workspace/workspace-context'
import { useErrorToast } from '@/hooks/use-api-toast'
import { ordersApi, type Order, type OrderStatus } from './api'

/**
 * Status changes, archiving and restoring of orders.
 * After any change (or a conflict) the cached order lists are refreshed.
 */
export function useOrderActions() {
  const { t } = useTranslation()
  const { workspaceId } = useWorkspace()
  const queryClient = useQueryClient()
  const showError = useErrorToast()

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['ws', workspaceId, 'orders'] })
    void queryClient.invalidateQueries({ queryKey: ['ws', workspaceId, 'clients'] })
  }
  const onError = (error: unknown) => {
    showError(error)
    // e.g. STALE_VERSION: reload so the user sees the current state
    refresh()
  }

  const changeStatus = useMutation({
    mutationFn: ({ order, status }: { order: Order; status: OrderStatus }) =>
      ordersApi.changeStatus(workspaceId, order.id, status, order.version),
    onSuccess: () => {
      toast.success(t('orders.statusChanged'))
      refresh()
    },
    onError,
  })

  const archive = useMutation({
    mutationFn: (order: Order) => ordersApi.archive(workspaceId, order.id),
    onSuccess: () => {
      toast.success(t('orders.archivedToast'))
      refresh()
    },
    onError,
  })

  const restore = useMutation({
    mutationFn: (order: Order) => ordersApi.restore(workspaceId, order.id),
    onSuccess: () => {
      toast.success(t('orders.restoredToast'))
      refresh()
    },
    onError,
  })

  return { changeStatus, archive, restore, refresh }
}
