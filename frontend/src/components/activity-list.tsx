import type { CSSProperties } from 'react'
import { useTranslation } from 'react-i18next'

import { formatDateTime, formatMoney } from '@/lib/format'
import type { Activity } from '@/lib/types'

/** Turns an activity record into a translated sentence. */
function useDescribeActivity() {
  const { t } = useTranslation()
  return (item: Activity) => {
    const m = item.metadata as Record<string, string | string[]>
    switch (item.action) {
      case 'ORDER_STATUS_CHANGED':
        return t('activity.ORDER_STATUS_CHANGED', {
          from: t(`orderStatus.${m.from}`),
          to: t(`orderStatus.${m.to}`),
        })
      case 'PAYMENT_RECORDED':
      case 'PAYMENT_VOIDED':
        return t(`activity.${item.action}`, {
          amount: formatMoney(String(m.amount), String(m.currency)),
          reason: m.reason,
        })
      case 'MEMBER_ROLE_CHANGED':
        return t('activity.MEMBER_ROLE_CHANGED', { from: t(`roles.${m.from}`), to: t(`roles.${m.to}`) })
      case 'ORDER_UPDATED': {
        const fields = Array.isArray(m.changedFields) ? m.changedFields : []
        return t('activity.ORDER_UPDATED', {
          fields: fields.map((f) => t(`activity.fields.${f}`, { defaultValue: f })).join(', '),
        })
      }
      default:
        return t(`activity.${item.action}`, { ...m, defaultValue: t('activity.unknown') })
    }
  }
}

export function ActivityList({ items }: { items: Activity[] }) {
  const { t } = useTranslation()
  const describe = useDescribeActivity()
  if (items.length === 0) return <p className="text-sm text-muted-foreground">{t('activity.empty')}</p>

  return (
    <ol className="cf-stagger relative grid gap-4 border-l border-border pl-5">
      {items.map((item, i) => (
        <li key={item.id} className="relative" style={{ '--cf-i': i } as CSSProperties}>
          <span className="absolute top-1.5 -left-[25px] size-2.5 rounded-full bg-primary ring-4 ring-card" aria-hidden />
          <p className="text-sm text-pretty">
            <span className="font-medium">{item.actor?.fullName ?? t('common.deletedUser')}</span>{' '}
            {describe(item)}
          </p>
          <time className="text-xs text-muted-foreground" dateTime={item.createdAt}>
            {formatDateTime(item.createdAt)}
          </time>
        </li>
      ))}
    </ol>
  )
}
