import { useState } from 'react';
import { api, getTraderKey, setTraderKey } from '../api.js';
import { C, MONO, SANS } from '../theme.js';
import { MICRO, priceUsd, fmtUsd, fmtQty, priceDecimals } from '../format.js';

// Places real limit orders through POST /orders. Prices are entered in USD and
// converted to the backend's scaled-integer, tick-aligned price; quantity is whole
// units. Every backend outcome (202/400/404/422/503/401) is surfaced.
//
// An "Amount" / "Total" mode toggle is a UI convenience only: in Amount mode the user
// enters a coin quantity; in Total mode the user enters a USD spend and the panel
// derives quantity = total / price (entered limit price, else the current market
// price). Either way the engine still receives a plain quantity + price order; there
// is no new order type or endpoint.
export function TradePanel({ pairInfo, mid }) {
  const [mode, setMode] = useState('amount'); // 'amount' | 'total'
  const [price, setPrice] = useState('');
  const [amount, setAmount] = useState(''); // quantity of base (whole units)
  const [total, setTotal] = useState(''); // USD to spend
  const [key, setKey] = useState(getTraderKey());
  const [msg, setMsg] = useState({ text: '', tone: 'muted' });
  const [busy, setBusy] = useState(false);

  const decimals = priceDecimals(pairInfo.tickSize);
  const marketUsd = mid != null ? priceUsd(mid) : null;
  const priceNum = parseFloat(price);
  const hasEnteredPrice = Number.isFinite(priceNum) && priceNum > 0;

  // Price used to convert between amount and total. In Total mode we fall back to the
  // live market price when no limit price is typed; in Amount mode only an entered
  // price is used (unchanged behaviour).
  const convUsd = mode === 'total' ? (hasEnteredPrice ? priceNum : marketUsd) : priceNum;

  // Derived counterpart shown in the summary row.
  const derivedTotal =
    Number.isFinite(priceNum) && Number.isFinite(parseInt(amount, 10))
      ? priceNum * parseInt(amount, 10)
      : 0;
  const derivedQty =
    convUsd && convUsd > 0 && Number.isFinite(parseFloat(total))
      ? Math.floor(parseFloat(total) / convUsd)
      : 0;
  const derivedCost = derivedQty > 0 && convUsd ? derivedQty * convUsd : 0;

  function switchMode(next) {
    if (next === mode) return;
    const conv = hasEnteredPrice ? priceNum : marketUsd;
    if (next === 'total') {
      const q = parseInt(amount, 10);
      if (Number.isFinite(q) && q > 0 && conv && conv > 0) setTotal((q * conv).toFixed(2));
    } else {
      const t = parseFloat(total);
      if (Number.isFinite(t) && t > 0 && conv && conv > 0) setAmount(String(Math.floor(t / conv)));
    }
    setMode(next);
  }

  async function submit(side) {
    // Effective price: entered limit price, else (Total mode) the market price.
    const effUsd = hasEnteredPrice ? priceNum : mode === 'total' ? marketUsd : NaN;
    if (!Number.isFinite(effUsd) || effUsd <= 0) {
      setMsg({ text: 'Enter a limit price.', tone: 'red' });
      return;
    }
    // Quantity: entered directly (Amount mode) or derived from the USD total.
    let qty;
    if (mode === 'total') {
      const t = parseFloat(total);
      if (!Number.isFinite(t) || t <= 0) {
        setMsg({ text: 'Enter a total to spend.', tone: 'red' });
        return;
      }
      qty = Math.floor(t / effUsd);
      if (qty <= 0) {
        setMsg({ text: 'Total is below the price of one unit.', tone: 'red' });
        return;
      }
    } else {
      qty = parseInt(amount, 10);
      if (!Number.isFinite(qty) || qty <= 0) {
        setMsg({ text: 'Enter a whole-unit amount to trade.', tone: 'red' });
        return;
      }
    }
    const tick = pairInfo.tickSize;
    const scaled = Math.round(Math.round(effUsd * MICRO) / tick) * tick;
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
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span style={{ fontSize: 11, color: C.muted, letterSpacing: '.04em' }}>Order by</span>
        <div
          style={{
            display: 'flex',
            gap: 2,
            background: C.inputBg,
            border: `1px solid ${C.border2}`,
            borderRadius: 8,
            padding: 3,
          }}
        >
          {[
            ['amount', 'Amount'],
            ['total', 'Total'],
          ].map(([k, label]) => {
            const active = k === mode;
            return (
              <button
                key={k}
                onClick={() => switchMode(k)}
                style={{
                  cursor: 'pointer',
                  border: 'none',
                  borderRadius: 6,
                  padding: '5px 12px',
                  fontFamily: MONO,
                  fontSize: 11.5,
                  fontWeight: 500,
                  color: active ? '#1a1204' : C.muted,
                  background: active ? C.amber : 'transparent',
                }}
              >
                {label}
              </button>
            );
          })}
        </div>
      </div>

      <div style={{ display: 'flex', gap: 11 }}>
        <Field label="Limit Price (USD)">
          <input
            value={price}
            onChange={(e) => setPrice(e.target.value)}
            placeholder={marketUsd != null ? marketUsd.toFixed(decimals) : '0.00'}
            inputMode="decimal"
            style={inputStyle}
          />
        </Field>
        {mode === 'amount' ? (
          <Field label={`Amount (${pairInfo.base})`}>
            <input
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0"
              inputMode="numeric"
              style={inputStyle}
            />
          </Field>
        ) : (
          <Field label="Total (USD)">
            <input
              value={total}
              onChange={(e) => setTotal(e.target.value)}
              placeholder="0.00"
              inputMode="decimal"
              style={inputStyle}
            />
          </Field>
        )}
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
        {mode === 'amount' ? (
          <>
            <span style={{ fontSize: 11, color: C.muted }}>Order Value</span>
            <span style={{ fontFamily: MONO, fontSize: 13, color: C.text2 }}>
              {derivedTotal.toLocaleString('en-US', {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2,
              })}{' '}
              <span style={{ color: C.muted2 }}>USD</span>
            </span>
          </>
        ) : (
          <>
            <span style={{ fontSize: 11, color: C.muted }}>Quantity</span>
            <span style={{ fontFamily: MONO, fontSize: 13, color: C.text2 }}>
              ≈ {fmtQty(derivedQty)} <span style={{ color: C.muted2 }}>{pairInfo.base}</span>
              {derivedCost > 0 && (
                <span style={{ color: C.muted2 }}>
                  {'  ·  $'}
                  {derivedCost.toLocaleString('en-US', {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2,
                  })}
                </span>
              )}
            </span>
          </>
        )}
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
