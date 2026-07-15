import { INVALID_AMOUNT_MESSAGE, isPositiveQuantity } from './quantityInput'

export const PRICE_UNAVAILABLE_MESSAGE = 'Fiyat verisi kullanılamıyor.'
export const PRICE_STALE_MESSAGE = 'Fiyat verisi güncel değil.'
export const INSUFFICIENT_USD_MESSAGE = 'Bu işlem için yeterli USD bakiyeniz yok.'
export const INSUFFICIENT_ASSET_MESSAGE = 'Satış için yeterli varlık bakiyeniz yok.'

export function validateTrade({ quantity, side, symbol, livePrice, priceStatus, portfolio }) {
  if (!isPositiveQuantity(quantity)) return INVALID_AMOUNT_MESSAGE

  const price = Number(livePrice)
  if (priceStatus !== 'live' || !Number.isFinite(price) || price <= 0) {
    return priceStatus === 'stale' ? PRICE_STALE_MESSAGE : PRICE_UNAVAILABLE_MESSAGE
  }

  const amount = Number(quantity)
  if (side === 'BUY') {
    const usdBalance = Number(portfolio?.usdBalance)
    const total = amount * price
    if (!Number.isFinite(usdBalance) || !Number.isFinite(total) || total > usdBalance) {
      return INSUFFICIENT_USD_MESSAGE
    }
    return ''
  }

  const assetBalance = Number(portfolio?.assets?.find(asset => asset.symbol === symbol)?.quantity)
  if (!Number.isFinite(assetBalance) || amount > assetBalance) return INSUFFICIENT_ASSET_MESSAGE

  return ''
}
