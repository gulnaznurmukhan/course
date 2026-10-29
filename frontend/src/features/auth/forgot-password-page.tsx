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
import { emailSchema, type EmailValues } from './schemas'

export function ForgotPasswordPage() {
  const { t } = useTranslation()
  const forgot = useMutation({ mutationFn: authApi.forgotPassword })

  const form = useForm<EmailValues>({
    resolver: zodResolver(emailSchema),
    defaultValues: { email: '' },
  })
  const { errors, isSubmitting } = form.formState

  const onSubmit = form.handleSubmit(async ({ email }) => {
    await forgot.mutateAsync(email).catch(() => undefined)
  })

  // The same message is shown whether or not the account exists
  if (forgot.isSuccess) {
    return (
      <AuthLayout title={t('auth.forgot.sentTitle')} description={t('auth.forgot.sentDescription')}>
        <Button asChild variant="outline" className="w-full">
          <Link to="/login">{t('common.backToLogin')}</Link>
        </Button>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title={t('auth.forgot.title')} description={t('auth.forgot.subtitle')}>
      <form onSubmit={onSubmit} noValidate className="grid gap-4">
        <ApiErrorAlert error={forgot.error} />
        <FormField
          label={t('common.email')}
          type="email"
          autoComplete="email"
          error={errors.email?.message && t(errors.email.message)}
          {...form.register('email')}
        />
        <Button type="submit" disabled={isSubmitting}>
          {t('auth.forgot.submit')}
        </Button>
        <Button asChild variant="ghost">
          <Link to="/login">{t('common.backToLogin')}</Link>
        </Button>
      </form>
    </AuthLayout>
  )
}
