import { useEffect, useRef, useState } from 'react';

const MAX_TAPE = 60;

// Subscribes to one pair's live market data over the per-pair WebSocket
// (/ws/marketdata?pair=...). Returns the latest coalesced book snapshot, a bounded
// rolling trade tape, and the connection status. Reconnects with backoff on drop.
export function useMarketSocket(pair) {
  const [book, setBook] = useState({ bids: [], asks: [] });
  const [trades, setTrades] = useState([]);
  const [status, setStatus] = useState('connecting');
  const wsRef = useRef(null);
  const seqRef = useRef(0);

  useEffect(() => {
    if (!pair) return undefined;
    setBook({ bids: [], asks: [] });
    setTrades([]);
    setStatus('connecting');
    let closed = false;
    let retry = null;
    let backoff = 500;

    const connect = () => {
      const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
      const url = `${proto}://${window.location.host}/ws/marketdata?pair=${encodeURIComponent(pair)}`;
      const ws = new WebSocket(url);
      wsRef.current = ws;

      ws.onopen = () => {
        if (closed) return;
        backoff = 500;
        setStatus('live');
      };
      ws.onmessage = (event) => {
        let msg;
        try {
          msg = JSON.parse(event.data);
        } catch {
          return;
        }
        if (msg.pair && msg.pair !== pair) return; // never render another pair's data
        if (msg.type === 'book') {
          setBook({ bids: msg.bids || [], asks: msg.asks || [] });
        } else if (msg.type === 'trades' && msg.trades && msg.trades.length) {
          const stamped = msg.trades.map((t) => ({
            ...t,
            _k: `${t.sequence}-${seqRef.current++}`,
            _t: Date.now(),
          }));
          setTrades((prev) => [...stamped.reverse(), ...prev].slice(0, MAX_TAPE));
        }
      };
      ws.onclose = () => {
        if (closed) return;
        setStatus('reconnecting');
        retry = setTimeout(connect, backoff);
        backoff = Math.min(8000, backoff * 2);
      };
      ws.onerror = () => {
        try {
          ws.close();
        } catch {
          /* ignore */
        }
      };
    };

    connect();
    return () => {
      closed = true;
      if (retry) clearTimeout(retry);
      if (wsRef.current) {
        try {
          wsRef.current.close();
        } catch {
          /* ignore */
        }
      }
    };
  }, [pair]);

  return { book, trades, status };
}
