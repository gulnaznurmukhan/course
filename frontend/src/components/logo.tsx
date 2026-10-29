import { cn } from '@/lib/utils'

/** Brand mark from the project's logo file (symbol only, transparent background). */
export function LogoMark({ className }: { className?: string }) {
  return (
    <img
      src="/logo-mark.png"
      alt=""
      width={512}
      height={512}
      decoding="async"
      className={cn('size-7 shrink-0 select-none', className)}
      draggable={false}
    />
  )
}

export function Logo({ className }: { className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-2', className)} translate="no">
      <LogoMark />
      <span className="text-base font-semibold tracking-tight">
        Client<span className="text-primary">Flow</span>
      </span>
    </span>
  )
}
