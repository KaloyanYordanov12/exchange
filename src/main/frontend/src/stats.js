import { priceUsd } from './format.js';

// Derives the markets-row stats from a real candle series (SEEDED and/or REAL
// candles from the backend). Returns nulls when there is no history rather than
// inventing a price, so the row is fail-honest.
export function deriveStats(series, pairInfo) {
  const candles = (series && series.candles) || [];
  if (candles.length === 0) {
    // No traded/seeded history yet: fall back to the configured reference price
    // (a real backend field), with change/volume unknown.
    return {
      last: pairInfo ? pairInfo.referencePrice : null,
      indicative: true,
      changePct: null,
      volumeUsd: null,
      spark: [],
    };
  }
  const first = candles[0];
  const lastC = candles[candles.length - 1];
  const opening = first.open;
  const last = lastC.close;
  const changePct = opening > 0 ? ((last - opening) / opening) * 100 : null;
  let volumeUsd = 0;
  for (const c of candles) {
    volumeUsd += c.volume * priceUsd(c.close);
  }
  return {
    last,
    indicative: false,
    changePct,
    volumeUsd,
    spark: candles.map((c) => priceUsd(c.close)),
  };
}

// Builds an SVG path (line) and area path for a sparkline over [values], fitted to
// a width x height box. Returns { line, area } path strings, or null if too few points.
export function sparkPaths(values, width, height, pad = 2) {
  if (!values || values.length < 2) return null;
  let lo = Math.min(...values);
  let hi = Math.max(...values);
  if (hi === lo) {
    hi += 1;
    lo -= 1;
  }
  const n = values.length;
  const x = (i) => (i / (n - 1)) * width;
  const y = (v) => pad + (1 - (v - lo) / (hi - lo)) * (height - pad * 2);
  let line = '';
  values.forEach((v, i) => {
    line += (i ? 'L' : 'M') + x(i).toFixed(1) + ' ' + y(v).toFixed(1) + ' ';
  });
  const area =
    line + `L ${width.toFixed(1)} ${height} L 0 ${height} Z`;
  return { line: line.trim(), area };
}
