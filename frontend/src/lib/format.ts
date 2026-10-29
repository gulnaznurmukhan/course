import i18n from '@/i18n'

/** "Aigerim Bekova" -> "AB" (avatar placeholder). */
export function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')
}

/** Intl locale for the current interface language. */
function locale() {
  const lng = i18n.resolvedLanguage ?? 'ru'
  return lng === 'kk' ? 'kk-KZ' : lng === 'en' ? 'en-US' : 'ru-RU'
}

/**
 * Money comes from the API as a JSON number with at most 2 decimals.
 * It is only displayed here; all calculations happen on the backend (BigDecimal).
 */
export function formatMoney(amount: number | string, currency: string) {
  const value = Number(amount)
  // narrowSymbol: "₸" in every language (ru-RU would otherwise print "KZT");
  // whole amounts drop the ",00", amounts with tiyn/cents keep both digits
  return new Intl.NumberFormat(locale(), {
    style: 'currency',
    currency,
    currencyDisplay: 'narrowSymbol',
    minimumFractionDigits: Number.isInteger(value) ? 0 : 2,
    maximumFractionDigits: 2,
  }).format(value)
}

/** "2026-11-05" (a business date without time) -> localized date. */
export function formatDate(isoDate: string) {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Intl.DateTimeFormat(locale(), { dateStyle: 'medium' }).format(new Date(year, month - 1, day))
}

export function formatDateTime(isoInstant: string) {
  return new Intl.DateTimeFormat(locale(), { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(isoInstant),
  )
}

/** Today's date as yyyy-mm-dd in the browser's time zone (for date inputs). */
export function todayIso() {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}
