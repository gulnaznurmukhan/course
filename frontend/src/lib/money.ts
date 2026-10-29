/**
 * Exact decimal arithmetic for previews in forms (no floating point).
 * The server recalculates every amount with BigDecimal; these helpers only
 * show the user what the server will compute.
 */

/** "12.5" -> 1250n (hundredths). Returns null for invalid input. */
export function toHundredths(value: string): bigint | null {
  const text = value.trim().replace(',', '.').replace(/\s/g, '')
  const match = /^(\d{1,12})(?:\.(\d{1,2}))?$/.exec(text)
  if (!match) return null
  return BigInt(match[1]) * 100n + BigInt((match[2] ?? '').padEnd(2, '0'))
}

/** quantity * price rounded half-up to hundredths (both given in hundredths). */
export function multiplyHalfUp(quantity: bigint, price: bigint): bigint {
  const product = quantity * price // in 1/10000
  return (product + 50n) / 100n
}

/** 1250n -> "12.50" */
export function formatHundredths(value: bigint): string {
  const whole = value / 100n
  const cents = (value % 100n).toString().padStart(2, '0')
  return `${whole}.${cents}`
}
