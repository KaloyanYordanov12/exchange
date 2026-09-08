import { useEffect, useMemo, useState } from 'react';
import { api } from '../api.js';
import { C, MONO, SANS, chipFor } from '../theme.js';
import { fmtPrice, fmtSignedPct, fmtCompactUsd } from '../format.js';
import { deriveStats } from '../stats.js';
import { Link } from '../router.jsx';
import { useMarketSocket } from '../useMarketSocket.js';
import { PriceChart } from '../chart/PriceChart.jsx';
import { Panel } from '../components/Panel.jsx';
import { OrderBook } from '../components/OrderBook.jsx';
import { TradeTape } from '../components/TradeTape.jsx';
import { TradePanel } from '../components/TradePanel.jsx';
import { SimulatorPanel } from '../components/SimulatorPanel.jsx';
import { InvariantPanel } from '../components/InvariantPanel.jsx';

const DAY_SECONDS = 24 * 60 * 60;

export function TerminalPage({ pair }) {
  const [pairInfo, setPairInfo] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [stats, setStats] = useState(null);

  const { book, trades, status } = useMarketSocket(pairInfo ? pairInfo.pairId : null);

  useEffect(() => {
    let alive = true;
    api
      .pairs()
      .then((pairs) => {
        if (!alive) return;
        const found = pairs.find((p) => p.pairId === pair);
        if (found) setPairInfo(found);
        else setNotFound(true);
      })
      .catch(() => alive && setNotFound(true));
    return () => {
      alive = false;
    };
  }, [pair]);

  useEffect(() => {
    if (!pairInfo) return undefined;
    let alive = true;
    let timer = null;
    async function load() {
      const now = Math.floor(Date.now() / 1000);
      try {
        const series = await api.candles(pairInfo.pairId, '1h', now - DAY_SECONDS, now);
        if (alive) setStats(deriveStats(series, pairInfo));
      } catch {
        if (alive) setStats(deriveStats(null, pairInfo));
      }
    }
    load();
    timer = setInterval(load, 8000);
    return () => {
      alive = false;
      if (timer) clearInterval(timer);
    };
  }, [pairInfo]);

  const derived = useMemo(() => {
    const bestBid = book.bids && book.bids.length ? book.bids[0].price : null;
    const bestAsk = book.asks && book.asks.length ? book.asks[0].price : null;
    const mid = bestBid != null && bestAsk != null ? (bestBid + bestAsk) / 2 : null;
    const spread = bestBid != null && bestAsk != null ? bestAsk - bestBid : null;
    const lastTrade = trades.length ? trades[0].price : null;
    const price =
      lastTrade ?? mid ?? (stats && stats.last) ?? (pairInfo ? pairInfo.referencePrice : null);
    return { bestBid, bestAsk, mid, spread, price };
  }, [book, trades, stats, pairInfo]);

  if (notFound) {
    return (
      <div style={pageStyle}>
        <div style={{ maxWidth: 600, margin: '80px auto', textAlign: 'center' }}>
          <div style={{ fontSize: 18, marginBottom: 10 }}>Unknown pair: {pair}</div>
          <Link to="/markets">← Back to Markets</Link>
        </div>
      </div>
    );
  }
  if (!pairInfo) {
    return (
      <div style={pageStyle}>
        <div style={{ color: C.muted, fontFamily: MONO, padding: 40 }}>Loading {pair}...</div>
      </div>
    );
  }

  return (
    <div style={pageStyle}>
      <div style={{ minWidth: 1180, display: 'flex', flexDirection: 'column', gap: 12 }}>
        <Header
          pairInfo={pairInfo}
          derived={derived}
          stats={stats}
          wsStatus={status}
        />
        <main
          style={{
            display: 'grid',
            gridTemplateColumns: 'minmax(360px,1fr) 300px 324px',
            gap: 12,
            minHeight: 680,
          }}
        >
          {/* Column A: chart + trade */}
          <div style={{ minHeight: 0, display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div
              style={{
                flex: 1,
                minHeight: 360,
                border: `1px solid ${C.card2}`,
                borderRadius: 11,
                boxShadow: '0 8px 24px rgba(0,0,0,.4)',
                overflow: 'hidden',
              }}
            >
              <PriceChart pair={pairInfo.pairId} pairInfo={pairInfo} book={book} />
            </div>
            <Panel title="Place Limit Order" style={{ flex: '0 0 auto' }}>
              <TradePanel pairInfo={pairInfo} mid={derived.mid} />
            </Panel>
          </div>

          {/* Column B: order book + tape */}
          <div style={{ minHeight: 0, display: 'flex', flexDirection: 'column', gap: 12 }}>
            <OrderBook book={book} pairInfo={pairInfo} />
            <TradeTape trades={trades} pairInfo={pairInfo} />
          </div>

          {/* Column C: simulator + invariants */}
          <div style={{ minHeight: 0, display: 'flex', flexDirection: 'column', gap: 12 }}>
            <SimulatorPanel pairInfo={pairInfo} mid={derived.mid} />
            <InvariantPanel pairInfo={pairInfo} />
          </div>
        </main>
      </div>
    </div>
  );
}

function Header({ pairInfo, derived, stats, wsStatus }) {
  const [chipBg, glyphColor, glyph] = chipFor(pairInfo.base);
  const up = stats && stats.changePct != null ? stats.changePct >= 0 : true;
  const tick = pairInfo.tickSize;
  return (
    <header
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 20,
        padding: '0 18px',
        height: 62,
        background: `linear-gradient(180deg, ${C.panelBg2}, ${C.panelBg})`,
        border: `1px solid ${C.card2}`,
        borderRadius: 11,
        boxShadow: '0 10px 30px rgba(0,0,0,.45)',
      }}
    >
      <Link
        to="/markets"
        style={{ display: 'flex', alignItems: 'center', gap: 11, color: 'inherit' }}
      >
        <div
          style={{
            width: 30,
            height: 30,
            borderRadius: 8,
            background: chipBg,
            color: glyphColor,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontWeight: 600,
            fontFamily: MONO,
            fontSize: 15,
            boxShadow: '0 0 18px rgba(255,176,32,.25)',
          }}
        >
          {glyph}
        </div>
        <div>
          <div style={{ fontWeight: 600, fontSize: 16, letterSpacing: '.02em' }}>
            {pairInfo.base} / {pairInfo.quote}
          </div>
          <div
            style={{
              fontSize: 10.5,
              color: C.muted2,
              letterSpacing: '.1em',
              textTransform: 'uppercase',
            }}
          >
            ← Markets · Spot
          </div>
        </div>
      </Link>

      <div
        style={{
          display: 'flex',
          alignItems: 'baseline',
          gap: 14,
          paddingLeft: 8,
          borderLeft: `1px solid ${C.card2}`,
        }}
      >
        <div
          style={{
            fontFamily: MONO,
            fontSize: 24,
            fontWeight: 600,
            color: up ? C.green : C.red,
            textShadow: `0 0 20px ${up ? 'rgba(14,206,134,.4)' : 'rgba(255,87,102,.4)'}`,
          }}
        >
          {derived.price == null ? '--' : '$' + fmtPrice(derived.price, tick)}
        </div>
        <div style={{ fontFamily: MONO, fontSize: 14, color: up ? C.green : C.red }}>
          {stats ? fmtSignedPct(stats.changePct) : '--'} {up ? '▲' : '▼'}
        </div>
      </div>

      <div style={{ display: 'flex', gap: 22, marginLeft: 6 }}>
        <Stat label="BID" value={derived.bestBid == null ? '--' : fmtPrice(derived.bestBid, tick)} color={C.green} />
        <Stat label="ASK" value={derived.bestAsk == null ? '--' : fmtPrice(derived.bestAsk, tick)} color={C.red} />
        <Stat
          label="SPREAD"
          value={derived.spread == null ? '--' : fmtPrice(derived.spread, tick)}
          color={C.text2}
        />
        <Stat
          label="24H VOL"
          value={stats && stats.volumeUsd != null ? fmtCompactUsd(stats.volumeUsd) : '--'}
          color={C.text2}
        />
      </div>

      <div style={{ flex: 1 }} />

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '7px 12px',
          background: wsStatus === 'live' ? '#0c1a13' : '#1a1410',
          border: `1px solid ${wsStatus === 'live' ? '#14351f' : '#3a2a14'}`,
          borderRadius: 8,
        }}
      >
        <span
          style={{
            width: 8,
            height: 8,
            borderRadius: '50%',
            background: wsStatus === 'live' ? C.green : C.amber,
            animation: wsStatus === 'live' ? 'pulse 1.8s infinite' : 'none',
          }}
        />
        <span
          style={{
            fontFamily: MONO,
            fontSize: 12,
            color: wsStatus === 'live' ? C.green : C.amber,
            letterSpacing: '.06em',
          }}
        >
          {wsStatus === 'live' ? 'LIVE' : wsStatus === 'reconnecting' ? 'RECONNECTING' : 'CONNECTING'}
        </span>
      </div>
    </header>
  );
}

function Stat({ label, value, color }) {
  return (
    <div>
      <div style={{ fontSize: 10, color: C.muted2, letterSpacing: '.1em' }}>{label}</div>
      <div style={{ fontFamily: MONO, fontSize: 13, color: color || C.text2 }}>{value}</div>
    </div>
  );
}

const pageStyle = {
  minHeight: '100vh',
  background: `radial-gradient(1400px 700px at 72% -12%, ${C.panelBg2} 0%, ${C.deepBg} 62%)`,
  color: C.text,
  fontFamily: SANS,
  padding: 12,
  overflowX: 'auto',
};
