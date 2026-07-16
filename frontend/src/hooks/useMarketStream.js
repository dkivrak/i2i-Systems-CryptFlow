import { useEffect, useRef, useState } from 'react'
import { api, WS_URL } from '../api/client'

export const MARKET_STALE_AFTER_MS = 15_000
export const MARKET_WATCHDOG_INTERVAL_MS = 1_000
export const MARKET_RECONNECT_DELAY_MS = 5_000

export function useMarketStream() {
  const [market, setMarket] = useState(null)
  const [basePrices, setBasePrices] = useState(null)
  const [dailyOpenPrices, setDailyOpenPrices] = useState(null)
  const [status, setStatus] = useState('connecting')
  const fallbackSymbols = ['BTC', 'ETH', 'SOL', 'BNB', 'ADA', 'XRP', 'DOGE', 'DOT', 'AVAX', 'LINK']
  const [symbolStatuses, setSymbolStatuses] = useState(() =>
    Object.fromEntries(fallbackSymbols.map(s => [s, 'connecting']))
  )
  const [error, setError] = useState('')

  const marketRef = useRef(market)
  marketRef.current = market
  const basePricesRef = useRef(basePrices)
  basePricesRef.current = basePrices

  useEffect(() => {
    let alive = true
    let socket = null
    let reconnectTimer = null
    let watchdogTimer = null
    let openedAt = null
    const lastValidMessageAt = {}

    const clearReconnectTimer = () => {
      if (reconnectTimer !== null) {
        clearTimeout(reconnectTimer)
        reconnectTimer = null
      }
    }

    const updateHealth = (now = Date.now()) => {
      if (!alive || !socket || socket.readyState !== WebSocket.OPEN) return

      let hasLiveSymbol = false
      let hasStaleSymbol = false
      const nextStatuses = {}
      const activeSymbols = marketRef.current?.prices ? Object.keys(marketRef.current.prices) : []

      for (const symbol of activeSymbols) {
        const lastMessageAt = lastValidMessageAt[symbol] || null
        if (lastMessageAt !== null && now - lastMessageAt <= MARKET_STALE_AFTER_MS) {
          nextStatuses[symbol] = 'live'
          hasLiveSymbol = true
        } else if (lastMessageAt !== null || (openedAt !== null && now - openedAt >= MARKET_STALE_AFTER_MS)) {
          nextStatuses[symbol] = 'stale'
          hasStaleSymbol = true
        } else {
          nextStatuses[symbol] = 'connecting'
        }
      }

      setSymbolStatuses(nextStatuses)
      setStatus(hasStaleSymbol ? 'stale' : hasLiveSymbol ? 'live' : 'connecting')
    }

    const markDisconnected = () => {
      openedAt = null
      setStatus('offline')
      setSymbolStatuses(prev => {
        const next = {}
        Object.keys(prev).forEach(s => {
          next[s] = 'offline'
        })
        return next
      })
    }

    const resetFreshness = () => {
      openedAt = null
      Object.keys(lastValidMessageAt).forEach(s => {
        lastValidMessageAt[s] = null
      })
      setStatus('connecting')
      setSymbolStatuses(prev => {
        const next = {}
        Object.keys(prev).forEach(s => {
          next[s] = 'connecting'
        })
        return next
      })
    }

    const scheduleReconnect = () => {
      if (!alive || reconnectTimer !== null) return
      reconnectTimer = setTimeout(() => {
        reconnectTimer = null
        connect()
      }, MARKET_RECONNECT_DELAY_MS)
    }

    const connect = () => {
      if (!alive) return
      if (socket && (socket.readyState === WebSocket.CONNECTING || socket.readyState === WebSocket.OPEN)) return

      clearReconnectTimer()
      resetFreshness()

      let nextSocket
      try {
        nextSocket = new WebSocket(WS_URL)
      } catch {
        socket = null
        markDisconnected()
        scheduleReconnect()
        return
      }

      socket = nextSocket

      nextSocket.onopen = () => {
        if (!alive || socket !== nextSocket) return
        openedAt = Date.now()
        setStatus('connecting')
      }

      nextSocket.onmessage = event => {
        if (!alive || socket !== nextSocket) return

        try {
          const data = JSON.parse(event.data)
          const price = Number(data.p)
          const isKnownSymbol = basePricesRef.current && (data.s in basePricesRef.current)
          if (!isKnownSymbol || !Number.isFinite(price) || price <= 0) {
            throw new Error('Invalid market price message')
          }

          const receivedAt = Date.now()
          lastValidMessageAt[data.s] = receivedAt
          const nextMarket = {
            ...(marketRef.current ?? {}),
            prices: { ...(marketRef.current?.prices ?? {}), [data.s]: data.p },
            updatedAt: new Date(receivedAt).toISOString(),
          }
          marketRef.current = nextMarket
          setMarket(nextMarket)
          setError('')
          updateHealth(receivedAt)
        } catch {
          setError('Failed to parse WebSocket message.')
        }
      }

      nextSocket.onerror = () => {
        if (!alive || socket !== nextSocket) return
        socket = null
        markDisconnected()
        nextSocket.onclose = null
        if (nextSocket.readyState === WebSocket.CONNECTING || nextSocket.readyState === WebSocket.OPEN) {
          nextSocket.close()
        }
        scheduleReconnect()
      }

      nextSocket.onclose = () => {
        if (!alive || socket !== nextSocket) return
        socket = null
        markDisconnected()
        scheduleReconnect()
      }
    }

    if (import.meta.env.MODE !== 'test') {
      fetch('https://api.binance.com/api/v3/ticker/24hr')
        .then(res => res.json())
        .then(data => {
          if (alive && Array.isArray(data)) {
            const opens = {}
            data.forEach(item => {
              if (item.symbol.endsWith('USDT')) {
                const symbol = item.symbol.slice(0, -4)
                const openPrice = Number(item.openPrice || 0)
                if (openPrice > 0) {
                  opens[symbol] = openPrice
                }
              }
            })
            setDailyOpenPrices(opens)
          }
        })
        .catch(err => {
          console.warn('Failed to fetch daily open prices from Binance:', err)
        })
    }

    api('/market/prices')
      .then(value => {
        if (alive) {
          marketRef.current = value
          setMarket(value)
          if (value?.prices) {
            setBasePrices(previous => previous || value.prices)
          }
          updateHealth()
        }
      })
      .catch(requestError => {
        if (alive) setError(requestError.message)
      })

    watchdogTimer = setInterval(() => updateHealth(), MARKET_WATCHDOG_INTERVAL_MS)
    connect()

    return () => {
      alive = false
      clearReconnectTimer()
      if (watchdogTimer !== null) clearInterval(watchdogTimer)
      if (socket) {
        const activeSocket = socket
        socket = null
        activeSocket.onopen = null
        activeSocket.onmessage = null
        activeSocket.onerror = null
        activeSocket.onclose = null
        if (activeSocket.readyState === WebSocket.CONNECTING || activeSocket.readyState === WebSocket.OPEN) {
          activeSocket.close()
        }
      }
    }
  }, [])

  const changes = {}
  if (market?.prices) {
    for (const symbol of Object.keys(market.prices)) {
      const base = Number(dailyOpenPrices?.[symbol] || basePrices?.[symbol] || 0)
      const current = Number(market.prices[symbol])
      if (base > 0 && current > 0) {
        changes[symbol] = ((current - base) / base) * 100
      } else {
        changes[symbol] = 0
      }
    }
  }

  return { market, status, symbolStatuses, error, changes, dailyOpenPrices, basePrices }
}
