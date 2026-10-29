import { useState, type CSSProperties } from 'react'
import { useTranslation } from 'react-i18next'

import { ORDER_STATUSES, type OrderStatus } from '@/features/orders/api'

/**
 * Horizontal bars: number of orders per status (one series, so one hue and no legend).
 * Marks follow the data-viz spec: <= 24px thick, 4px rounded end, square at the
 * baseline, value at the tip in text color, hover/focus tooltip with the share.
 * Bars grow from the baseline on mount. The list itself is the accessible table view.
 */
export function StatusBarChart({ counts }: { counts: Record<OrderStatus, number> }) {
  const { t } = useTranslation()
  const [active, setActive] = useState<OrderStatus | null>(null)
  const total = ORDER_STATUSES.reduce((sum, s) => sum + (counts[s] ?? 0), 0)
  const max = Math.max(1, ...ORDER_STATUSES.map((s) => counts[s] ?? 0))

  return (
    <ul className="grid gap-3">
      {ORDER_STATUSES.map((status, i) => {
        const value = counts[status] ?? 0
        const percent = total === 0 ? 0 : Math.round((value / total) * 100)
        const share = t('dashboard.share', { value, total, percent })
        return (
          <li
            key={status}
            className="grid grid-cols-[7.5rem_1fr] items-center gap-3 text-sm sm:grid-cols-[9rem_1fr]"
          >
            <span className="truncate text-muted-foreground">{t(`orderStatus.${status}`)}</span>
            <div
              className="relative flex h-8 items-center gap-2 rounded-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
              tabIndex={0}
              aria-label={`${t(`orderStatus.${status}`)}: ${share}`}
              onMouseEnter={() => setActive(status)}
              onMouseLeave={() => setActive(null)}
              onFocus={() => setActive(status)}
              onBlur={() => setActive(null)}
            >
              {value > 0 ? (
                <div
                  className="cf-grow-x h-5 rounded-r-[4px] bg-[var(--viz-series-1)] transition-opacity duration-150"
                  style={{ width: `${(value / max) * 85}%`, opacity: active && active !== status ? 0.45 : 1, '--cf-i': i } as CSSProperties}
                />
              ) : null}
              <span className="font-medium text-foreground tabular-nums">{value}</span>
              {active === status ? (
                <span
                  role="tooltip"
                  className="pointer-events-none absolute -top-9 left-0 z-10 rounded-md border bg-popover px-2 py-1 text-xs whitespace-nowrap text-popover-foreground shadow-[var(--shadow-raised)]"
                >
                  {t(`orderStatus.${status}`)} — {share}
                </span>
              ) : null}
            </div>
          </li>
        )
      })}
    </ul>
  )
}
