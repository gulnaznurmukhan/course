import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { MailWarning } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate } from 'react-router'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { FormField } from '@/components/form-field'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { authApi } from './api'
import { AuthLayout } from './auth-layout'
import { loginSchema, type LoginValues } from './schemas'
import { useLogin } from './use-auth'

export function LoginPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const login = useLogin()
  const resend = useMutation({ mutationFn: authApi.resendVerification })

  const form = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  })
  const { errors, isSubmitting } = form.formState

  const onSubmit = form.handleSubmit(async (values) => {
    try {
      await login.mutateAsync(values)
      const from = (location.state as { from?: string } | null)?.from
      navigate(from && from !== '/login' ? from : '/', { replace: true })
    } catch {
      // shown below via login.error
    }
  })

  const notVerified = login.error instanceof ApiError && login.error.code === 'EMAIL_NOT_VERIFIED'

  const resendLink = async () => {
    const email = form.getValues('email').trim()
    await resend.mutateAsync(email).catch(() => undefined)
    navigate('/check-email', { state: { email } })
  }

  return (
    <AuthLayout title={t('auth.login.title')} description={t('auth.login.subtitle')}>
      <form onSubmit={onSubmit} noValidate className="grid gap-4">
        {notVerified ? (
          <Alert role="alert">
            <MailWarning />
            <AlertDescription className="grid gap-2">
              <span>{t('auth.notVerified.message')}</span>
              <Button type="button" variant="outline" size="sm" onClick={resendLink}
                      disabled={resend.isPending} className="justify-self-start">
                {t('auth.notVerified.resend')}
              </Button>
            </AlertDescription>
          </Alert>
        ) : (
          <ApiErrorAlert error={login.error} />
        )}

        <FormField
          label={t('common.email')}
          type="email"
          autoComplete="email"
          error={errors.email?.message && t(errors.email.message)}
          {...form.register('email')}
        />
        <FormField
          label={t('common.password')}
          type="password"
          autoComplete="current-password"
          error={errors.password?.message && t(errors.password.message)}
          {...form.register('password')}
        />

        <div className="flex justify-end">
          <Link to="/forgot-password" className="text-sm underline-offset-4 hover:underline">
            {t('auth.login.forgot')}
          </Link>
        </div>

        <Button type="submit" size="lg" disabled={isSubmitting} className="mt-1">
          {t('auth.login.submit')}
        </Button>

        <p className="text-center text-sm text-muted-foreground">
          {t('auth.login.noAccount')}{' '}
          <Link to="/register" className="font-medium text-foreground underline-offset-4 hover:underline">
            {t('auth.login.register')}
          </Link>
        </p>
      </form>
    </AuthLayout>
  )
}
