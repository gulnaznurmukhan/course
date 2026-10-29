/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Public API base URL. Never put secrets in VITE_ variables. */
  readonly VITE_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
