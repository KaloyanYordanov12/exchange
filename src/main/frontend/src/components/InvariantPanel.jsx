import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { Panel } from './Panel.jsx';
import { C, MONO, SANS } from '../theme.js';

const LABELS = {
  CASH_CONSERVATION: 'Cash conserved',
  ASSET_CONSERVATION: 'Asset conserved',
  NO_NEGATIVE_BALANCES: 'No negative balances',
  NO_OVERFILL: 'No overfill',
  PRICE_TIME_PRIORITY: 'Price-time priority',
  BOOK_NOT_CROSSED: 'Book not crossed',
  TRADES_BALANCE: 'Trades balanced',
};

// The public per-pair invariant panel. Polls /invariants and shows the live verdict;
// an unavailable snapshot is shown honestly rather than as a false green.
export function InvariantPanel({ pairInfo }) {
  const [report, setReport] = useState(null);

  useEffect(() => {
    let alive = true;
    let timer = null;
    async function poll() {
      try {
        const r = await api.invariants(pairInfo.pairId);
        if (alive) setReport(r);
      } catch {
        /* transient */
      }
    }
    poll();
    timer = setInterval(poll, 1000);
    return () => {
      alive = false;
      if (timer) clearInterval(timer);
    };
  }, [pairInfo.pairId]);

  const results = report && report.results ? report.results : [];
  const passed = results.filter((r) => r.passed).length;
  const allOk = report && report.available && report.allPassed;

  return (
    <Panel
      title="Invariants"
      right={
        <span
          style={{
            fontFamily: MONO,
            fontSize: 11,
            color: allOk ? C.green : report && !report.available ? C.muted2 : C.red,
          }}
        >
          {!report
            ? '...'
            : !report.available
              ? 'unavailable'
              : `${passed}/${results.length} OK`}
        </span>
      }
      style={{ flex: 1 }}
    >
      <div style={{ flex: 1, minHeight: 0, overflow: 'auto' }}>
        {report && !report.available && (
          <div style={{ padding: 14, color: C.muted, fontFamily: MONO, fontSize: 12 }}>
            Snapshot momentarily unavailable (engine saturated); retrying.
          </div>
        )}
        {results.map((r) => (
          <div
            key={r.invariant}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '9.5px 14px',
              borderBottom: `1px solid ${C.card}`,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <span
                style={{
                  width: 7,
                  height: 7,
                  borderRadius: '50%',
                  flex: '0 0 auto',
                  background: r.passed ? C.green : C.red,
                  boxShadow: `0 0 8px ${r.passed ? C.green : C.red}`,
                }}
              />
              <span style={{ fontFamily: SANS, fontSize: 12.5, color: C.text2 }}>
                {LABELS[r.invariant] || r.invariant}
              </span>
            </div>
            <span
              style={{
                fontFamily: MONO,
                fontSize: 11,
                color: r.passed ? C.muted2 : C.red,
                maxWidth: 140,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {r.passed ? r.detail || 'ok' : 'FAIL'}
            </span>
          </div>
        ))}
      </div>
    </Panel>
  );
}
