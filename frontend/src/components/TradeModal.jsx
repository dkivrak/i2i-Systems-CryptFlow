import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api } from '../api/client'
import { normalizeQuantityInput } from '../features/trades/quantityInput'
import { validateTrade } from '../features/trades/tradeValidation'
import { money } from '../utils/format'

export default function TradeModal({ symbol, side: initialSide = 'BUY', isSellOnly = false, changePercent, livePrice, priceStatus, portfolio, onClose, onComplete }) {
  const { t } = useTranslation()
  const [side, setSide] = useState(initialSide)
  const [quantity, setQuantity] = useState('')
  const [busy, setBusy] = useState(false)
  const [requestError, setRequestError] = useState('')
  const [result, setResult] = useState(null)
  const [showApproveStep, setShowApproveStep] = useState(false)

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

  function handleFormSubmit(event) {
    event.preventDefault()
    setRequestError('')
    if (validationError) {
      setRequestError(t(validationError))
      return
    }
    setShowApproveStep(true)
  }

  async function executeTrade() {
    setRequestError('')
    const submittedContext = contextKey
    setBusy(true)
    try {
      const res = await api('/trades', { method: 'POST', body: JSON.stringify({ symbol, side, quantity }) })
      setResult(res)
      await onComplete()
    } catch (requestErrorValue) {
      if (latestContextRef.current === submittedContext) {
        setRequestError(requestErrorValue.message)
      }
      setShowApproveStep(false)
    } finally {
      setBusy(false)
    }
  }

  if (result) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm" onMouseDown={event => event.target === event.currentTarget && onClose()}>
        <div role="dialog" aria-modal="true" className="card w-full max-w-md rounded-3xl p-7 animate-in space-y-6">
          {/* Header */}
          <div className="text-center space-y-2">
            <span className="inline-grid h-12 w-12 place-items-center rounded-full bg-emerald-500/10 text-emerald-400">
              <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                <polyline points="20 6 9 17 4 12" />
              </svg>
            </span>
            <h2 className="text-2xl font-black text-white">{t('trade.successTitle')}</h2>
            <p className="text-xs text-slate-400">{t('trade.receiptDesc')}</p>
          </div>

          {/* Receipt Details */}
          <div className="rounded-2xl bg-[#081522] p-5 space-y-3.5 border border-white/5 text-sm">
            <div className="flex justify-between">
              <span className="text-slate-400">{t('trade.receiptType')}</span>
              <span className={`font-bold px-2 py-0.5 rounded text-xs ${result.side === 'BUY' ? 'bg-[#1fc8a4]/10 text-[#1fc8a4]' : 'bg-rose-500/10 text-rose-400'}`}>
                {result.side === 'BUY' ? t('trade.buy') : t('trade.sell')}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-400">{t('trade.receiptAsset')}</span>
              <span className="font-bold text-white">{result.symbol}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-400">{t('trade.receiptQuantity')}</span>
              <span className="font-bold text-white">{result.quantity} {result.symbol}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-400">{t('trade.receiptPrice')}</span>
              <span className="font-bold text-white">{money(result.unitPriceUsd)}</span>
            </div>
            <div className="flex justify-between border-t border-white/10 pt-3">
              <span className="text-slate-400 font-bold">{t('trade.receiptTotal')}</span>
              <span className="font-black text-[#1fc8a4]">{money(result.totalUsd)}</span>
            </div>
          </div>

          {/* Close Button */}
          <button
            type="button"
            onClick={onClose}
            className="btn btn-primary w-full py-3"
          >
            {t('trade.closeReceipt')}
          </button>
        </div>
      </div>
    )
  }

  if (showApproveStep && !result) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm" onMouseDown={event => event.target === event.currentTarget && onClose()}>
        <div role="dialog" aria-modal="true" className="card w-full max-w-md rounded-3xl p-7 animate-in space-y-6">
          {/* Header */}
          <div className="text-center space-y-2">
            <span className="inline-grid h-12 w-12 place-items-center rounded-full bg-amber-500/10 text-amber-400">
              <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="8" x2="12" y2="12" />
                <line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
            </span>
            <h2 className="text-2xl font-black text-white">{t('trade.confirmTitle')}</h2>
            <p className="text-xs text-slate-400">{t('trade.confirmDesc')}</p>
          </div>

          {/* Details Table */}
          <div className="rounded-2xl bg-[#081522] p-5 space-y-3.5 border border-white/5 text-sm">
            <div className="flex justify-between">
              <span className="text-slate-400">{t('trade.receiptType')}</span>
              <span className={`font-bold px-2 py-0.5 rounded text-xs ${side === 'BUY' ? 'bg-[#1fc8a4]/10 text-[#1fc8a4]' : 'bg-rose-500/10 text-rose-400'}`}>
                {side === 'BUY' ? t('trade.buy') : t('trade.sell')}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-400">{t('trade.receiptAsset')}</span>
              <span className="font-bold text-white">{symbol}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-400">{t('trade.receiptQuantity')}</span>
              <span className="font-bold text-white">{quantity} {symbol}</span>
            </div>
            <div className="flex justify-between border-t border-white/10 pt-3">
              <span className="text-slate-400 font-bold">{t('trade.receiptTotal')}</span>
              <span className="font-black text-[#1fc8a4]">{money(estimatedTotal)}</span>
            </div>
          </div>

          {displayedError && <p role="alert" className="rounded-xl bg-red-500/10 p-3 text-sm text-red-300">{displayedError}</p>}

          {/* Buttons */}
          <div className="flex gap-3">
            <button
              type="button"
              onClick={executeTrade}
              disabled={busy}
              className="btn btn-primary flex-1 py-3"
            >
              {busy ? t('trade.processingOrder') : t('trade.approveOrder')}
            </button>
            <button
              type="button"
              onClick={() => setShowApproveStep(false)}
              disabled={busy}
              className="btn bg-white/10 hover:bg-white/20 text-white flex-1 py-3 font-bold transition"
            >
              {t('trade.backToEdit')}
            </button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm" onMouseDown={event => event.target === event.currentTarget && onClose()}>
      <div role="dialog" aria-modal="true" aria-label={t('trade.symbolTransaction', { symbol })} className="card w-full max-w-md rounded-3xl p-7 animate-in">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-3.5">
            <CoinLogo symbol={symbol} index={symbol === 'BTC' ? 0 : symbol === 'ETH' ? 1 : 2} />
            <div>
              <p className="label text-[10px] tracking-wider">{t('trade.newOrder')}</p>
              <div className="flex items-baseline gap-2 mt-0.5">
                <h2 className="text-xl font-black text-white">{symbol} / USD</h2>
              </div>
            </div>
          </div>
          <button type="button" aria-label="Close" onClick={onClose} className="text-2xl text-slate-400 hover:text-white transition-colors">×</button>
        </div>

        {/* BUY / SELL tabs */}
        {!isSellOnly ? (
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
        ) : (
          <div className="mt-5 rounded-xl bg-rose-500/10 border border-rose-500/20 py-3 text-center text-rose-400 font-bold text-sm">
            {t('trade.sell')} ({symbol})
          </div>
        )}

        <form onSubmit={handleFormSubmit} className="mt-6">
          <label htmlFor="trade-quantity" className="text-sm text-slate-300">{t('trade.coinQuantity')}</label>
          <div className="mt-2 text-sm text-slate-400 flex items-center gap-2">
            {hasFreshPrice ? (
              <>
                <span>{t('trade.livePrice')}: <span className="text-white font-bold">{money(numericPrice)}</span></span>
                {changePercent !== undefined && (
                  <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${
                    changePercent >= 0 ? 'bg-emerald-500/10 text-emerald-400' : 'bg-rose-500/10 text-rose-400'
                  }`}>
                    {changePercent >= 0 ? '+' : ''}{changePercent.toFixed(2)}%
                  </span>
                )}
              </>
            ) : (
              t(unavailablePriceMessage)
            )}
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

function CoinLogo({ symbol }) {
  const [imgError, setImgError] = useState(false);

  const getSymbolGradient = (sym) => {
    let hash = 0;
    for (let i = 0; i < sym.length; i++) {
      hash = sym.charCodeAt(i) + ((hash << 5) - hash);
    }
    const h1 = Math.abs(hash % 360);
    const h2 = (h1 + 60) % 360;
    return `linear-gradient(135deg, hsl(${h1}, 85%, 60%), hsl(${h2}, 90%, 40%))`;
  };

  if (imgError) {
    const displaySymbol = symbol.length > 4 ? symbol.slice(0, 3) : symbol;
    return (
      <span
        style={{ background: getSymbolGradient(symbol) }}
        className="grid h-11 w-11 place-items-center rounded-full font-black text-[10px] text-white shadow-[0_4px_12px_rgba(0,0,0,0.3)] tracking-tight uppercase"
      >
        {displaySymbol}
      </span>
    );
  }

  return (
    <img
      src={`https://raw.githubusercontent.com/spothq/cryptocurrency-icons/master/128/color/${symbol.toLowerCase()}.png`}
      alt={symbol}
      onError={() => setImgError(true)}
      className="h-11 w-11 rounded-full object-contain"
      loading="lazy"
    />
  );
}
