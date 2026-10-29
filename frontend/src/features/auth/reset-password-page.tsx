import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { FormField } from '@/components/form-field'
import { Button } from '@/components/ui/button'
import { authApi } from './api'
import { AuthLayout } from './auth-layout'
import { resetPasswordSchema, type ResetPasswordValues } from './schemas'
import { useTokenFromUrl } from './use-token-from-url'

/** Opened from the link in the password reset email. */
export function ResetPasswordPage() {
  const { t } = useTranslation()
  const token = useTokenFromUrl()
  const reset = useMutation({
    mutationFn: (password: string) => authApi.resetPassword(token ?? '', password),
  })

  const form = useForm<ResetPasswordValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { newPassword: '', confirmPassword: '' },
  })
  const { errors, isSubmitting } = form.formState

  const onSubmit = form.handleSubmit(async ({ newPassword }) => {
    await reset.mutateAsync(newPassword).catch(() => undefined)
  })

  if (!token) {
    return (
      <AuthLayout title={t('auth.reset.title')} description={t('auth.reset.missingToken')}>
        <Button asChild className="w-full">
          <Link to="/forgot-password">{t('auth.forgot.submit')}</Link>
        </Button>
      </AuthLayout>
    )
  }

  if (reset.isSuccess) {
    return (
      <AuthLayout title={t('auth.reset.successTitle')} description={t('auth.reset.successDescription')}>
        <Button asChild className="w-full">
          <Link to="/login">{t('auth.verify.goToLogin')}</Link>
        </Button>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title={t('auth.reset.title')} description={t('auth.reset.subtitle')}>
      <form onSubmit={onSubmit} noValidate className="grid gap-4">
        <ApiErrorAlert error={reset.error} />
        <FormField
          label={t('auth.reset.newPassword')}
          type="password"
          autoComplete="new-password"
          hint={t('auth.register.passwordHint')}
          error={errors.newPassword?.message && t(errors.newPassword.message)}
          {...form.register('newPassword')}
        />
        <FormField
          label={t('auth.reset.confirmPassword')}
          type="password"
          autoComplete="new-password"
          error={errors.confirmPassword?.message && t(errors.confirmPassword.message)}
          {...form.register('confirmPassword')}
        />
        <Button type="submit" disabled={isSubmitting}>
          {t('auth.reset.submit')}
        </Button>
        <Button asChild variant="ghost">
          <Link to="/login">{t('common.backToLogin')}</Link>
        </Button>
      </form>
    </AuthLayout>
  )
}
