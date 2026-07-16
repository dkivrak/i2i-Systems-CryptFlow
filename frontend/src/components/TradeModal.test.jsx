import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import TradeModal from './TradeModal'

vi.mock('../api/client', () => ({ api: vi.fn() }))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key, options) => {
      const translations = {
        'trade.symbolTransaction': `${options?.symbol || ''} işlemi`,
        'trade.newOrder': 'NEW ORDER',
        'trade.buy': 'Satın al',
        'trade.sell': 'Sat',
        'trade.coinQuantity': 'Coin miktarı',
        'trade.livePrice': 'Canlı Fiyat',
        'trade.priceUnavailable': 'Fiyat verisi kullanılamıyor.',
        'trade.priceStale': 'Fiyat verisi güncel değil.',
        'trade.insufficientUsd': 'Bu işlem için yeterli USD bakiyeniz yok.',
        'trade.insufficientAssetSimple': 'Satış için yeterli varlık bakiyeniz yok.',
        'trade.invalidAmount': 'Miktar en fazla 8 ondalık basamaklı pozitif bir sayı olmalıdır.',
        'trade.processingOrder': 'Emir işleniyor…',
        'trade.executeOrder': 'Emri gerçekleştir',
        'trade.available': 'Mevcut:',
        'trade.owned': 'Sahip olunan:',
      }
      return translations[key] || key
    }
  })
}))

const portfolio = {
  usdBalance: '1000.00',
  assets: [
    { symbol: 'BTC', quantity: '1.00000000' },
    { symbol: 'ETH', quantity: '5.00000000' },
    { symbol: 'SOL', quantity: '10.00000000' },
  ],
}

const baseProps = {
  symbol: 'BTC',
  livePrice: '50000',
  priceStatus: 'live',
  portfolio,
  onClose: vi.fn(),
  onComplete: vi.fn().mockResolvedValue(undefined),
}

function renderModal(overrides = {}) {
  return render(<TradeModal {...baseProps} {...overrides} />)
}

describe('TradeModal', () => {
  afterEach(() => {
    cleanup()
    vi.resetAllMocks()
    vi.unstubAllGlobals()
    baseProps.onComplete.mockResolvedValue(undefined)
  })

  it('disables submit and avoids showing a zero price when price data is unavailable', () => {
    const webSocketConstructor = vi.fn()
    vi.stubGlobal('WebSocket', webSocketConstructor)
    renderModal({ livePrice: undefined, priceStatus: 'connecting' })

    expect(screen.getByText('Fiyat verisi kullanılamıyor.')).toBeTruthy()
    expect(screen.queryByText(/Canlı Fiyat: 0/)).toBeNull()
    fireEvent.change(screen.getByLabelText('Coin miktarı'), { target: { value: '0.01' } })
    expect(screen.getByRole('button', { name: 'Emri gerçekleştir' }).disabled).toBe(true)
    expect(webSocketConstructor).not.toHaveBeenCalled()
  })

  it('disables submit when the selected symbol price is stale', () => {
    renderModal({ priceStatus: 'stale' })

    expect(screen.getByText('Fiyat verisi güncel değil.')).toBeTruthy()
    fireEvent.change(screen.getByLabelText('Coin miktarı'), { target: { value: '0.01' } })
    expect(screen.getByRole('button', { name: 'Emri gerçekleştir' }).disabled).toBe(true)
  })

  it('re-enables a buy after an unaffordable quantity is changed to an affordable value', () => {
    renderModal()
    const input = screen.getByLabelText('Coin miktarı')
    const submitButton = screen.getByRole('button', { name: 'Emri gerçekleştir' })

    fireEvent.change(input, { target: { value: '1' } })
    expect(screen.getByRole('alert').textContent).toContain('yeterli USD bakiyeniz yok')
    expect(submitButton.disabled).toBe(true)

    fireEvent.change(input, { target: { value: '0.01' } })
    expect(screen.queryByRole('alert')).toBeNull()
    expect(submitButton.disabled).toBe(false)
    expect(api).not.toHaveBeenCalled()
  })

  it('disables a sale that exceeds the selected asset balance', () => {
    renderModal()

    fireEvent.click(screen.getByRole('button', { name: 'Sat' }))
    fireEvent.change(screen.getByLabelText('Coin miktarı'), { target: { value: '2' } })

    expect(screen.getByRole('alert').textContent).toContain('yeterli varlık bakiyeniz yok')
    expect(screen.getByRole('button', { name: 'Emri gerçekleştir' }).disabled).toBe(true)
    expect(api).not.toHaveBeenCalled()
  })

  it('clears loading after an API failure and clears the request error when input changes', async () => {
    api.mockRejectedValueOnce(new Error('Yetersiz USD bakiyesi'))
    renderModal()
    const input = screen.getByLabelText('Coin miktarı')

    fireEvent.change(input, { target: { value: '0.01' } })
    fireEvent.click(screen.getByRole('button', { name: 'Emri gerçekleştir' }))

    expect(screen.getByRole('button', { name: 'Emir işleniyor…' }).disabled).toBe(true)

    await act(async () => {
      await Promise.resolve()
    })

    const submitButton = screen.getByRole('button', { name: 'Emri gerçekleştir' })
    expect(screen.getByRole('alert').textContent).toBe('Yetersiz USD bakiyesi')
    expect(submitButton.disabled).toBe(false)

    fireEvent.change(input, { target: { value: '0.005' } })
    expect(screen.queryByText('Yetersiz USD bakiyesi')).toBeNull()
    expect(submitButton.disabled).toBe(false)
  })
})
