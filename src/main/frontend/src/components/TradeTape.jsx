import { Panel } from './Panel.jsx';
import { C, MONO } from '../theme.js';
import { fmtPrice, fmtQty, fmtTimeOfDay } from '../format.js';

// The public trade tape. The backend tape carries no aggressor side, so direction is
// inferred by the tick rule (up-tick green, down-tick red) against the prior print.
export function TradeTape({ trades, pairInfo }) {
  return (
    <Panel title="Trade Tape" style={{ flex: 1 }}>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'auto 1fr auto',
          columnGap: 10,
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
        <span style={{ textAlign: 'right' }}>TIME</span>
      </div>
      <div style={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
        {trades.length === 0 && (
          <div style={{ padding: '12px', color: C.muted2, fontFamily: MONO, fontSize: 12 }}>
            Waiting for trades...
          </div>
        )}
        {trades.map((t, i) => {
          const prev = trades[i + 1];
          const up = !prev ? true : t.price >= prev.price;
          const color = up ? C.green : C.red;
          return (
            <div
              key={t._k}
              style={{
                display: 'grid',
                gridTemplateColumns: 'auto 1fr auto',
                columnGap: 10,
                padding: '0 12px',
                height: 21,
                alignItems: 'center',
                fontFamily: MONO,
                fontSize: 12,
                animation: `${up ? 'tflashG' : 'tflashR'} .7s ease-out`,
              }}
            >
              <div style={{ color, fontWeight: 500 }}>{fmtPrice(t.price, pairInfo.tickSize)}</div>
              <div style={{ textAlign: 'right', color: C.text2 }}>{fmtQty(t.quantity)}</div>
              <div style={{ textAlign: 'right', color: C.muted2, fontSize: 11 }}>
                {fmtTimeOfDay(t._t)}
              </div>
            </div>
          );
        })}
      </div>
    </Panel>
  );
}
