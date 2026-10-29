import { useTranslation } from 'react-i18next'

import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import type { PaymentState } from './api'

/** Payment state is semantic: tinted chip (critical / warning / good) with readable text. */
const STYLES: Record<PaymentState, string> = {
  UNPAID: 'border-destructive/20 bg-destructive/10 text-destructive',
  PARTIALLY_PAID: 'border-warning/30 bg-warning/15 text-foreground',
  PAID: 'border-success/25 bg-success/12 text-success',
}

export function PaymentStateBadge({ state }: { state: PaymentState }) {
  const { t } = useTranslation()
  return (
    <Badge variant="outline" className={cn(STYLES[state])}>
      {t(`paymentState.${state}`)}
    </Badge>
  )
}
