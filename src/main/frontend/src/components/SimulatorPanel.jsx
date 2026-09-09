import { useEffect, useRef, useState } from 'react';
import { api } from '../api.js';
import { Panel } from './Panel.jsx';
import { C, MONO, SANS } from '../theme.js';

// Drives the pair's real load simulator via /admin/simulator/* and renders its real,
// measured metrics (throughput gauge, matching-latency percentiles). No synthetic
// numbers: every value is read back from the running engine.
export function SimulatorPanel({ pairInfo, mid }) {
  const [traders, setTraders] = useState(250);
  const [thinkSeconds, setThinkSeconds] = useState(6); // avg think-time; 0 = max speed
  const [aggression, setAggression] = useState(0.3); // taker fraction; rest is passive
  // Server-enforced bounds (read from /simulator/limits). The slider caps mirror what
  // the server will actually run: it clamps trader count down to the cap and think-time
  // up to the floor regardless, so these only keep the UI honest. Local defaults are
  // unrestricted (2000-trader UI default, 0s floor = max speed allowed).
  const [traderMax, setTraderMax] = useState(2000);
  const [minThinkSeconds, setMinThinkSeconds] = useState(0);
  const [continuous, setContinuous] = useState(true); // default: keep the market alive
  const [durationSeconds, setDurationSeconds] = useState(60); // used when not continuous
  const [metrics, setMetrics] = useState(null);
  const [msg, setMsg] = useState('');
  const busyRef = useRef(false);

  // Randomized human-like think-time: uniform in +/-50% of the chosen average, so a
  // large roster behaves like a real market. 0 = max-throughput stress test.
  const minThinkMillis = Math.round(thinkSeconds * 1000 * 0.5);
  const maxThinkMillis = Math.round(thinkSeconds * 1000 * 1.5);

  // Read the server's enforced limits once and pull the sliders in to match them.
  useEffect(() => {
    let alive = true;
    api
      .simulatorLimits()
      .then((lim) => {
        if (!alive || !lim) return;
        const cap = Number(lim.maxTraders);
        // Unlimited locally (Integer.MAX_VALUE) -> keep the UI default; otherwise cap it.
        const uiMax = Number.isFinite(cap) ? Math.min(cap, 2000) : 2000;
        setTraderMax(uiMax);
        setTraders((t) => Math.min(t, uiMax));
        const floorSec = Number(lim.minThinkMillis) / 1000;
        if (Number.isFinite(floorSec) && floorSec > 0) {
          setMinThinkSeconds(floorSec);
          setThinkSeconds((s) => Math.max(s, floorSec));
        }
      })
      .catch(() => {
        /* limits are advisory for the UI; the server still enforces them */
      });
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => {
    let alive = true;
    let timer = null;
    async function poll() {
      try {
        const m = await api.simulatorMetrics(pairInfo.pairId);
        if (alive) setMetrics(m);
      } catch {
        /* transient; keep last */
      }
    }
    poll();
    timer = setInterval(poll, 1000);
    return () => {
      alive = false;
      if (timer) clearInterval(timer);
    };
  }, [pairInfo.pairId]);

  const running = !!(metrics && metrics.running);

  async function launch() {
    if (busyRef.current) return;
    busyRef.current = true;
    try {
      if (running) {
        await api.stopSimulator(pairInfo.pairId);
        setMsg('Stopping run...');
      } else {
        const tick = pairInfo.tickSize;
        const midScaled =
          mid != null ? Math.max(tick, Math.round(mid / tick) * tick) : pairInfo.referencePrice;
        await api.startSimulator({
          pair: pairInfo.pairId,
          traderCount: traders,
          ordersPerTrader: 0, // unbounded: the run ends on the duration or on Stop
          orderRatePerSecond: 0,
          durationMillis: continuous ? 0 : Math.max(1, Math.round(durationSeconds)) * 1000,
          midPrice: midScaled,
          priceSpreadTicks: 25,
          minQuantity: 1,
          maxQuantity: 5,
          randomSeed: Math.floor(Math.random() * 1_000_000_000),
          maxLatencySamples: 100000,
          minThinkMillis,
          maxThinkMillis,
          aggression,
        });
        setMsg('');
      }
    } catch (e) {
      setMsg(
        e.status === 409
          ? 'A run is already active on this pair.'
          : e.status === 401
            ? 'Admin key invalid.'
            : `Error: ${e.message}`,
      );
    } finally {
      busyRef.current = false;
    }
  }

  const thru = metrics ? metrics.throughputPerSecond : 0;
  const lat = metrics ? metrics.latency : null;

  return (
    <Panel
      title="Load Simulator"
      right={
        <span
          style={{
            fontFamily: MONO,
            fontSize: 11,
            color: running ? C.green : C.muted2,
          }}
        >
          {running ? '● running' : 'idle'}
        </span>
      }
      style={{ flex: '0 0 auto' }}
    >
      <div style={{ padding: 14, display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontSize: 12, color: C.muted }}>Simulated traders</span>
          <span style={{ fontFamily: MONO, fontSize: 15, color: C.amber }}>
            {traders.toLocaleString('en-US')}
          </span>
        </div>
        <input
          type="range"
          min="10"
          max={traderMax}
          step="10"
          value={traders}
          onChange={(e) => setTraders(Math.min(+e.target.value, traderMax))}
          style={{ width: '100%', accentColor: C.amber }}
        />
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontSize: 12, color: C.muted }}>Trader think time</span>
          <span style={{ fontFamily: MONO, fontSize: 13, color: C.amber }}>
            {thinkSeconds === 0
              ? 'max speed'
              : `~${(minThinkMillis / 1000).toFixed(1)}-${(maxThinkMillis / 1000).toFixed(1)}s`}
          </span>
        </div>
        <input
          type="range"
          min={minThinkSeconds}
          max="12"
          step="0.5"
          value={thinkSeconds}
          onChange={(e) => setThinkSeconds(Math.max(+e.target.value, minThinkSeconds))}
          style={{ width: '100%', accentColor: C.amber }}
        />
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontSize: 12, color: C.muted }}>Aggression</span>
          <span style={{ fontFamily: MONO, fontSize: 13, color: C.amber }}>
            {Math.round(aggression * 100)}% taker
          </span>
        </div>
        <input
          type="range"
          min="0"
          max="1"
          step="0.05"
          value={aggression}
          onChange={(e) => setAggression(+e.target.value)}
          style={{ width: '100%', accentColor: C.amber }}
        />
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontSize: 12, color: C.muted }}>Run</span>
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
              [true, 'Continuous'],
              [false, 'Timed'],
            ].map(([val, label]) => {
              const active = val === continuous;
              return (
                <button
                  key={label}
                  onClick={() => setContinuous(val)}
                  style={{
                    cursor: 'pointer',
                    border: 'none',
                    borderRadius: 6,
                    padding: '5px 11px',
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
        {!continuous && (
          <label style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <span style={{ fontSize: 12, color: C.muted }}>Duration (s)</span>
            <input
              type="number"
              min="1"
              value={durationSeconds}
              onChange={(e) => setDurationSeconds(+e.target.value)}
              style={{
                background: C.inputBg,
                border: `1px solid ${C.border2}`,
                borderRadius: 7,
                color: C.text,
                fontFamily: MONO,
                fontSize: 13,
                padding: '6px 10px',
                width: 90,
                textAlign: 'right',
                outline: 'none',
              }}
            />
          </label>
        )}
        <button
          onClick={launch}
          style={
            running
              ? {
                  cursor: 'pointer',
                  border: '1px solid #3a1f26',
                  borderRadius: 8,
                  padding: 11,
                  fontFamily: SANS,
                  fontWeight: 600,
                  fontSize: 13,
                  color: '#ff8a95',
                  background: '#1a0f12',
                }
              : {
                  cursor: 'pointer',
                  border: 'none',
                  borderRadius: 8,
                  padding: 11,
                  fontFamily: SANS,
                  fontWeight: 600,
                  fontSize: 13,
                  color: '#1a1204',
                  background: `linear-gradient(180deg, ${C.amberHi}, ${C.amber})`,
                  boxShadow: '0 4px 16px rgba(255,176,32,.28)',
                }
          }
        >
          {running ? 'Stop Load Test' : 'Launch Load Test'}
        </button>

        <Gauge value={thru} />

        {msg && <div style={{ fontSize: 11, color: C.muted, fontFamily: MONO }}>{msg}</div>}

        <div style={{ borderTop: `1px solid ${C.card}`, paddingTop: 11 }}>
          <div style={{ fontSize: 10, color: C.muted2, letterSpacing: '.1em', marginBottom: 4 }}>
            MATCHING LATENCY
          </div>
          <LatBar label="p50" ms={lat ? lat.p50Nanos / 1e6 : null} />
          <LatBar label="p95" ms={lat ? lat.p95Nanos / 1e6 : null} />
          <LatBar label="p99" ms={lat ? lat.p99Nanos / 1e6 : null} />
        </div>
      </div>
    </Panel>
  );
}

function Gauge({ value }) {
  const pct = Math.min(1, (value || 0) / 6000);
  const polar = (cx, cy, r, aDeg) => {
    const a = (aDeg * Math.PI) / 180;
    return [cx + r * Math.cos(a), cy + r * Math.sin(a)];
  };
  const p0 = polar(80, 84, 64, 180);
  const p1 = polar(80, 84, 64, 360);
  const pv = polar(80, 84, 64, 180 + 180 * pct);
  return (
    <div style={{ display: 'flex', justifyContent: 'center' }}>
      <svg viewBox="0 0 160 104" style={{ width: '80%', height: 'auto' }}>
        <defs>
          <linearGradient id="gaugeg" x1="0" x2="1">
            <stop offset="0" stopColor={C.amberDeep2} />
            <stop offset="1" stopColor={C.amberHi} />
          </linearGradient>
        </defs>
        <path
          d={`M ${p0[0]} ${p0[1]} A 64 64 0 0 1 ${p1[0]} ${p1[1]}`}
          fill="none"
          stroke="#1b2027"
          strokeWidth="10"
          strokeLinecap="round"
        />
        <path
          d={`M ${p0[0]} ${p0[1]} A 64 64 0 0 1 ${pv[0]} ${pv[1]}`}
          fill="none"
          stroke="url(#gaugeg)"
          strokeWidth="10"
          strokeLinecap="round"
          style={{ filter: 'drop-shadow(0 0 6px rgba(255,176,32,.6))' }}
        />
        <text x="80" y="78" textAnchor="middle" fill={C.text} style={{ font: `600 26px ${MONO}` }}>
          {Math.round(value || 0).toLocaleString('en-US')}
        </text>
        <text
          x="80"
          y="96"
          textAnchor="middle"
          fill={C.muted3}
          style={{ font: `500 9.5px ${SANS}`, letterSpacing: '.14em' }}
        >
          ORDERS / SEC
        </text>
      </svg>
    </div>
  );
}

function LatBar({ label, ms }) {
  const has = ms != null && Number.isFinite(ms);
  const scale = 30;
  const w = has ? Math.min(1, ms / scale) : 0;
  const col = !has ? C.muted2 : ms < 2 ? C.green : ms < 8 ? C.amber : C.red;
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 9, margin: '6px 0' }}>
      <div style={{ width: 30, color: C.muted3, fontFamily: MONO, fontSize: 11 }}>{label}</div>
      <div
        style={{
          flex: 1,
          height: 6,
          background: '#151a21',
          borderRadius: 3,
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            width: `${w * 100}%`,
            height: '100%',
            background: col,
            boxShadow: `0 0 8px ${col}`,
            transition: 'width .2s',
          }}
        />
      </div>
      <div style={{ width: 66, textAlign: 'right', color: col, fontFamily: MONO, fontSize: 12 }}>
        {has ? `${ms.toFixed(2)}ms` : '--'}
      </div>
    </div>
  );
}
