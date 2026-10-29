import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

export function PageHeader({ title, description, actions }: {
  title: string
  description?: ReactNode
  actions?: ReactNode
}) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div className="min-w-0">
        <h1 className="text-2xl font-semibold tracking-tight sm:text-[1.75rem]">{title}</h1>
        {description ? <div className="mt-1 text-sm text-muted-foreground">{description}</div> : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </div>
  )
}

/** Wraps a page's content so it rises in on navigation. */
export function Page({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn('cf-page', className)}>{children}</div>
}

/**
 * Container for a data table: rounded, ringed, and the table scrolls inside it
 * horizontally when the columns do not fit (never the page).
 */
export function TableFrame({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={cn('min-w-0 overflow-hidden rounded-xl bg-card shadow-[var(--shadow-card)] ring-1 ring-foreground/10', className)}>
      {children}
    </div>
  )
}

export function EmptyState({ icon: Icon, title, hint, action }: {
  icon: LucideIcon
  title: string
  hint?: string
  action?: ReactNode
}) {
  return (
    <div className="cf-page flex flex-col items-center justify-center rounded-xl border border-dashed border-foreground/15 px-6 py-14 text-center">
      <span className="mb-4 flex size-12 items-center justify-center rounded-full bg-accent text-accent-foreground">
        <Icon className="size-5" aria-hidden />
      </span>
      <p className="font-medium">{title}</p>
      {hint ? <p className="mt-1 max-w-sm text-sm text-pretty text-muted-foreground">{hint}</p> : null}
      {action ? <div className="mt-5">{action}</div> : null}
    </div>
  )
}

export function ListSkeleton({ rows = 5 }: { rows?: number }) {
  const { t } = useTranslation()
  return (
    <div className="grid gap-2" role="status" aria-label={t('common.loading')}>
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton key={i} className="cf-skeleton h-11 w-full rounded-lg" style={{ opacity: 1 - i * 0.12 }} />
      ))}
    </div>
  )
}

export function PaginationBar({ page, totalPages, onChange }: {
  page: number
  totalPages: number
  onChange: (page: number) => void
}) {
  const { t } = useTranslation()
  if (totalPages <= 1) return null
  return (
    <nav className="mt-4 flex items-center justify-between gap-2" aria-label="pagination">
      <Button variant="outline" size="sm" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        {t('common.previous')}
      </Button>
      <span className="text-sm text-muted-foreground tabular-nums">
        {t('common.pageOf', { page: page + 1, total: totalPages })}
      </span>
      <Button variant="outline" size="sm" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>
        {t('common.next')}
      </Button>
    </nav>
  )
}

/** Read-only label/value pair for detail pages. */
export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-1">
      <dt className="text-[11px] font-medium tracking-wider text-muted-foreground uppercase">{label}</dt>
      <dd className="text-sm break-words">{children || '—'}</dd>
    </div>
  )
}
