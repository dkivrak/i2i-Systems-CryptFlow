import { useEffect, useState } from 'react'
import { Client } from '@stomp/stompjs'
import { api, WS_URL } from '../api/client'

const RECONNECT_DELAY_MS = 5000
const PRICE_TOPIC = '/topic/market/prices'

export function useMarketStream() {
  const [market, setMarket] = useState(null)
  const [status, setStatus] = useState('connecting')
  const [error, setError] = useState('')

  useEffect(() => {
    let alive = true

    api('/market/prices')
      .then(prices => alive && setMarket(prices))
      .catch(err => alive && setError(err.message))

    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: RECONNECT_DELAY_MS,
      onConnect: () => {
        if (!alive) return
        setStatus('live')
        client.subscribe(PRICE_TOPIC, msg => {
          setMarket(JSON.parse(msg.body))
          setStatus('live')
        })
      },
      onWebSocketClose: () => alive && setStatus('offline'),
      onStompError: () => alive && setStatus('offline')
    })

    client.activate()

    return () => {
      alive = false
      client.deactivate()
    }
  }, [])

  return { market, status, error }
}
