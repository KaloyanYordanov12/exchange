import { useEffect, useMemo, useState } from 'react';
import { api } from '../api.js';
import { C, SANS, MONO, chipFor, ASSET_NAMES } from '../theme.js';
import { fmtPrice, fmtSignedPct, fmtCompactUsd } from '../format.js';
import { deriveStats, sparkPaths } from '../stats.js';
import { navigate, Link } from '../router.jsx';

const DAY_SECONDS = 24 * 60 * 60;

export function MarketsPage() {
  const [rows, setRows] = useState([]); // [{ info, stats }]
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [tab, setTab] = useState('all'); // all | gainers | losers

  useEffect(() => {
    let alive = true;
    let timer = null;

    async function loadStatsFor(pairs) {
      const now = Math.floor(Date.now() / 1000);
      const results = await Promise.all(
        pairs.map(async (info) => {
          try {
            const series = await api.candles(info.pairId, '1h', now - DAY_SECONDS, now);
            return { info, stats: deriveStats(series, info) };
          } catch {
            return { info, stats: deriveStats(null, info) };
          }
        }),
      );
      if (alive) setRows(results);
    }

    async function init() {
      try {
        const pairs = await api.pairs();
        if (!alive) return;
        setRows(pairs.map((info) => ({ info, stats: deriveStats(null, info) })));
        setLoading(false);
        await loadStatsFor(pairs);
        timer = setInterval(() => loadStatsFor(pairs), 8000);
      } catch (e) {
        if (alive) {
          setError(e.message || 'Failed to load markets');
          setLoading(false);
        }
      }
    }
    init();
    return () => {
      alive = false;
      if (timer) clearInterval(timer);
    };
  }, []);

  const header = useMemo(() => {
    let volume = 0;
    let haveVolume = false;
    let gainers = 0;
    for (const r of rows) {
      if (r.stats.volumeUsd != null) {
        volume += r.stats.volumeUsd;
        haveVolume = true;
      }
      if (r.stats.changePct != null && r.stats.changePct > 0) gainers += 1;
    }
    return { volume: haveVolume ? volume : null, pairs: rows.length, gainers };
  }, [rows]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    let out = rows;
    if (q) {
      out = out.filter((r) => {
        const name = (ASSET_NAMES[r.info.base] || '').toLowerCase();
        return (
          r.info.pairId.toLowerCase().includes(q) ||
          r.info.base.toLowerCase().includes(q) ||
          name.includes(q)
        );
      });
    }
    if (tab === 'gainers') out = out.filter((r) => (r.stats.changePct ?? 0) > 0);
    if (tab === 'losers') out = out.filter((r) => (r.stats.changePct ?? 0) < 0);
    return out;
  }, [rows, search, tab]);

  return (
    <div
      style={{
        minHeight: '100vh',
        background: `radial-gradient(1200px 600px at 78% -10%, ${C.panelBg2} 0%, ${C.pageBg} 60%)`,
        color: C.text,
        fontFamily: SANS,
        padding: '20px 24px 40px',
      }}
    >
      <div style={{ maxWidth: 1200, margin: '0 auto', minWidth: 720 }}>
        <TopBar header={header} />
        <Controls
          search={search}
          setSearch={setSearch}
          tab={tab}
          setTab={setTab}
          counts={{
            all: rows.length,
            gainers: rows.filter((r) => (r.stats.changePct ?? 0) > 0).length,
            losers: rows.filter((r) => (r.stats.changePct ?? 0) < 0).length,
          }}
        />
        <Table rows={filtered} loading={loading} error={error} />
      </div>
    </div>
  );
}

