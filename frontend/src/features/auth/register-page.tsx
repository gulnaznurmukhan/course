import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { FormField } from '@/components/form-field'
import { Button } from '@/components/ui/button'
import { authApi } from './api'
import { AuthLayout } from './auth-layout'
import { registerSchema, type RegisterValues } from './schemas'

export function RegisterPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const register = useMutation({ mutationFn: authApi.register })

  const form = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: '', email: '', password: '' },
  })
  const { errors, isSubmitting } = form.formState

  const onSubmit = form.handleSubmit(async (values) => {
    try {
      await register.mutateAsync({ ...values, language: i18n.resolvedLanguage ?? 'ru' })
      navigate('/check-email', { state: { email: values.email } })
    } catch {
      // shown below via register.error
    }
  })

  return (
    <AuthLayout title={t('auth.register.title')} description={t('auth.register.subtitle')}>
      <form onSubmit={onSubmit} noValidate className="grid gap-4">
        <ApiErrorAlert error={register.error} />

        <FormField
          label={t('common.fullName')}
          autoComplete="name"
          error={errors.fullName?.message && t(errors.fullName.message)}
          {...form.register('fullName')}
        />
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
          autoComplete="new-password"
          hint={t('auth.register.passwordHint')}
          error={errors.password?.message && t(errors.password.message)}
          {...form.register('password')}
        />

        <Button type="submit" size="lg" disabled={isSubmitting} className="mt-1">
          {t('auth.register.submit')}
        </Button>

        <p className="text-center text-sm text-muted-foreground">
          {t('auth.register.haveAccount')}{' '}
          <Link to="/login" className="font-medium text-foreground underline-offset-4 hover:underline">
            {t('auth.register.login')}
          </Link>
        </p>
      </form>
    </AuthLayout>
  )
}
