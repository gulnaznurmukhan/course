/**
 * Minimal fetch wrapper for the ClientFlow REST API.
 *
 * - Auth uses an HttpOnly session cookie (never localStorage).
 * - State-changing requests send the CSRF token from the XSRF-TOKEN cookie.
 * - Errors are thrown as ApiError with a stable code that the UI translates.
 */

export const API_URL = import.meta.env.VITE_API_URL ?? '/api/v1'

export interface FieldError {
  field: string
  code: string
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldError[]

  constructor(status: number, code: string, fieldErrors: FieldError[] = []) {
    super(code)
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((row) => row.startsWith(`${name}=`))
    ?.split('=')[1]
}

const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS']

/** Asks the backend to issue the XSRF-TOKEN cookie. */
async function refreshCsrfCookie() {
  await fetch(`${API_URL}/auth/csrf`, { credentials: 'include' })
}

async function errorCode(response: Response): Promise<string | undefined> {
  const body = await response.clone().json().catch(() => null)
  return body?.code
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const unsafe = !SAFE_METHODS.includes(method)

  const send = async () => {
    const headers = new Headers(init.headers)
    if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json')
    }
    if (unsafe) {
      if (!readCookie('XSRF-TOKEN')) await refreshCsrfCookie()
      const csrf = readCookie('XSRF-TOKEN')
      if (csrf) headers.set('X-XSRF-TOKEN', decodeURIComponent(csrf))
    }
    return fetch(`${API_URL}${path}`, { ...init, method, headers, credentials: 'include' })
  }

  let response: Response
  try {
    response = await send()
    // A missing or stale CSRF token: get a fresh one and retry once
    if (unsafe && response.status === 403 && (await errorCode(response)) === 'CSRF_INVALID') {
      await refreshCsrfCookie()
      response = await send()
    }
  } catch {
    throw new ApiError(0, 'NETWORK')
  }

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    const code = body?.code ?? (response.status === 401 ? 'UNAUTHORIZED' : 'INTERNAL_ERROR')
    throw new ApiError(response.status, code, body?.fieldErrors ?? [])
  }

  // 202/204 responses have no body
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}
