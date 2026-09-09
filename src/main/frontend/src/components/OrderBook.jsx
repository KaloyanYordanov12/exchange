import { Panel } from './Panel.jsx';
import { C, MONO } from '../theme.js';
import { fmtPrice, fmtQty } from '../format.js';

const DEPTH = 13;

export function OrderBook({ book, pairInfo }) {
  const asks = (book.asks || []).slice(0, DEPTH);
  const bids = (book.bids || []).slice(0, DEPTH);

  let ca = 0;
  const askRows = asks.map((l) => {
    ca += l.quantity;
    return { price: l.price, size: l.quantity, cum: ca };
  });
  let cb = 0;
  const bidRows = bids.map((l) => {
    cb += l.quantity;
    return { price: l.price, size: l.quantity, cum: cb };
  });
  const maxCum = Math.max(1, ca, cb);

  const bestBid = bids.length ? bids[0].price : null;
  const bestAsk = asks.length ? asks[0].price : null;
  const mid = bestBid != null && bestAsk != null ? (bestBid + bestAsk) / 2 : null;
  const spread = bestBid != null && bestAsk != null ? bestAsk - bestBid : null;
  const spreadPct = mid ? (spread / mid) * 100 : null;

  return (
    <Panel
      title="Order Book"
      right={<span style={{ fontFamily: MONO, color: C.muted2 }}>{pairInfo.pairId}</span>}
      style={{ height: 496, flex: '0 0 auto' }}
    >
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr 1fr',
          padding: '6px 12px',
          fontSize: 10,
          letterSpacing: '.06em',
          color: C.muted2,
          borderBottom: `1px solid ${C.hair}`,
          flex: '0 0 auto',
        }}
      >
        <span>PRICE</span>
        <span style={{ textAlign: 'right' }}>SIZE</span>
        <span style={{ textAlign: 'right' }}>TOTAL</span>
      </div>

      <div
        style={{
          flex: 1,
          minHeight: 0,
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'flex-end',
        }}
      >
        {askRows
          .slice()
          .reverse()
          .map((r) => (
            <Row
              key={`a${r.price}`}
              r={r}
              color={C.red}
              bar="rgba(255,87,102,.13)"
              maxCum={maxCum}
              pairInfo={pairInfo}
            />
          ))}
      </div>

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '8px 14px',
          background: 'linear-gradient(90deg,rgba(255,176,32,.06),transparent)',
          borderTop: `1px solid ${C.card2}`,
          borderBottom: `1px solid ${C.card2}`,
          flex: '0 0 auto',
        }}
      >
        <span style={{ fontFamily: MONO, fontSize: 15, fontWeight: 600, color: C.text2 }}>
          {mid == null ? '--' : fmtPrice(mid, pairInfo.tickSize)}
        </span>
        <span style={{ fontFamily: MONO, fontSize: 11, color: C.muted3 }}>
          {spread == null
            ? 'no spread'
            : `spread ${fmtPrice(spread, pairInfo.tickSize)} · ${spreadPct.toFixed(3)}%`}
        </span>
      </div>

      <div style={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
        {bidRows.map((r) => (
          <Row
            key={`b${r.price}`}
            r={r}
            color={C.green}
            bar="rgba(14,206,134,.13)"
            maxCum={maxCum}
            pairInfo={pairInfo}
          />
        ))}
      </div>
    </Panel>
  );
}

function Row({ r, color, bar, maxCum, pairInfo }) {
  return (
    <div
      style={{
        position: 'relative',
        display: 'grid',
        gridTemplateColumns: '1fr 1fr 1fr',
        padding: '0 12px',
        height: 21,
        alignItems: 'center',
        fontFamily: MONO,
        fontSize: 12,
      }}
    >
      <div
        style={{
          position: 'absolute',
          right: 0,
          top: 1,
          bottom: 1,
          width: `${(r.cum / maxCum) * 100}%`,
          background: bar,
          borderRadius: '2px 0 0 2px',
        }}
      />
      <div style={{ position: 'relative', color, fontWeight: 500 }}>
        {fmtPrice(r.price, pairInfo.tickSize)}
      </div>
      <div style={{ position: 'relative', textAlign: 'right', color: C.text2 }}>
        {fmtQty(r.size)}
      </div>
      <div style={{ position: 'relative', textAlign: 'right', color: C.muted3 }}>
        {fmtQty(r.cum)}
      </div>
    </div>
  );
}
