import { z } from 'zod'

// Messages are i18n keys; the form components translate them.
const email = z.string().trim().min(1, 'validation.required').pipe(z.email('validation.email').max(254, 'validation.tooLong'))

const newPassword = z
  .string()
  .min(8, 'validation.passwordMin')
  .max(128, 'validation.passwordMax')

export const loginSchema = z.object({
  email,
  password: z.string().min(1, 'validation.required').max(128, 'validation.passwordMax'),
})

export const registerSchema = z.object({
  fullName: z.string().trim().min(1, 'validation.required').max(100, 'validation.tooLong'),
  email,
  password: newPassword,
})

export const emailSchema = z.object({ email })

export const resetPasswordSchema = z
  .object({
    newPassword,
    confirmPassword: z.string().min(1, 'validation.required'),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: 'validation.passwordsMismatch',
    path: ['confirmPassword'],
  })

export type LoginValues = z.infer<typeof loginSchema>
export type RegisterValues = z.infer<typeof registerSchema>
export type EmailValues = z.infer<typeof emailSchema>
export type ResetPasswordValues = z.infer<typeof resetPasswordSchema>
