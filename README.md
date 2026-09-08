# exchange

A multi-pair trading platform built around **single-threaded matching cores**. Each
pair (BTC/USD, ETH/USD, SOL/USD, XRP/USD, DOGE/USD) is a real, independent engine:
one dedicated thread owns that pair's order book and per-pair asset ledger, and many
producers offer orders to a bounded lock-free MPSC ring buffer that one consumer
drains. Correctness comes from the architecture (serial, deterministic cores;
scaled-integer money) and is held in place by property-based tests, not by hope.

**Cash is the one thing shared across pairs**, and it is never shared by memory: a
single-owner `CashLedger` actor holds every account's cash, and the five matching
threads settle through it by message passing. So an account can trade on all five
pairs at once and its cash is never raced, double-spent, or driven negative - proven
by a concurrent cross-pair test.

## Not real market data (by design)

- **Prices come from each pair's own simulated order flow**, not a real market feed.
  Recognizable pairs at realistic magnitudes make it look real; this note keeps it
  honest. No real money, no real chains, no paid APIs.
- **Pre-launch candle history is synthetic and labeled.** When seeding is enabled it
  backfills the long chart timeframes with candles marked `SEEDED`; every candle from
  launch forward is a real trade, marked `REAL`. The candle API exposes the boundary
  so the chart can delineate the two. Seeding is off by default
  (`exchange.seed.enabled`), so a real run shows only real trades.

## Correctness / gates

`./mvnw verify` is the one command that defines "done." It fails the build unless
every gate below passes:

| Gate | Tool | Threshold |
|---|---|---|
| Tests (incl. property-based) | JUnit + jqwik | all green |
| Line / branch coverage | JaCoCo | >= 85% line / >= 80% branch |
| Mutation score | PIT (`pitest-maven`) | >= 70% |
| Style | Checkstyle (Google, **error** severity) | 0 violations |
| Static analysis | SpotBugs (effort=max, threshold=low) | 0 findings |

Thresholds ratchet up, never down. The seven invariants are each covered by a
property-based test: six per-pair invariants over each engine's snapshot (asset
conservation, no negative balances, no overfill, price-time priority, book not
crossed, trades balance) and cross-account cash conservation over the shared cash
ledger. Candle invariants are property-tested too.

## API (public unless noted)

| Endpoint | Purpose |
|---|---|
| `GET /pairs` | the five markets |
| `GET /book?pair=` | a pair's order book |
| `POST /orders` (key) | place a limit order (pair in the body) |
| `GET /accounts/me` (key) | shared cash + per-pair asset holdings |
| `POST /accounts/{deposit,withdraw}` (key) | fund the shared cash balance |
| `GET /candles?pair=&timeframe=&from=&to=` | OHLCV candles + SEEDED/REAL boundary |
| `GET /invariants?pair=` | a pair's live invariant panel |
| `POST /admin/simulator/*` (admin) | drive a pair's load simulator, read real metrics |

## Build & run

```sh
./mvnw verify        # build + all gates
./mvnw spring-boot:run
```

Health check: `GET /actuator/health` -> `200 {"status":"UP"}`.

### Docker

```sh
docker compose up --build
```

## License

[MIT](LICENSE).
