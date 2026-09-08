// Money and quantity formatting. Backend prices are scaled integers in micro-USD
// (priceUSD = scaled / 1e6); quantities are whole integer units. All display values
// are derived from these real integers, never fabricated.

export const MICRO = 1_000_000;

export function priceUsd(scaled) {
  return scaled / MICRO;
}

// Decimal places implied by a pair's tick size (in micro-USD): a $0.01 tick shows 2
// places, a $0.000001 tick shows 6. Keeps display exactly as precise as the grid.
export function priceDecimals(tickSize) {
  const tickUsd = tickSize / MICRO;
  const d = Math.round(-Math.log10(tickUsd));
  return Math.min(8, Math.max(2, d));
}

export function fmtUsd(scaled, decimals = 2) {
  return priceUsd(scaled).toLocaleString('en-US', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

export function fmtPrice(scaled, tickSize) {
  return fmtUsd(scaled, priceDecimals(tickSize));
}

export function fmtQty(units) {
  return Number(units).toLocaleString('en-US');
}

export function fmtSignedPct(pct) {
  if (pct === null || pct === undefined || Number.isNaN(pct)) return '--';
  return (pct >= 0 ? '+' : '') + pct.toFixed(2) + '%';
}

// Compact USD for header/volume readouts: $2.41B, $12.4M, $845K, $12.30.
export function fmtCompactUsd(usd) {
  if (usd === null || usd === undefined || Number.isNaN(usd)) return '--';
  const abs = Math.abs(usd);
  if (abs >= 1e9) return '$' + (usd / 1e9).toFixed(2) + 'B';
  if (abs >= 1e6) return '$' + (usd / 1e6).toFixed(2) + 'M';
  if (abs >= 1e3) return '$' + (usd / 1e3).toFixed(1) + 'K';
  return '$' + usd.toFixed(2);
}

export function fmtTimeOfDay(ms) {
  return new Date(ms).toLocaleTimeString('en-US', { hour12: false });
}
