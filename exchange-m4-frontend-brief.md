# BRIEF — Phase M4: Frontend Port (Markets + Terminal + Charts)

**SPEND CEILING: $0.** No real money, no real market data. Every value on screen comes from the real five-engine backend or the labeled candle API. Verify no real key is needed to run.

**Inherits:** `exchange-preflight.md` + `exchange-multipair-plan.md`. Full gate set on `mvn verify`. §4.4 unaffected (frontend doesn't touch the core). §5: **no fabricated data on screen** — the markets page, terminal, and charts render real backend data; the only "synthetic" data is the pre-launch candles, which the backend already marks SEEDED and the UI must show as delineated.

**This is a re-skin + wire, not a redesign.** Apply the approved Claude Design look (files in this directory: `Markets.dc.html`, `Exchange Terminal.dc.html`, `Price Chart.dc.html` — these are Claude Design prototypes using `{{ }}`/`<sc-*>` templating and `support.js`; they are the **visual target**, NOT drop-in code) onto real React components wired to the live backend. Preserve every bit of real data flow; do not invent data.

**Read first (report before building):** the current SPA (if any) and how it's built/served (frontend-maven-plugin into the jar); the backend's real endpoints and WebSocket topics from M1-M3 and earlier — markets/pairs list, per-pair book snapshot + live updates, trade tape stream, place-order (with auth + backpressure 503), account/balances, deposit, simulator control + metrics, invariant checker (per-pair), and the candle query API (pair + timeframe + range, with the SEEDED/REAL marker). Report the exact endpoint/topic names so the wiring matches reality.

---

## Design tokens (from the approved files — reproduce exactly)

- **Fonts:** IBM Plex Sans (UI/labels), IBM Plex Mono (all numbers/data), via Google Fonts. Tabular-nums on numeric cells.
- **Backgrounds:** page `#0a0d12`; deeper panels `#0a0c0f`/`#0f1319`; cards/panels `#161b22`/`#1c2129`/`#12161d`.
- **Hero accent (amber):** `#ffb020` (lighter `#ffc65a`, orange variants `#ff7a2e`/`#ff8a3c`). Used for the active states, the Launch button, the SEEDED/LIVE divider, focus.
- **Market up/green:** `#0ece86`. **Down/red:** `#ff5766`.
- **Text:** primary `#e8ecf1`, secondary `#c3cad4`, muted `#8b95a3`/`#5a6472`/`#6b7482`. Borders `#232a33`/`#2a323d`.
- Match the panel styling, spacing, and layout hierarchy shown in the approved terminal: chart dominant (left) with the trade panel beneath it, order book + trade tape as the middle column, load simulator + invariant panel as the right column.

## What to build

### M4.1 — Markets page (the landing)
- Route `/` (or `/markets`): the five pairs (BTC, ETH, SOL, XRP, DOGE), each row: icon, pair, name, last price, 24h change (green/red pill), 24h volume, a sparkline preview, and a Trade action. Working search/filter and All/Gainers/Losers tabs. Header stats strip (total 24h volume, pair count, gainers count).
- All values from the real backend (per-pair price/change/volume). Clicking a row routes to that pair's terminal (`/trade/:pair`).

### M4.2 — Pair-aware terminal
- Route `/trade/:pair`: the combined terminal layout from the approved design, driven by the selected pair's real engine over WebSocket:
  - **Header:** pair, live price, 24h change, best bid/ask/spread.
  - **Order book:** live bids/asks with depth bars and the spread row (throttled snapshots over WebSocket).
  - **Trade tape:** live executions, green/red.
  - **Trade panel:** limit price + amount + order value, Buy/Sell — **actually places orders** via the real endpoint, handling accepted/rejected/backpressure(503), requiring the trader key.
  - **Load simulator panel:** trader-count control + Launch, wired to the real simulator control endpoint; live throughput gauge + p50/p95/p99 from real metrics. (On the deployed box this will be admin-gated / capped — for now wire it to the real control; the deploy step sets the public ceiling.)
  - **Invariant panel:** the seven checks for this pair from the real per-pair invariant checker; green/red live.
- Everything per-pair; switching pairs (or navigating from markets) loads that engine's data.

### M4.3 — The chart (real charting library)
- **Use a real charting library** (e.g. TradingView Lightweight Charts, or an equivalent lightweight OSS candlestick library), skinned to match the approved design's look (amber accent, green/red candles, the dark palette, IBM Plex Mono labels). Hand-drawn SVG candles are NOT the goal — a real library gives crosshair, volume pane, time axis, and zoom correctly.
- **This is the one sanctioned new dependency for the frontend** (a charting library). Pin it; if the chosen library is problematic, that's a wall, don't hand-roll a worse chart.
- Features (all from the real candle API): candlestick / line / depth toggle; timeframe switcher (1m, 5m, 15m, 1h, 4h, 1d, 1w, 1M, 1y); volume histogram; crosshair with OHLC+Vol readout and axis labels; and the **SEEDED/LIVE divider** — render seeded (pre-launch) candles visually delineated (dimmer / a marked boundary) from real candles, using the backend's SEEDED/REAL marker. Live candles update as trades occur.

## Rules
- Re-skin + wire only: apply the approved look, wire to real endpoints. No redesign of the approved components; no fabricated data.
- The charting library is the only new frontend dependency; nothing else without a wall.
- SPA builds via the existing frontend-maven-plugin into the jar; `mvn verify` green (backend gates unaffected; frontend build succeeds).
- Writing style for any prose: no em-dashes, American spelling. No `Claude-Session` trailer.
- The trader key / admin handling stays in-memory only (the gateway lesson), never persisted to browser storage.

## Exit / handoff
- `mvn verify` green; SPA builds.
- Report: the real endpoints/topics each view is wired to; the charting library chosen and why; confirmation the SEEDED/LIVE delineation uses the backend's real marker; confirmation no data is fabricated (every screen value traces to a real endpoint). Note anything that came close to needing a backend change and how it was avoided (frontend shouldn't require core changes; if it does, that's a wall).
- Tree committed, green, unpushed. Deploy is the separate manual step (M5).
