export const MAX_DECIMAL_PLACES = 8

export const INVALID_AMOUNT_MESSAGE = 'Quantity must be positive with at most 8 decimal places.'

export function normalizeQuantityInput(rawValue) {
  const normalized = rawValue.replace(/,/g, '.')

  if (normalized === '') return ''
  // Allow up to MAX_DECIMAL_PLACES decimal digits
  if (!/^\d*(?:\.\d{0,8})?$/.test(normalized)) return null
  if (normalized.startsWith('.')) return `0${normalized}`

  return normalized
}

export function isPositiveQuantity(value) {
  if (!value) return false
  const number = Number(value)
  return Number.isFinite(number) && number > 0
}
