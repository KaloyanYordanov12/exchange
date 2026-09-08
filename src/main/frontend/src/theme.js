// Design tokens lifted from the Claude Design prototypes (Markets / Exchange
// Terminal / Price Chart). Single source of truth for colours and per-pair chips.

export const C = {
  pageBg: '#0a0d12',
  deepBg: '#0a0c0f',
  panelBg: '#0f1319',
  panelBg2: '#12161d',
  card: '#161b22',
  card2: '#1c2129',
  inputBg: '#0a0d12',
  border: '#1c2129',
  border2: '#232a33',
  border3: '#2a323d',
  hair: '#14181f',
  hair2: '#161b22',
  text: '#e8ecf1',
  text2: '#c3cad4',
  muted: '#8b95a3',
  muted2: '#5a6472',
  muted3: '#6b7482',
  amber: '#ffb020',
  amberHi: '#ffc65a',
  amberDeep: '#ff7a2e',
  amberDeep2: '#ff8a3c',
  green: '#0ece86',
  greenBright: '#14e08f',
  red: '#ff5766',
  redBright: '#ff6472',
};

// [chipBackground, glyphColor, glyph] per base asset, matching the Markets design.
export const CHIPS = {
  BTC: ['#ffb020', '#1a1204', '₿'],
  ETH: ['#7b8cff', '#0a0e1a', 'Ξ'],
  SOL: ['#14e0b0', '#04150f', '◎'],
  XRP: ['#8b95a3', '#0a0d12', '✕'],
  DOGE: ['#f2c744', '#1a1404', 'Ð'],
};

// Full asset names for the markets listing.
export const ASSET_NAMES = {
  BTC: 'Bitcoin',
  ETH: 'Ethereum',
  SOL: 'Solana',
  XRP: 'Ripple',
  DOGE: 'Dogecoin',
};

export function chipFor(base) {
  return CHIPS[base] || ['#8b95a3', '#0a0d12', base.slice(0, 1)];
}

export const SANS = "'IBM Plex Sans', system-ui, -apple-system, sans-serif";
export const MONO = "'IBM Plex Mono', ui-monospace, 'SF Mono', monospace";
