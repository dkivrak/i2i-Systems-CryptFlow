export const money = (value) =>
  new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })
    .format(Number(value || 0))

export const coin = (value) =>
  value == null ? '—' : Number(value).toFixed(8)
