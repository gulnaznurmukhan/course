import { useMutation } from '@tanstack/react-query'
import { CheckCircle2, Loader2, XCircle } from 'lucide-react'
import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { Button } from '@/components/ui/button'
import { authApi } from './api'
import { AuthLayout } from './auth-layout'
import { useTokenFromUrl } from './use-token-from-url'

/** Opened from the link in the verification email. */
export function VerifyEmailPage() {
  const { t } = useTranslation()
  const token = useTokenFromUrl()
  const verify = useMutation({ mutationFn: authApi.verifyEmail })
  const started = useRef(false)

  useEffect(() => {
    // The token is single-use: send it exactly once (React StrictMode runs effects twice in development)
    if (token && !started.current) {
      started.current = true
      verify.mutate(token)
    }
  }, [token, verify])

  if (token && (verify.isPending || verify.isIdle)) {
    return (
      <AuthLayout title={t('auth.verify.verifying')}>
        <div className="flex justify-center py-4" role="status">
          <Loader2 className="size-6 animate-spin text-muted-foreground" aria-hidden />
        </div>
      </AuthLayout>
    )
  }

  const success = verify.isSuccess
  const Icon = success ? CheckCircle2 : XCircle
  return (
    <AuthLayout
      title={t(success ? 'auth.verify.successTitle' : 'auth.verify.failedTitle')}
      description={t(success ? 'auth.verify.successDescription' : 'auth.verify.failedDescription')}
    >
      <div className="grid gap-4">
        <Icon className={success ? 'mx-auto size-10 text-green-600' : 'mx-auto size-10 text-destructive'} aria-hidden />
        <Button asChild>
          <Link to="/login">{t('auth.verify.goToLogin')}</Link>
        </Button>
      </div>
    </AuthLayout>
  )
}
