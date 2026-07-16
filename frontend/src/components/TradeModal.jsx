import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api } from '../api/client'
import { normalizeQuantityInput } from '../features/trades/quantityInput'
import { validateTrade } from '../features/trades/tradeValidation'
import { money } from '../utils/format'

export default function TradeModal({ symbol, side: initialSide = 'BUY', livePrice, priceStatus, portfolio, onClose, onComplete }) {
  const { t } = useTranslation()
  const [side, setSide] = useState(initialSide)
  const [quantity, setQuantity] = useState('')
  const [busy, setBusy] = useState(false)
  const [requestError, setRequestError] = useState('')

  const contextKey = `${symbol}|${side}|${quantity}|${livePrice}|${priceStatus}`
  const latestContextRef = useRef(contextKey)
  latestContextRef.current = contextKey
  const validationError = validateTrade({ quantity, side, symbol, livePrice, priceStatus, portfolio })
  const numericPrice = Number(livePrice)
  const hasFreshPrice = priceStatus === 'live' && Number.isFinite(numericPrice) && numericPrice > 0

  const displayedError = requestError || (quantity !== '' && validationError ? t(validationError) : '')
  const unavailablePriceMessage = priceStatus === 'stale' ? 'trade.priceStale' : 'trade.priceUnavailable'

  useEffect(() => {
    setRequestError('')
  }, [symbol, livePrice, priceStatus])

  const numericQuantity = parseFloat(quantity) || 0
  const estimatedTotal = numericQuantity * numericPrice

  const usdBalance = Number(portfolio?.usdBalance || 0)
  const assetQuantity = Number(portfolio?.assets?.find(a => a.symbol === symbol)?.quantity || 0)

  function changeQuantity(event) {
    const nextValue = normalizeQuantityInput(event.target.value)
    if (nextValue !== null) {
      setRequestError('')
      setQuantity(nextValue)
    }
  }

  function changeSide(nextSide) {
    setRequestError('')
    setSide(nextSide)
  }

  async function submit(event) {
    event.preventDefault()
    setRequestError('')

    if (validationError) {
      setRequestError(t(validationError))
      return
    }

    const submittedContext = contextKey
    setBusy(true)
    try {
      await api('/trades', { method: 'POST', body: JSON.stringify({ symbol, side, quantity }) })
      await onComplete()
      onClose()
    } catch (requestErrorValue) {
      if (latestContextRef.current === submittedContext) {
        setRequestError(requestErrorValue.message)
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm" onMouseDown={event => event.target === event.currentTarget && onClose()}>
      <div role="dialog" aria-modal="true" aria-label={t('trade.symbolTransaction', { symbol })} className="card w-full max-w-md rounded-3xl p-7 animate-in">
        <div className="flex items-start justify-between">
          <div>
            <p className="label">{t('trade.newOrder')}</p>
            <h2 className="mt-1 text-3xl font-black">{t('trade.symbolTransaction', { symbol })}</h2>
          </div>
          <button type="button" aria-label="Close" onClick={onClose} className="text-2xl text-slate-400 hover:text-white transition-colors">×</button>
        </div>

        {/* BUY / SELL tabs */}
        <div className="mt-5 grid grid-cols-2 rounded-xl bg-[#081522] p-1">
          <button
            type="button"
            onClick={() => changeSide('BUY')}
            className={`rounded-lg py-3 font-bold transition-all ${side === 'BUY' ? 'bg-[#1fc8a4] text-[#06140f]' : 'text-slate-400'} hover:opacity-90`}
          >
            {t('trade.buy')}
          </button>
          <button
            type="button"
            onClick={() => changeSide('SELL')}
            className={`rounded-lg py-3 font-bold transition-all ${side === 'SELL' ? 'bg-rose-400 text-[#19080d]' : 'text-slate-400'} hover:opacity-90`}
          >
            {t('trade.sell')}
          </button>
        </div>

        <form onSubmit={submit} className="mt-6">
          <label htmlFor="trade-quantity" className="text-sm text-slate-300">{t('trade.coinQuantity')}</label>
          <div className="mt-2 text-sm text-slate-400">
            {hasFreshPrice ? `${t('trade.livePrice')}: ${money(numericPrice)}` : t(unavailablePriceMessage)}
          </div>
          <div className="relative mt-2">
            <input
              id="trade-quantity"
              autoFocus
              autoComplete="off"
              className="input pr-20"
              type="text"
              inputMode="decimal"
              value={quantity}
              onChange={changeQuantity}
              placeholder="0.01000000"
              aria-describedby="trade-quantity-help"
            />
          </div>

          {/* Dynamic estimated total */}
          {numericQuantity > 0 && (
            <div className="mt-3 flex items-center justify-between rounded-xl bg-[#0c1a2a] px-4 py-3">
              <span className="text-xs text-slate-500">{t('trade.estimatedTotal')}</span>
              <span className={`font-bold ${side === 'BUY' ? 'text-[#1fc8a4]' : 'text-rose-400'}`}>{money(estimatedTotal)}</span>
            </div>
          )}

          {/* Balance details */}
          <div className="mt-3 text-xs text-slate-500">
            {side === 'BUY' ? (
              <span>{t('trade.available')} <span className="text-slate-300">{money(usdBalance)}</span></span>
            ) : (
              <span>{t('trade.owned')} <span className="text-slate-300">{assetQuantity} {symbol}</span></span>
            )}
          </div>

          {displayedError && <p role="alert" className="mt-4 rounded-xl bg-red-500/10 p-3 text-sm text-red-300">{displayedError}</p>}
          
          <button disabled={busy || Boolean(validationError)} className="btn btn-primary mt-6 w-full disabled:opacity-50 disabled:cursor-not-allowed">
            {busy ? t('trade.processingOrder') : t('trade.executeOrder')}
          </button>
        </form>
      </div>
    </div>
  )
}
