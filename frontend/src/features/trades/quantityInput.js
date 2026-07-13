export const INVALID_AMOUNT_MESSAGE = 'Miktar pozitif ve en fazla 8 ondalıklı olmalıdır.'

export function normalizeQuantityInput(rawValue) {
  const normalized = rawValue.replace(/,/g, '.')

  if (normalized === '') return ''
  if (!/^\d*(?:\.\d{0,8})?$/.test(normalized)) return null
  if (normalized.startsWith('.')) return `0${normalized}`

  return normalized
}

export function isPositiveQuantity(value) {
  if (!value) return false
  const number = Number(value)
  return Number.isFinite(number) && number > 0
}
