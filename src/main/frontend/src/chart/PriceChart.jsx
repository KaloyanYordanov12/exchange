import { useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../api.js';
import { C, MONO, SANS } from '../theme.js';
import { fmtPrice, fmtSignedPct, priceDecimals } from '../format.js';
import { LwcChart } from './LwcChart.jsx';
import { DepthChart } from './DepthChart.jsx';

const TIMEFRAMES = ['1m', '5m', '15m', '1h', '4h', '1d', '1w', '1M', '1y'];
const TYPES = [
  ['candle', 'Candles'],
  ['line', 'Line'],
  ['depth', 'Depth'],
];

export function PriceChart({ pair, pairInfo, book }) {
  const [timeframe, setTimeframe] = useState('15m');
  const [type, setType] = useState('candle');
  const [series, setSeries] = useState({ candles: [], realBoundary: null });
  const [hover, setHover] = useState(null);
  const [error, setError] = useState(null);
  const hoverRef = useRef(setHover);
  hoverRef.current = setHover;

  useEffect(() => {
    if (!pair) return undefined;
    let alive = true;
    let timer = null;
    async function load() {
      try {
        const s = await api.candles(pair, timeframe);
        if (!alive) return;
        setSeries({ candles: s.candles || [], realBoundary: s.realBoundary ?? null });
        setError(null);
      } catch (e) {
        if (alive) setError(e.message || 'Failed to load candles');
      }
    }
    setSeries({ candles: [], realBoundary: null });
    load();
    timer = setInterval(load, 5000);
    return () => {
      alive = false;
      if (timer) clearInterval(timer);
    };
  }, [pair, timeframe]);

  const candles = series.candles;
  const decimals = priceDecimals(pairInfo.tickSize);

  const headline = useMemo(() => {
    if (!candles.length) return { last: null, changePct: null };
    const first = candles[0];
    const last = candles[candles.length - 1];
    const changePct = first.open > 0 ? ((last.close - first.open) / first.open) * 100 : null;
    return { last: last.close, changePct };
  }, [candles]);

  const readout = hover || (candles.length ? candles[candles.length - 1] : null);
  const up = headline.changePct == null ? true : headline.changePct >= 0;

  return (
    <section
      style={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        background: C.panelBg,
        overflow: 'hidden',
      }}
    >
      {/* toolbar */}
      <div
        style={{
          flex: '0 0 auto',
          display: 'flex',
          alignItems: 'center',
          gap: 14,
          padding: '12px 16px',
          borderBottom: `1px solid ${C.card2}`,
          flexWrap: 'wrap',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 10 }}>
          <span style={{ fontWeight: 600, fontSize: 15, letterSpacing: '.02em' }}>
            {pairInfo.base} / {pairInfo.quote}
          </span>
          <span style={{ fontFamily: MONO, fontSize: 15, color: up ? C.green : C.red }}>
            {headline.last == null ? '--' : '$' + fmtPrice(headline.last, pairInfo.tickSize)}
          </span>
          <span
            style={{
              fontFamily: MONO,
              fontSize: 12.5,
              color: headline.changePct == null ? C.muted : up ? C.green : C.red,
            }}
          >
            {fmtSignedPct(headline.changePct)}
          </span>
        </div>
        <div style={{ flex: 1 }} />
        <Switcher
          items={TIMEFRAMES.map((t) => [t, t])}
          value={timeframe}
          onChange={setTimeframe}
        />
        <Switcher items={TYPES} value={type} onChange={setType} />
      </div>

      {/* OHLC readout */}
      <div
        style={{
          flex: '0 0 auto',
          display: 'flex',
          alignItems: 'center',
          gap: 16,
          padding: '8px 16px',
          borderBottom: `1px solid ${C.hair}`,
          fontFamily: MONO,
          fontSize: 11.5,
          minHeight: 34,
        }}
      >
        {type !== 'depth' && readout ? (
          <>
            <OhlcItem label="O" value={fmtPrice(readout.open, pairInfo.tickSize)} color={C.text2} />
            <OhlcItem label="H" value={fmtPrice(readout.high, pairInfo.tickSize)} color={C.green} />
            <OhlcItem label="L" value={fmtPrice(readout.low, pairInfo.tickSize)} color={C.red} />
            <OhlcItem
              label="C"
              value={fmtPrice(readout.close, pairInfo.tickSize)}
              color={readout.close >= readout.open ? C.green : C.red}
            />
            <OhlcItem
              label="Vol"
              value={Number(readout.volume).toLocaleString('en-US')}
              color={C.muted}
            />
            {readout.source && (
              <span
                style={{
                  fontFamily: SANS,
                  fontSize: 10,
                  letterSpacing: '.08em',
                  color: readout.source === 'SEEDED' ? C.muted2 : C.amber,
                }}
              >
                {readout.source}
              </span>
            )}
          </>
        ) : (
          <span style={{ color: C.muted2 }}>
            {type === 'depth' ? 'Market depth from the live order book' : 'No candle data yet'}
          </span>
        )}
        <div style={{ flex: 1 }} />
        {type !== 'depth' && (
          <span
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 7,
              color: C.muted3,
              fontFamily: SANS,
              fontSize: 11,
            }}
          >
            <span style={{ width: 22, borderTop: `2px dashed ${C.amber}` }} /> live data begins
          </span>
        )}
      </div>

      {/* body */}
      <div style={{ flex: 1, minHeight: 0, position: 'relative' }}>
        {error ? (
          <Centered color={C.red}>Failed to load candles: {error}</Centered>
        ) : type === 'depth' ? (
          <DepthChart book={book} pairInfo={pairInfo} />
        ) : candles.length === 0 ? (
          <Centered color={C.muted}>No candle history for this timeframe yet.</Centered>
        ) : (
          <LwcChart
            candles={candles}
            realBoundary={series.realBoundary}
            pairInfo={pairInfo}
            type={type}
            fitKey={`${pair}|${timeframe}|${type}`}
            onHover={(c) => hoverRef.current(c)}
          />
        )}
      </div>
    </section>
  );
}

function OhlcItem({ label, value, color }) {
  return (
    <span style={{ color: C.muted2 }}>
      {label} <span style={{ color }}>{value}</span>
    </span>
  );
}

function Switcher({ items, value, onChange }) {
  return (
    <div
      style={{
        display: 'flex',
        gap: 2,
        background: C.inputBg,
        border: `1px solid ${C.card2}`,
        borderRadius: 9,
        padding: 3,
      }}
    >
      {items.map(([key, label]) => {
        const active = key === value;
        return (
          <button
            key={key}
            onClick={() => onChange(key)}
            style={{
              cursor: 'pointer',
              border: 'none',
              borderRadius: 6,
              padding: '6px 10px',
              fontFamily: MONO,
              fontSize: 12,
              fontWeight: 500,
              color: active ? '#1a1204' : C.muted,
              background: active ? C.amber : 'transparent',
              boxShadow: active ? '0 0 12px rgba(255,176,32,.3)' : 'none',
            }}
          >
            {label}
          </button>
        );
      })}
    </div>
  );
}

function Centered({ children, color }) {
  return (
    <div
      style={{
        position: 'absolute',
        inset: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color,
        fontFamily: MONO,
        fontSize: 13,
        textAlign: 'center',
        padding: 20,
      }}
    >
      {children}
    </div>
  );
}
