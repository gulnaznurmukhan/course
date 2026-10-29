import { toast } from 'sonner'

import { useErrorMessage } from '@/components/api-error-alert'

/** Shows a translated error toast for a failed request. */
export function useErrorToast() {
  const errorMessage = useErrorMessage()
  return (error: unknown) => toast.error(errorMessage(error))
}
