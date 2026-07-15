import { useEffect, useState } from 'react'
import { api, WS_URL } from '../api/client'

export const MARKET_SYMBOLS = ['BTC', 'ETH', 'SOL']
export const MARKET_STALE_AFTER_MS = 15_000
export const MARKET_WATCHDOG_INTERVAL_MS = 1_000
export const MARKET_RECONNECT_DELAY_MS = 5_000

const statusesFor = status => Object.fromEntries(MARKET_SYMBOLS.map(symbol => [symbol, status]))

export function useMarketStream() {
  const [market, setMarket] = useState(null)
  const [status, setStatus] = useState('connecting')
  const [symbolStatuses, setSymbolStatuses] = useState(() => statusesFor('connecting'))
  const [error, setError] = useState('')

  useEffect(() => {
    let alive = true
    let socket = null
    let reconnectTimer = null
    let watchdogTimer = null
    let openedAt = null
    const lastValidMessageAt = Object.fromEntries(MARKET_SYMBOLS.map(symbol => [symbol, null]))

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

      for (const symbol of MARKET_SYMBOLS) {
        const lastMessageAt = lastValidMessageAt[symbol]
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
      setSymbolStatuses(statusesFor('offline'))
    }

    const resetFreshness = () => {
      openedAt = null
      for (const symbol of MARKET_SYMBOLS) lastValidMessageAt[symbol] = null
      setStatus('connecting')
      setSymbolStatuses(statusesFor('connecting'))
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
          if (!MARKET_SYMBOLS.includes(data.s) || !Number.isFinite(price) || price <= 0) {
            throw new Error('Invalid market price message')
          }

          const receivedAt = Date.now()
          lastValidMessageAt[data.s] = receivedAt
          setMarket(previous => ({
            ...(previous ?? {}),
            prices: { ...(previous?.prices ?? {}), [data.s]: data.p },
            updatedAt: new Date(receivedAt).toISOString(),
          }))
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

    api('/market/prices')
      .then(value => {
        if (alive) {
          setMarket(previous => previous ? {
            ...value,
            ...previous,
            prices: { ...(value?.prices ?? {}), ...(previous.prices ?? {}) },
          } : value)
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

  return { market, status, symbolStatuses, error }
}
