import { Loader2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'

export function FullPageLoader() {
  const { t } = useTranslation()
  return (
    <div className="flex min-h-svh items-center justify-center" role="status">
      <Loader2 className="size-6 animate-spin text-muted-foreground" aria-hidden />
      <span className="sr-only">{t('common.loading')}</span>
    </div>
  )
}
