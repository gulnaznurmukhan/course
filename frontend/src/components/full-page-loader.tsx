import { Loader2, RotateCw } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { LogoMark } from '@/components/logo'
import { Button } from '@/components/ui/button'

export function FullPageLoader() {
  const { t } = useTranslation()
  return (
    <div className="flex min-h-svh items-center justify-center" role="status">
      <Loader2 className="size-6 animate-spin text-muted-foreground" aria-hidden />
      <span className="sr-only">{t('common.loading')}</span>
    </div>
  )
}

/** Shown when the session check fails for a reason other than "signed out" (server down, 5xx). */
export function FullPageError({ onRetry, retrying }: { onRetry: () => void; retrying: boolean }) {
  const { t } = useTranslation()
  return (
    <div className="flex min-h-svh items-center justify-center px-4" role="alert">
      <div className="cf-page flex max-w-sm flex-col items-center text-center">
        <LogoMark className="size-12" />
        <h1 className="mt-5 text-xl font-semibold tracking-tight">{t('common.serverDownTitle')}</h1>
        <p className="mt-2 text-sm text-muted-foreground">{t('common.serverDownText')}</p>
        <Button className="mt-6" onClick={onRetry} disabled={retrying}>
          <RotateCw className={retrying ? 'animate-spin' : undefined} aria-hidden />
          {t('common.retry')}
        </Button>
      </div>
    </div>
  )
}
