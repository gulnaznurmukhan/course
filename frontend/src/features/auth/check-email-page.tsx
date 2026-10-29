import { useMutation } from '@tanstack/react-query'
import { CheckCircle2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, Navigate, useLocation } from 'react-router'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { authApi } from './api'
import { AuthLayout } from './auth-layout'

/** Shown after registration: explains that a confirmation email was sent. */
export function CheckEmailPage() {
  const { t } = useTranslation()
  const email = (useLocation().state as { email?: string } | null)?.email
  const resend = useMutation({ mutationFn: authApi.resendVerification })

  if (!email) return <Navigate to="/login" replace />

  return (
    <AuthLayout title={t('auth.checkEmail.title')}>
      <div className="grid gap-4 text-sm">
        <p>{t('auth.checkEmail.description', { email })}</p>
        <p className="text-muted-foreground">{t('auth.checkEmail.spamHint')}</p>

        {resend.isSuccess ? (
          <Alert role="status">
            <CheckCircle2 />
            <AlertDescription>{t('auth.checkEmail.resent')}</AlertDescription>
          </Alert>
        ) : null}
        <ApiErrorAlert error={resend.error} />

        <Button variant="outline" onClick={() => resend.mutate(email)} disabled={resend.isPending}>
          {t('auth.checkEmail.resend')}
        </Button>
        <Button asChild variant="ghost">
          <Link to="/login">{t('common.backToLogin')}</Link>
        </Button>
      </div>
    </AuthLayout>
  )
}
