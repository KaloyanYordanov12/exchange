import { C, MONO } from '../theme.js';
import { priceUsd, fmtPrice } from '../format.js';

// Market-depth view built from the live order book (real bids/asks). Cumulative size
// from the mid outward, drawn as two filled areas. No fabricated data: an empty book
// shows an empty state.
export function DepthChart({ book, pairInfo }) {
  const W = 1000;
  const H = 440;
  const padT = 12;
  const bids = (book && book.bids) || [];
  const asks = (book && book.asks) || [];

  if (bids.length === 0 && asks.length === 0) {
    return <EmptyDepth />;
  }

  const bestBid = bids.length ? bids[0].price : null;
  const bestAsk = asks.length ? asks[0].price : null;
  const mid =
    bestBid != null && bestAsk != null
      ? (bestBid + bestAsk) / 2
      : bestBid != null
        ? bestBid
        : bestAsk;

  let cb = 0;
  const bidPts = bids.map((l) => {
    cb += l.quantity;
    return [l.price, cb];
  });
  let ca = 0;
  const askPts = asks.map((l) => {
    ca += l.quantity;
    return [l.price, ca];
  });

  const lo = bidPts.length ? bidPts[bidPts.length - 1][0] : mid;
  const hi = askPts.length ? askPts[askPts.length - 1][0] : mid;
  const maxY = Math.max(1, cb, ca);
  const plotH = H - padT;
  const range = hi - lo || 1;
  const dx = (p) => ((p - lo) / range) * W;
  const dy = (v) => padT + plotH - (v / maxY) * plotH;

  const bidPath =
    `M ${dx(mid).toFixed(1)} ${(padT + plotH).toFixed(1)} ` +
    bidPts.map((p) => `L ${dx(p[0]).toFixed(1)} ${dy(p[1]).toFixed(1)}`).join(' ') +
    ` L ${dx(lo).toFixed(1)} ${(padT + plotH).toFixed(1)} Z`;
  const askPath =
    `M ${dx(mid).toFixed(1)} ${(padT + plotH).toFixed(1)} ` +
    askPts.map((p) => `L ${dx(p[0]).toFixed(1)} ${dy(p[1]).toFixed(1)}`).join(' ') +
    ` L ${dx(hi).toFixed(1)} ${(padT + plotH).toFixed(1)} Z`;

  return (
    <div style={{ position: 'relative', width: '100%', height: '100%' }}>
      <svg
        viewBox={`0 0 ${W} ${H}`}
        preserveAspectRatio="none"
        style={{ width: '100%', height: '100%', display: 'block' }}
      >
        <defs>
          <linearGradient id="depthBid" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor={C.green} stopOpacity="0.4" />
            <stop offset="1" stopColor={C.green} stopOpacity="0.02" />
          </linearGradient>
          <linearGradient id="depthAsk" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor={C.red} stopOpacity="0.4" />
            <stop offset="1" stopColor={C.red} stopOpacity="0.02" />
          </linearGradient>
        </defs>
        {bidPts.length > 0 && (
          <path d={bidPath} fill="url(#depthBid)" stroke={C.green} strokeWidth="1.5" />
        )}
        {askPts.length > 0 && (
          <path d={askPath} fill="url(#depthAsk)" stroke={C.red} strokeWidth="1.5" />
        )}
        <line
          x1={dx(mid)}
          y1={padT}
          x2={dx(mid)}
          y2={padT + plotH}
          stroke={C.border3}
          strokeWidth="1"
          strokeDasharray="3 3"
        />
      </svg>
      <div
        style={{
          position: 'absolute',
          top: 10,
          left: '50%',
          transform: 'translateX(-50%)',
          fontFamily: MONO,
          fontSize: 11,
          color: C.muted,
        }}
      >
        mid ${mid != null ? fmtPrice(mid, pairInfo.tickSize) : '--'}
      </div>
      <div
        style={{
          position: 'absolute',
          bottom: 8,
          left: 12,
          fontFamily: MONO,
          fontSize: 11,
          color: C.green,
        }}
      >
        bids · {priceUsd(lo).toFixed(pairInfo.tickSize < 100 ? 4 : 2)}
      </div>
      <div
        style={{
          position: 'absolute',
          bottom: 8,
          right: 12,
          fontFamily: MONO,
          fontSize: 11,
          color: C.red,
        }}
      >
        asks · {priceUsd(hi).toFixed(pairInfo.tickSize < 100 ? 4 : 2)}
      </div>
    </div>
  );
}

function EmptyDepth() {
  return (
    <div
      style={{
        width: '100%',
        height: '100%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: C.muted,
        fontFamily: MONO,
        fontSize: 13,
      }}
    >
      Order book is empty.
    </div>
  );
}