function TopBar({ header }) {
  return (
    <header
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 24,
        marginBottom: 20,
        flexWrap: 'wrap',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <div
          style={{
            width: 34,
            height: 34,
            borderRadius: 9,
            background: `linear-gradient(135deg, ${C.amber}, ${C.amberDeep})`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#1a1204',
            fontWeight: 600,
            fontSize: 18,
            boxShadow: '0 0 20px rgba(255,176,32,.35)',
          }}
        >
          ◆
        </div>
        <div>
          <div style={{ fontWeight: 600, fontSize: 17, letterSpacing: '.02em' }}>
            APEX <span style={{ color: C.muted2 }}>·</span>{' '}
            <span style={{ color: C.muted }}>Markets</span>
          </div>
          <div
            style={{
              fontSize: 10.5,
              color: C.muted2,
              letterSpacing: '.14em',
              textTransform: 'uppercase',
            }}
          >
            Spot Exchange
          </div>
        </div>
      </div>
      <div style={{ flex: 1 }} />
      <HeaderStat label="24H VOLUME" value={fmtCompactUsd(header.volume)} />
      <HeaderStat label="PAIRS" value={String(header.pairs || '--')} />
      <HeaderStat label="GAINERS" value={String(header.gainers)} accent={C.green} />
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '8px 13px',
          background: '#0c1a13',
          border: '1px solid #14351f',
          borderRadius: 8,
        }}
      >
        <span
          style={{
            width: 8,
            height: 8,
            borderRadius: '50%',
            background: C.green,
            animation: 'pulse 1.8s infinite',
          }}
        />
        <span style={{ fontFamily: MONO, fontSize: 12, color: C.green, letterSpacing: '.06em' }}>
          LIVE
        </span>
      </div>
    </header>
  );
}

function HeaderStat({ label, value, accent }) {
  return (
    <div style={{ textAlign: 'right' }}>
      <div style={{ fontSize: 10, color: C.muted2, letterSpacing: '.1em' }}>{label}</div>
      <div style={{ fontFamily: MONO, fontSize: 16, color: accent || C.text }}>{value}</div>
    </div>
  );
}

