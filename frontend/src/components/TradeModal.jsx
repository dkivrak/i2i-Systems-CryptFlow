import { useState } from 'react'
import { api } from '../api/client'
import { INVALID_AMOUNT_MESSAGE, isPositiveQuantity, normalizeQuantityInput } from '../features/trades/quantityInput'

export default function TradeModal({ symbol, onClose, onComplete }) {
  const [side, setSide] = useState('BUY')
  const [quantity, setQuantity] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

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

  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" onMouseDown={event => event.target === event.currentTarget && onClose()}>
    <div role="dialog" aria-modal="true" aria-label={`${symbol} işlemi`} className="card w-full max-w-md rounded-3xl p-7">
      <div className="flex items-start justify-between">
        <div><p className="label">NEW ORDER</p><h2 className="mt-1 text-3xl font-black">{symbol} işlemi</h2></div>
        <button aria-label="Kapat" onClick={onClose} className="text-2xl text-slate-400">×</button>
      </div>
      <div className="mt-7 grid grid-cols-2 rounded-xl bg-[#081522] p-1">
        <button onClick={() => setSide('BUY')} className={`rounded-lg py-3 font-bold ${side === 'BUY' ? 'bg-[#1fc8a4] text-[#06140f]' : 'text-slate-400'}`}>Satın al</button>
        <button onClick={() => setSide('SELL')} className={`rounded-lg py-3 font-bold ${side === 'SELL' ? 'bg-rose-400 text-[#19080d]' : 'text-slate-400'}`}>Sat</button>
      </div>
      <form onSubmit={submit} className="mt-6">
        <label htmlFor="trade-quantity" className="text-sm text-slate-300">Coin miktarı</label>
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
        {error && <p role="alert" className="mt-4 rounded-xl bg-red-500/10 p-3 text-sm text-red-300">{error}</p>}
        <button disabled={busy} className="btn btn-primary mt-6 w-full">{busy ? 'Emir işleniyor…' : 'Emri gerçekleştir'}</button>
      </form>
    </div>
  </div>
}
