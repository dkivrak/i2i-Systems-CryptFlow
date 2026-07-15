import { act, cleanup, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import {
  MARKET_RECONNECT_DELAY_MS,
  MARKET_STALE_AFTER_MS,
  useMarketStream,
} from './useMarketStream'

vi.mock('../api/client', () => ({
  api: vi.fn(),
  WS_URL: 'ws://market.test/ws',
}))

class MockWebSocket {
  static CONNECTING = 0
  static OPEN = 1
  static CLOSING = 2
  static CLOSED = 3
  static instances = []

  constructor(url) {
    this.url = url
    this.readyState = MockWebSocket.CONNECTING
    MockWebSocket.instances.push(this)
  }

  open() {
    this.readyState = MockWebSocket.OPEN
    this.onopen?.({ target: this })
  }

  message(payload) {
    this.onmessage?.({ data: typeof payload === 'string' ? payload : JSON.stringify(payload) })
  }

  serverClose() {
    this.readyState = MockWebSocket.CLOSED
    this.onclose?.({ target: this })
  }

  close() {
    if (this.readyState === MockWebSocket.CLOSED) return
    this.readyState = MockWebSocket.CLOSED
    this.onclose?.({ target: this })
  }
}

async function renderStream() {
  const rendered = renderHook(() => useMarketStream())
  await act(async () => {
    await Promise.resolve()
  })
  return rendered
}

describe('useMarketStream', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-01-01T00:00:00Z'))
    MockWebSocket.instances = []
    api.mockResolvedValue({
      prices: { BTC: 100, ETH: 20, SOL: 5 },
      updatedAt: '2026-01-01T00:00:00Z',
    })
    vi.stubGlobal('WebSocket', MockWebSocket)
  })

  afterEach(() => {
    cleanup()
    vi.clearAllTimers()
    vi.useRealTimers()
    vi.unstubAllGlobals()
    vi.resetAllMocks()
  })

  it('stays connecting when the socket opens without a price message', async () => {
    const { result } = await renderStream()

    act(() => MockWebSocket.instances[0].open())

    expect(result.current.status).toBe('connecting')
    expect(result.current.symbolStatuses).toEqual({ BTC: 'connecting', ETH: 'connecting', SOL: 'connecting' })

    act(() => vi.advanceTimersByTime(MARKET_STALE_AFTER_MS))
    expect(result.current.status).toBe('stale')
    expect(result.current.symbolStatuses).toEqual({ BTC: 'stale', ETH: 'stale', SOL: 'stale' })
  })

  it('becomes live only after a valid current-generation price message', async () => {
    const { result } = await renderStream()
    const socket = MockWebSocket.instances[0]

    act(() => {
      socket.open()
      socket.message({ s: 'DOGE', p: '1' })
    })
    expect(result.current.status).toBe('connecting')

    act(() => socket.message({ s: 'BTC', p: '101.25' }))

    expect(result.current.status).toBe('live')
    expect(result.current.symbolStatuses.BTC).toBe('live')
    expect(result.current.market.prices.BTC).toBe('101.25')
  })

  it('does not let fresh ETH and SOL messages hide a stale BTC stream', async () => {
    const { result } = await renderStream()
    const socket = MockWebSocket.instances[0]

    act(() => {
      socket.open()
      socket.message({ s: 'BTC', p: '100' })
      socket.message({ s: 'ETH', p: '20' })
      socket.message({ s: 'SOL', p: '5' })
      vi.advanceTimersByTime(MARKET_STALE_AFTER_MS - 1_000)
      socket.message({ s: 'ETH', p: '21' })
      socket.message({ s: 'SOL', p: '6' })
      vi.advanceTimersByTime(2_000)
    })

    expect(result.current.status).toBe('stale')
    expect(result.current.symbolStatuses).toEqual({ BTC: 'stale', ETH: 'live', SOL: 'live' })

    act(() => socket.message({ s: 'BTC', p: '102' }))
    expect(result.current.status).toBe('live')
    expect(result.current.symbolStatuses.BTC).toBe('live')
  })

  it('goes offline on close and creates exactly one reconnect attempt', async () => {
    const { result } = await renderStream()
    const socket = MockWebSocket.instances[0]

    act(() => {
      socket.open()
      socket.message({ s: 'BTC', p: '100' })
      socket.serverClose()
    })

    expect(result.current.status).toBe('offline')
    expect(result.current.symbolStatuses.BTC).toBe('offline')

    act(() => vi.advanceTimersByTime(MARKET_RECONNECT_DELAY_MS - 1))
    expect(MockWebSocket.instances).toHaveLength(1)

    act(() => vi.advanceTimersByTime(1))
    expect(MockWebSocket.instances).toHaveLength(2)
    expect(result.current.status).toBe('connecting')

    act(() => vi.advanceTimersByTime(MARKET_RECONNECT_DELAY_MS * 2))
    expect(MockWebSocket.instances).toHaveLength(2)
  })

  it('clears watchdog and reconnect timers when unmounted', async () => {
    const { unmount } = await renderStream()
    act(() => MockWebSocket.instances[0].serverClose())

    unmount()

    expect(vi.getTimerCount()).toBe(0)
  })
})
