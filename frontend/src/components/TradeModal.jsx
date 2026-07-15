import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api } from '../api/client'
import { INVALID_AMOUNT_MESSAGE, isPositiveQuantity, normalizeQuantityInput } from '../features/trades/quantityInput'
import { money } from '../utils/format'

export default function TradeModal({ symbol, lockedPrice, usdBalance, assetQuantity, onClose, onComplete }) {
  const { t } = useTranslation()
  const canBuy = usdBalance > 0
  const canSell = assetQuantity > 0
  const [side, setSide] = useState(canBuy ? 'BUY' : canSell ? 'SELL' : 'BUY')
  const [quantity, setQuantity] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const numericQuantity = parseFloat(quantity) || 0
  const estimatedTotal = numericQuantity * lockedPrice

  function changeQuantity(event) {
    const nextValue = normalizeQuantityInput(event.target.value)
    if (nextValue !== null) setQuantity(nextValue)
  }

  async function submit(event) {
    event.preventDefault()
    setError('')

    if (!isPositiveQuantity(quantity)) {
      setError(INVALID_AMOUNT_MESSAGE)
      return
    }

    if (side === 'BUY' && estimatedTotal > usdBalance) {
      setError(t('trade.insufficientFunds', { amount: money(usdBalance) }))
      return
    }

    if (side === 'SELL' && numericQuantity > assetQuantity) {
      setError(t('trade.insufficientAsset', { quantity: assetQuantity, symbol }))
      return
    }

    setBusy(true)
    try {
      await api('/trades', { method: 'POST', body: JSON.stringify({ symbol, side, quantity }) })
      await onComplete()
      onClose()
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setBusy(false)
    }
  }

  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm" onMouseDown={event => event.target === event.currentTarget && onClose()}>
    <div role="dialog" aria-modal="true" aria-label={t('trade.symbolTransaction', { symbol })} className="card w-full max-w-md rounded-3xl p-7 animate-in">
      <div className="flex items-start justify-between">
        <div><p className="label">{t('trade.newOrder')}</p><h2 className="mt-1 text-3xl font-black">{t('trade.symbolTransaction', { symbol })}</h2></div>
        <button aria-label="Close" onClick={onClose} className="text-2xl text-slate-400 hover:text-white transition-colors">×</button>
      </div>

      {/* Locked price indicator */}
      <div className="mt-5 flex items-center justify-between rounded-xl bg-[#081522] px-5 py-3">
        <span className="text-xs text-slate-500 uppercase tracking-wider">{t('trade.lockedPrice')}</span>
        <span className="text-lg font-black text-[#1fc8a4]">{money(lockedPrice)}</span>
      </div>

      {/* BUY / SELL tabs */}
      <div className="mt-5 grid grid-cols-2 rounded-xl bg-[#081522] p-1">
        <button
          onClick={() => canBuy && setSide('BUY')}
          disabled={!canBuy}
          className={`rounded-lg py-3 font-bold transition-all ${side === 'BUY' ? 'bg-[#1fc8a4] text-[#06140f]' : 'text-slate-400'} ${!canBuy ? 'opacity-30 cursor-not-allowed' : 'hover:opacity-90'}`}
        >{t('trade.buy')}</button>
        <button
          onClick={() => canSell && setSide('SELL')}
          disabled={!canSell}
          className={`rounded-lg py-3 font-bold transition-all ${side === 'SELL' ? 'bg-rose-400 text-[#19080d]' : 'text-slate-400'} ${!canSell ? 'opacity-30 cursor-not-allowed' : 'hover:opacity-90'}`}
        >{t('trade.sell')}</button>
      </div>

      {/* Warning if no balance */}
      {!canBuy && !canSell && <p className="mt-4 rounded-xl bg-amber-500/10 p-3 text-sm text-amber-300">{t('trade.noBalanceWarning')}</p>}

      <form onSubmit={submit} className="mt-6">
        <label htmlFor="trade-quantity" className="text-sm text-slate-300">{t('trade.coinQuantity')}</label>
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
        {numericQuantity > 0 && <div className="mt-3 flex items-center justify-between rounded-xl bg-[#0c1a2a] px-4 py-3">
          <span className="text-xs text-slate-500">{t('trade.estimatedTotal')}</span>
          <span className={`font-bold ${side === 'BUY' ? 'text-[#1fc8a4]' : 'text-rose-400'}`}>{money(estimatedTotal)}</span>
        </div>}

        {/* Balance details */}
        <div className="mt-3 text-xs text-slate-500">
          {side === 'BUY'
            ? <span>{t('trade.available')} <span className="text-slate-300">{money(usdBalance)}</span></span>
            : <span>{t('trade.owned')} <span className="text-slate-300">{assetQuantity} {symbol}</span></span>}
        </div>

        {error && <p role="alert" className="mt-4 rounded-xl bg-red-500/10 p-3 text-sm text-red-300">{error}</p>}
        <button disabled={busy || (!canBuy && side === 'BUY') || (!canSell && side === 'SELL')} className="btn btn-primary mt-6 w-full">{busy ? t('trade.processingOrder') : t('trade.executeOrder')}</button>
      </form>
    </div>
  </div>
}
