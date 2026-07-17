export const money = (value) => {
  const num = Number(value || 0);
  if (num === 0) return '$0.00';
  
  let decimals = 2;
  if (num < 0.01) {
    decimals = 8;
  } else if (num < 1) {
    decimals = 6;
  } else if (num < 10) {
    decimals = 4;
  }
  
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals
  }).format(num);
}

export const coin = (value) =>
  value == null ? '—' : Number(value).toFixed(8)