function Controls({ search, setSearch, tab, setTab, counts }) {
  const tabs = [
    ['all', 'All', counts.all],
    ['gainers', 'Gainers', counts.gainers],
    ['losers', 'Losers', counts.losers],
  ];
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 14,
        marginBottom: 12,
        flexWrap: 'wrap',
      }}
    >
      <input
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder="Search markets"
        style={{
          background: C.inputBg,
          border: `1px solid ${C.border2}`,
          borderRadius: 9,
          color: C.text,
          fontSize: 13,
          padding: '10px 14px',
          width: 260,
          outline: 'none',
        }}
      />
      <div style={{ flex: 1 }} />
      <div
        style={{
          display: 'flex',
          gap: 3,
          background: C.inputBg,
          border: `1px solid ${C.border}`,
          borderRadius: 9,
          padding: 3,
        }}
      >
        {tabs.map(([key, label, count]) => {
          const active = key === tab;
          return (
            <button
              key={key}
              onClick={() => setTab(key)}
              style={{
                cursor: 'pointer',
                border: 'none',
                borderRadius: 6,
                padding: '7px 14px',
                fontSize: 12.5,
                fontWeight: 500,
                color: active ? '#1a1204' : C.muted,
                background: active ? C.amber : 'transparent',
                boxShadow: active ? '0 0 12px rgba(255,176,32,.3)' : 'none',
              }}
            >
              {label}
              <span style={{ opacity: 0.7, marginLeft: 6 }}>{count}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}

const GRID = '34px 2fr 1.4fr 1.3fr 1.5fr 130px 96px';

function Table({ rows, loading, error }) {
  return (
    <section
      style={{
        background: C.panelBg,
        border: `1px solid ${C.border}`,
        borderRadius: 12,
        boxShadow: '0 8px 24px rgba(0,0,0,.4)',
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: GRID,
          gap: 10,
          padding: '11px 18px',
          borderBottom: `1px solid ${C.border}`,
          fontSize: 10,
          letterSpacing: '.09em',
          color: C.muted2,
        }}
      >
        <span>#</span>
        <span>MARKET</span>
        <span style={{ textAlign: 'right' }}>LAST PRICE</span>
        <span style={{ textAlign: 'right' }}>24H CHANGE</span>
        <span style={{ textAlign: 'right' }}>24H VOLUME</span>
        <span style={{ textAlign: 'right' }}>LAST 24H</span>
        <span />
      </div>
      {error && (
        <div style={{ padding: 24, color: C.red, fontFamily: MONO, fontSize: 13 }}>
          Failed to load markets: {error}
        </div>
      )}
      {!error && loading && (
        <div style={{ padding: 24, color: C.muted, fontFamily: MONO, fontSize: 13 }}>
          Loading markets...
        </div>
      )}
      {!error &&
        !loading &&
        rows.map((r, i) => <MarketRow key={r.info.pairId} rank={i + 1} row={r} />)}
      {!error && !loading && rows.length === 0 && (
        <div style={{ padding: 24, color: C.muted, fontFamily: MONO, fontSize: 13 }}>
          No markets match your search.
        </div>
      )}
    </section>
  );
}

function MarketRow({ rank, row }) {
  const { info, stats } = row;
  const [chipBg, glyphColor, glyph] = chipFor(info.base);
  const up = (stats.changePct ?? 0) >= 0;
  const changeColor = stats.changePct == null ? C.muted : up ? C.green : C.red;
  const to = `/trade/${info.pairId}`;

  return (
    <Link
      to={to}
      style={{
        display: 'grid',
        gridTemplateColumns: GRID,
        gap: 10,
        alignItems: 'center',
        padding: '14px 18px',
        borderBottom: `1px solid ${C.hair2}`,
        color: 'inherit',
        cursor: 'pointer',
      }}
    >
      <span style={{ fontFamily: MONO, fontSize: 12, color: C.muted2 }}>{rank}</span>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0 }}>
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
            fontFamily: MONO,
            fontWeight: 600,
            fontSize: 15,
            flex: '0 0 auto',
          }}
        >
          {glyph}
        </div>
        <div style={{ minWidth: 0 }}>
          <div style={{ fontWeight: 600, fontSize: 14 }}>
            {info.base} <span style={{ color: C.muted2, fontWeight: 400 }}>/ {info.quote}</span>
          </div>
          <div style={{ fontSize: 11.5, color: C.muted }}>
            {ASSET_NAMES[info.base] || info.base}
          </div>
        </div>
      </div>
      <div style={{ textAlign: 'right', fontFamily: MONO, fontSize: 14, color: C.text }}>
        {stats.last == null ? '--' : '$' + fmtPrice(stats.last, info.tickSize)}
      </div>
      <div style={{ textAlign: 'right' }}>
        <span
          style={{
            display: 'inline-block',
            fontFamily: MONO,
            fontSize: 12.5,
            color: changeColor,
            background:
              stats.changePct == null
                ? 'transparent'
                : up
                  ? 'rgba(14,206,134,.10)'
                  : 'rgba(255,87,102,.10)',
            border: `1px solid ${
              stats.changePct == null ? 'transparent' : up ? 'rgba(14,206,134,.25)' : 'rgba(255,87,102,.25)'
            }`,
            borderRadius: 7,
            padding: '4px 9px',
          }}
        >
          {fmtSignedPct(stats.changePct)}
        </span>
      </div>
      <div style={{ textAlign: 'right', fontFamily: MONO, fontSize: 13, color: C.text2 }}>
        {stats.volumeUsd == null ? '--' : fmtCompactUsd(stats.volumeUsd)}
      </div>
      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
        <Sparkline values={stats.spark} up={up} />
      </div>
      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
        <button
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            navigate(to);
          }}
          style={{
            cursor: 'pointer',
            border: `1px solid ${C.border2}`,
            background: C.card,
            color: C.text,
            borderRadius: 8,
            padding: '8px 16px',
            fontSize: 12.5,
            fontWeight: 600,
          }}
        >
          Trade
        </button>
      </div>
    </Link>
  );
}

function Sparkline({ values, up }) {
  const width = 120;
  const height = 34;
  const paths = sparkPaths(values, width, height);
  const color = up ? C.green : C.red;
  if (!paths) {
    return (
      <span style={{ fontFamily: MONO, fontSize: 11, color: C.muted2 }}>--</span>
    );
  }
  const gid = `sg-${Math.round(values[0] * 1000)}-${up ? 'u' : 'd'}`;
  return (
    <svg width={width} height={height} style={{ display: 'block' }}>
      <defs>
        <linearGradient id={gid} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={color} stopOpacity="0.22" />
          <stop offset="1" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={paths.area} fill={`url(#${gid})`} />
      <path d={paths.line} fill="none" stroke={color} strokeWidth="1.5" strokeLinejoin="round" />
    </svg>
  );
}
