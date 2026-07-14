import { useEffect, useState } from 'react';
import { api } from '../api/client';

const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws';

export function useMarketStream() {
  const [market, setMarket] = useState(null);
  const [status, setStatus] = useState('connecting');
  const [error, setError] = useState('');

  useEffect(() => {
    let alive = true;
    let socket = null;
    let reconnectTimer = null;

    const clearReconnectTimer = () => {
      if (reconnectTimer) {
        clearTimeout(reconnectTimer);
        reconnectTimer = null;
      }
    };

    const connect = () => {
      if (!alive) return;

      clearReconnectTimer();
      setStatus('connecting');

      socket = new WebSocket(WS_URL);

      socket.onopen = () => {
        if (!alive) return;
        setStatus('live');
      };

      socket.onmessage = (event) => {
        if (!alive) return;

        try {
          const data = JSON.parse(event.data);
          setMarket((prev) => ({
            ...prev,
            prices: { ...(prev?.prices ?? {}), [data.s]: data.p },
            updatedAt: new Date().toISOString(),
          }));
          setStatus('live');
          setError('');
        } catch {
          setError('WebSocket mesajı çözümlenemedi.');
        }
      };

      socket.onerror = () => {
        if (!alive) return;
        setStatus('offline');
      };

      socket.onclose = () => {
        if (!alive) return;

        setStatus('offline');
        reconnectTimer = setTimeout(connect, 5000);
      };
    };

    api('/market/prices')
      .then((value) => {
        if (alive) setMarket(value);
      })
      .catch((err) => {
        if (alive) setError(err.message);
      });

    connect();

    return () => {
      alive = false;
      clearReconnectTimer();
      if (socket) {
        socket.onopen = null;
        socket.onmessage = null;
        socket.onerror = null;
        socket.onclose = null;
        socket.close();
      }
    };
  }, []);

  return { market, status, error };
}
