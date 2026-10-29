import { AlertCircle } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { ApiError } from '@/lib/api'

/** Translates the stable error code returned by the backend. */
export function useErrorMessage() {
  const { t } = useTranslation()
  return (error: unknown) => {
    const code = error instanceof ApiError ? error.code : 'INTERNAL_ERROR'
    return t(`errors.${code}`, { defaultValue: t('errors.INTERNAL_ERROR') })
  }
}

export function ApiErrorAlert({ error }: { error: unknown }) {
  const errorMessage = useErrorMessage()
  if (!error) return null
  return (
    <Alert variant="destructive" role="alert">
      <AlertCircle />
      <AlertDescription>{errorMessage(error)}</AlertDescription>
    </Alert>
  )
}
