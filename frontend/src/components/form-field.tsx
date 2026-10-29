import type { ComponentProps } from 'react'

import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

interface FormFieldProps extends ComponentProps<'input'> {
  name: string
  label: string
  /** Already translated error text. */
  error?: string
  hint?: string
}

/** Label + input + error/hint, connected with aria attributes for screen readers. */
export function FormField({ name, label, error, hint, id, type, ...inputProps }: FormFieldProps) {
  const fieldId = id ?? name
  const messageId = `${fieldId}-message`
  const noSpell = type === 'email' || type === 'password' || type === 'tel'
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={fieldId}>{label}</Label>
      <Input
        id={fieldId}
        name={name}
        type={type}
        spellCheck={noSpell ? false : undefined}
        aria-invalid={error ? true : undefined}
        aria-describedby={error || hint ? messageId : undefined}
        {...inputProps}
      />
      {error ? (
        <p id={messageId} className="cf-page text-sm text-destructive" aria-live="polite">
          {error}
        </p>
      ) : hint ? (
        <p id={messageId} className="text-sm text-muted-foreground">
          {hint}
        </p>
      ) : null}
    </div>
  )
}
