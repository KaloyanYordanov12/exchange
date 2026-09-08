import { useState } from 'react';
import { api, getTraderKey, setTraderKey } from '../api.js';
import { C, MONO, SANS } from '../theme.js';
import { MICRO, priceUsd, fmtUsd, priceDecimals } from '../format.js';

// Places real limit orders through POST /orders. Prices are entered in USD and
// converted to the backend's scaled-integer, tick-aligned price; quantity is whole
// units. Every backend outcome (202/400/404/422/503/401) is surfaced.
export function TradePanel({ pairInfo, mid, onPlaced }) {
  const [price, setPrice] = useState('');
  const [amount, setAmount] = useState('');
  const [key, setKey] = useState(getTraderKey());
  const [msg, setMsg] = useState({ text: '', tone: 'muted' });
  const [busy, setBusy] = useState(false);

  const decimals = priceDecimals(pairInfo.tickSize);
  const midUsd = mid != null ? priceUsd(mid) : null;
  const priceNum = parseFloat(price);
  const amountNum = parseInt(amount, 10);
  const valueUsd =
    Number.isFinite(priceNum) && Number.isFinite(amountNum) ? priceNum * amountNum : 0;

  async function submit(side) {
    const usd = parseFloat(price);
    const qty = parseInt(amount, 10);
    if (!Number.isFinite(qty) || qty <= 0) {
      setMsg({ text: 'Enter a whole-unit amount to trade.', tone: 'red' });
      return;
    }
    if (!Number.isFinite(usd) || usd <= 0) {
      setMsg({ text: 'Enter a limit price.', tone: 'red' });
      return;
    }
    const tick = pairInfo.tickSize;
    const scaled = Math.round(Math.round(usd * MICRO) / tick) * tick;
    if (scaled <= 0) {
      setMsg({ text: 'Price rounds below one tick.', tone: 'red' });
      return;
    }
    setTraderKey(key.trim());
    setBusy(true);
    try {
      const res = await api.placeOrder({
        pair: pairInfo.pairId,
        side,
        price: scaled,
        quantity: qty,
      });
      const arrow = side === 'BUY' ? '▲' : '▼';
      setMsg({
        text: `${arrow} ${side} ${qty} ${pairInfo.base} @ $${fmtUsd(scaled, decimals)} accepted · order #${res.orderId}`,
        tone: 'green',
      });
      if (onPlaced) onPlaced();
    } catch (e) {
      const detail =
        e.status === 401
          ? 'invalid API key'
          : e.status === 422
            ? 'insufficient buying power'
            : e.status === 404
              ? 'unknown pair'
              : e.status === 503
                ? 'engine busy, try again'
                : e.message;
      setMsg({ text: `Rejected (${e.status || 'error'}): ${detail}`, tone: 'red' });
    } finally {
      setBusy(false);
    }
  }

  const toneColor = msg.tone === 'green' ? C.green : msg.tone === 'red' ? C.red : C.muted;

  return (
    <div style={{ padding: 14, display: 'flex', flexDirection: 'column', gap: 11 }}>
      <div style={{ display: 'flex', gap: 11 }}>
        <Field label="Limit Price (USD)">
          <input
            value={price}
            onChange={(e) => setPrice(e.target.value)}
            placeholder={midUsd != null ? midUsd.toFixed(decimals) : '0.00'}
            inputMode="decimal"
            style={inputStyle}
          />
        </Field>
        <Field label={`Amount (${pairInfo.base})`}>
          <input
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="0"
            inputMode="numeric"
            style={inputStyle}
          />
        </Field>
      </div>

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '8px 11px',
          background: C.inputBg,
          borderRadius: 7,
        }}
      >
        <span style={{ fontSize: 11, color: C.muted }}>Order Value</span>
        <span style={{ fontFamily: MONO, fontSize: 13, color: C.text2 }}>
          {valueUsd.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}{' '}
          <span style={{ color: C.muted2 }}>USD</span>
        </span>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 11 }}>
        <button
          disabled={busy}
          onClick={() => submit('BUY')}
          style={{
            cursor: busy ? 'default' : 'pointer',
            opacity: busy ? 0.6 : 1,
            border: 'none',
            borderRadius: 8,
            padding: 12,
            fontFamily: SANS,
            fontWeight: 600,
            fontSize: 14,
            color: '#04150d',
            background: `linear-gradient(180deg, ${C.greenBright}, #0bb673)`,
            boxShadow: '0 4px 16px rgba(14,206,134,.28)',
          }}
        >
          Buy {pairInfo.base}
        </button>
        <button
          disabled={busy}
          onClick={() => submit('SELL')}
          style={{
            cursor: busy ? 'default' : 'pointer',
            opacity: busy ? 0.6 : 1,
            border: 'none',
            borderRadius: 8,
            padding: 12,
            fontFamily: SANS,
            fontWeight: 600,
            fontSize: 14,
            color: '#1a0508',
            background: `linear-gradient(180deg, ${C.redBright}, #ea3b52)`,
            boxShadow: '0 4px 16px rgba(255,87,102,.28)',
          }}
        >
          Sell {pairInfo.base}
        </button>
      </div>

      <div style={{ minHeight: 15, fontSize: 11, color: toneColor, fontFamily: MONO }}>
        {msg.text}
      </div>

      <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ fontSize: 10, color: C.muted2, letterSpacing: '.06em' }}>API KEY</span>
        <input
          value={key}
          onChange={(e) => setKey(e.target.value)}
          spellCheck={false}
          style={{
            ...inputStyle,
            fontSize: 11,
            padding: '6px 9px',
            color: C.muted,
          }}
        />
      </label>
    </div>
  );
}

const inputStyle = {
  background: C.inputBg,
  border: `1px solid ${C.border2}`,
  borderRadius: 7,
  color: C.text,
  fontFamily: MONO,
  fontSize: 14,
  padding: '9px 11px',
  width: '100%',
  outline: 'none',
};

function Field({ label, children }) {
  return (
    <label style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 5 }}>
      <span style={{ fontSize: 11, color: C.muted }}>{label}</span>
      {children}
    </label>
  );
}
