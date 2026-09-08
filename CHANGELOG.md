# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Phase M1 — multi-pair backend (in progress).** Five-pair configuration model:
  `PairProperties` (base, quote, tick, lot, reference price) with a canonical
  `Symbol.pairId()` for routing, and the five standard pairs — BTC/USD, ETH/USD,
  SOL/USD, XRP/USD, DOGE/USD — as the default catalog, spanning five orders of price
  magnitude (BTC ~10^10 down to DOGE ~10^4 micro-USD). Scaled-integer price and
  notional math is proven exact at both the BTC and DOGE magnitudes.
- **Phase M1 — multi-pair backend (in progress).** Engine cash boundary: the
  matching engine no longer owns cash. It settles the asset leg through its per-pair
  `AssetLedger` and the cash leg through the shared `CashLedger` (a message send per
  fill; price-improvement savings released), and a buy's cash is reserved before the
  order enters the book. Balances split into per-pair asset (engine snapshot /
  `AccountUpdated`, now asset-only) and shared cash (cash-ledger snapshot). Invariants
  split accordingly: `InvariantChecker` verifies the six per-pair invariants, and a
  new `CashInvariantChecker` verifies cross-account cash conservation and
  no-negative-cash over the whole cash ledger; `InvariantMonitor` runs both.
  Deposits/withdrawals now flow through the `CashLedger` (audited off the hot path by
  a `CashAuditWorker`); order/trade persistence is unchanged.
- **Phase M1 — multi-pair backend (in progress).** Per-pair `AssetLedger`: an
  asset-only ledger + fill policy owned by one engine's matching thread. The seller
  is capped to the asset it holds (no negative asset); the buyer is unconstrained
  because its cash was reserved in the shared `CashLedger` before the order entered
  the book. Asset never crosses engines, so it stays plain single-thread state.
- **Phase M1 — multi-pair backend (in progress).** Shared cash owner: a
  `CashLedger` actor is the single owner of every account's cash (the quote
  currency), which — unlike per-pair asset holdings — is spendable on any pair and
  so cannot live inside one engine. Five matching threads never touch cash by
  shared memory; they send the actor immutable command messages (deposit,
  withdraw, reserve, release, settle) drained serially off one lock-free MPSC
  ingress, so check-then-commit is atomic without a lock — the same guarantee each
  engine's single-threaded core has. Affordability becomes a buying-power
  **reservation** taken before a buy enters a book; fills settle from reserved to
  the seller, price improvements are released, and reserved cash is never
  withdrawable. Proven race-free: a 1,000-thread contention test spends a fixed
  purse exactly once (no double-spend), a concurrent cross-pair flow by one account
  conserves cash, and a model-based property checks
  `Σ(available+reserved) == Σdeposited − Σwithdrawn` with no negative balance after
  every operation.

- **Phase 8.5 — accounts + deposits/withdrawals (demo provider).** Public account
  registration (`POST /register`): `TraderRegistry` now creates accounts at
  runtime and returns a generated API key **once** (only its bcrypt hash is kept);
  new accounts start at zero. Identity is in-memory (registered accounts do not
  survive a restart); persisting identity is out of v1 scope.
- `PaymentProvider` seam with a single `DemoPaymentProvider` — instant, simulated
  success, **no real money, no external call**. A real adapter would implement the
  interface but is deliberately out of scope. Fail-secure: the demo provider is
  the default unless a real one is explicitly configured (and none exists).
- Deposits and withdrawals are applied as serial, audited events **on the matching
  thread**: new `DepositCash`/`WithdrawCash` commands and `CashDeposited`/
  `CashWithdrawn` events, with `AccountLedger.creditCash`/`withdrawCash`. A
  withdrawal's sufficient-funds check and debit are atomic on that thread, so it
  can never over-draw committed funds or drive a balance negative. `PaymentService`
  orchestrates provider authorization with the serial balance change (the engine is
  the sole authority on funds); the demo provider moves no real money.
- Cash movements are durably audited to an append-only `ledger_transactions` table
  (Flyway `V2`): type (deposit/withdrawal), account, scaled-integer amount, the
  resulting cash balance, the provider reference, and a write timestamp. The async
  persistence worker records them off the matching thread, like the trade tape.
- Authenticated funding endpoints: `POST /accounts/deposit` (202 once authorized
  and enqueued) and `POST /accounts/withdraw` (200 applied, 422 insufficient
  funds), both scaled-integer amounts, behind the API-key filter.

### Changed

- **Deposit-aware cash conservation (invariant 1).** The static law (total cash
  equals the initial total) is replaced by `total_cash == initial + Σdeposits −
  Σwithdrawals`: the engine tracks cumulative deposited/withdrawn totals in the
  `EngineSnapshot`, the `InvariantChecker` verifies against them, and the property
  test now funds via the deposit path and interleaves withdrawals — trades still
  conserve, and the checker is proven to detect a violation of the new law. Asset
  conservation is unchanged (asset has no deposit path; it flows only via a
  one-time genesis endowment).
- **Opening balances funded through the deposit path.** Configured accounts are no
  longer seeded by mutating the ledger off-thread. A `GenesisFunder` deposits each
  account's opening cash through the engine once it has started (audited
  `genesis-*` `CashDeposited` events), while opening asset stays a one-time
  pre-start endowment. The ledger and read model now seed only asset; cash arrives
  via the deposit events — no off-thread balance mutation (§4.4).

- **Phase 8 — consistent engine snapshot.** A `LedgerView` lets the engine
  enumerate all accounts and totals; the engine tracks cumulative trade totals
  and initial totals, and answers an internal `SnapshotRequest` command by
  building an immutable `EngineSnapshot` (book, resting orders, all balances,
  initial + cumulative totals) **on the matching thread**. `requestSnapshot`
  submits through the real ingress and polls for the result — the checker never
  touches the live book or ledger (§4.4).
- `InvariantChecker`: a pure function over an `EngineSnapshot` producing a
  `CheckReport` of per-invariant pass/fail for all seven invariants (cash/asset
  conservation, no negative balances, no overfill, price-time priority, book not
  crossed, trades balance). Snapshots use raw (unvalidated) balance/order records
  so the checker is **proven to detect** a deliberately corrupted snapshot for
  every invariant — not just to pass (§5).
- `InvariantMonitor` runs the checker on demand and continuously (a lightweight
  periodic check that requests a snapshot through the real ingress, never
  affecting matching). A **public, read-only** panel exposes it: `GET /invariants`
  (latest verdict, cheap) and `GET /invariants/check` (fresh) — a visitor can
  watch all seven invariants stay green under load with no admin control.

- **Phase 7 — simulator metric core.** `LatencyPercentiles` (pure, deterministic
  nearest-rank) and a bounded, thread-safe `PercentileTracker` producing a
  `LatencySummary` (p50/p95/p99/max over real samples). Percentiles are computed
  from measured samples, never fabricated, and are exactly unit-testable.
- Simulator engine: a virtual-thread-per-trader `LoadSimulator` that submits
  through the **real ingress** (same `engine.submit` path as the API), records
  the real back-pressure rejection when the queue is full (never bypasses it),
  and generates a balanced random walk of tick/lot-aligned orders so the book
  trades. `SimulatorMetricsSink` measures submit-to-processed latency on the
  matching thread; `RunMetrics`/`MetricsSnapshot` report submitted/accepted/
  rejected/trades, throughput, and p50/p95/p99 — all from real measurement.
- Admin-gated simulator control: `POST /admin/simulator/start` (202, 409 if a run
  is active, 400 on bad config), `POST /admin/simulator/stop`, and
  `GET /admin/simulator/metrics` — behind an `X-Admin-Key` bcrypt gate on
  `/admin/*`. The metrics sink is wired into the event fan-out; a run is never a
  CI gate (heavy load is manual), but the simulator's logic is fully unit-tested.

- **Phase 6 — persistence groundwork.** `OrderAccepted` enriched to carry the
  full order (side/price/quantity/account) so the audit log is meaningful.
  `AsyncEventConsumer`: a bounded-queue, own-thread, batched outbound sink whose
  `publish` is a non-blocking enqueue (drop-and-count on full) — the mechanism
  that keeps a slow consumer (e.g. the database) off the matching hot path.
- Async Postgres persistence, **profile-gated** (`persistence`) so the app runs
  fully in-memory by default (DB never a matching dependency). Flyway migration
  (`accounts`, `orders`, `trades`; scaled-integer BIGINT columns), JPA entities +
  repositories, and a `PersistenceWorker` that maps the audit stream to entities
  and writes them in batched transactions on its own thread. Added to the event
  fan-out only when the profile is active.
- Persistence proven: a Testcontainers Postgres integration test shows orders,
  trades, and account snapshots are durably recorded via the async worker (Flyway
  builds the schema); and an off-hot-path test shows that with the consumer's
  handler blocked (a stalled DB), the engine still processes every order promptly
  while events queue for the worker — matching throughput is independent of DB
  latency. Boot 4 needs the `spring-boot-flyway` module explicitly for Flyway to
  run; tests execute in UTC (this host's zone is the retired "Europe/Kiev").

- **Phase 5 — throttled broadcaster core.** `ThrottledBroadcaster` consumes the
  event stream on the matching thread with only lock-free work (coalesce the
  latest book snapshot; offer trades to a bounded, drop-on-full tape) and flushes
  on its own scheduler thread at a fixed rate (default 10 Hz): one coalesced book
  snapshot plus a bounded trade batch per tick. `AsyncClientConnection` isolates
  each client behind its own bounded buffer + drain thread, so a slow socket
  drops/coalesces its own backlog and never stalls the flusher, other clients, or
  the engine. A `JsonSerializer` seam keeps serialization off the stored state.
- WebSocket transport at `/ws/marketdata` (`spring-boot-starter-websocket`): each
  session becomes an isolated `AsyncClientConnection`, registered with the
  broadcaster and added to the event fan-out. Proven end-to-end (a real client
  receives coalesced book snapshots and a batched trade tape), and that a flood
  of engine events collapses to one snapshot per flush while the engine still
  processes every order.

- **Phase 4 — API egress backbone.** `AccountUpdated` event emitted on the
  matching thread after settlement (pure egress; matching behaviour and
  determinism unchanged), `AccountView` read interface on `Ledger`, a
  `FanoutPublisher` that forwards each event to multiple sinks with per-sink
  failure isolation, and a synchronous lock-free `MarketDataCache` read model
  (latest immutable book snapshot + balances) so HTTP threads read market data
  without ever touching the book or ledger off the matching thread (§4.4).
- Trader identity: `ExchangeProperties` (symbol, ingress capacity, trader roster)
  and a bcrypt-backed `TraderRegistry` that resolves a presented API key to an
  account id (`spring-security-crypto`). Identity only, not full auth.
- REST API on virtual threads: `POST /orders` (submits to the ingress queue,
  202 with order id, **503 when the queue is full**, 400 on invalid/misaligned
  input), `GET /book` (public snapshot from the read model), `GET /accounts/me`
  (the caller's balances). An `X-API-Key` auth filter attributes each order to
  its account (401 on missing/invalid), scoped to `/orders` and `/accounts/*`.
  Spring wiring funds the ledger and seeds the read model from config, then
  starts/stops the engine with the context. Handlers never touch the book.

- **Phase 1 — domain core (pure, single-threaded).** Immutable scaled-integer
  value types in `book`: `Side`, `Symbol` (tick/lot validation), `OrderId`,
  `Order` (immutable `remaining`, `withRemaining`), `Trade` (overflow-checked
  `notional`). No floating point anywhere in the domain.
- PIT mutation testing is now **enforced**: `mutationCoverage` bound to `verify`
  with a 70% threshold (the Phase 0 deferral is lifted).
- `OrderBook`: bids highest-first, asks lowest-first (`TreeMap`), FIFO time
  priority within a level (`ArrayDeque`); best bid/ask, crossed check, immutable
  aggregated `snapshot()`. Non-concurrent by design — owned by one thread.
- `Matcher`: pure, deterministic price-time-priority matching — best opposite
  price first, then earliest at that price; executes at the maker price;
  partial-fills; rests only non-crossing remainders (never leaves the book
  crossed). `FillPolicy` hook injects affordability caps + immediate settlement
  in Phase 3; Phase 1 runs `UNCONSTRAINED`.
- **Phase 2 — matching engine + MPSC ingress.** Sealed `Command`/`SubmitOrder`
  (self-validating; arrival sequence assigned on the matching thread), immutable
  sealed `EngineEvent`s (`OrderAccepted`, `TradeExecuted`, `BookChanged`,
  `OrderRejected`), `SubmitResult` (enqueued / busy / not-running), and the
  `EventPublisher` egress sink.
- `MatchingEngine`: bounded `MpscArrayQueue` ingress; one dedicated matching
  thread that solely owns the book and sequence counters (no locks); `submit`
  returns busy on a full queue and never blocks; drain-and-stop lifecycle;
  per-command core logic (`processCommand`) unit-testable without threads.
- Phase 2 concurrency tests: 12k virtual-thread producers → every order processed
  exactly once (dense `0..n-1` sequences, no loss/duplication) and the final book
  equals the deterministic single-threaded replay of the processed sequence;
  bounded-queue back-pressure returns busy without blocking or losing commands;
  and a jqwik property confirming random concurrent submission stays non-crossed
  and replay-identical. PIT scopes out these timing-dependent tests (they still
  run in full under surefire).
- **Phase 3 — atomic ledger.** `Account` snapshot and `Ledger` (scaled-integer
  cash + asset per account, owned by the matching thread). `Ledger` implements
  `FillPolicy`: `maxBuyerUnits`/`maxSellerUnits` are the serial pre-trade check;
  `onFill` settles equal-and-opposite with exact arithmetic. No lock — atomic by
  virtue of running only on the matching thread.
- Phase 3 jqwik property tests over random funded sequences, one per invariant:
  cash conservation (INV-1), asset conservation (INV-2), no negative balances
  under adversarial under-funded flows (INV-3), and every trade equal-and-opposite
  (INV-7). Plus an end-to-end test settling through the engine's matching thread,
  including an under-funded buyer that fills only what it can afford.
- Phase 1 jqwik property tests over random order sequences, one per invariant:
  book never crossed (INV-4), price-time priority — first fill hits the best,
  earliest resting order (INV-5), no overfill (INV-6), and matching quantity
  conservation (`filled + resting == original` per order; units bought == sold).

- Maven + Spring Boot 4.1.1 project scaffold on Java 25 (Temurin), with the
  Maven wrapper and empty feature packages (`book`, `engine`, `ledger`, `api`,
  `realtime`, `persistence`, `sim`, `invariant`, `config`, `error`).
- Project meta: this changelog, README skeleton, MIT license, and a Conventional
  Commits `.gitmessage` template. `.gitattributes` forces LF on the Maven
  wrapper so CI on Linux runs it.
- `/actuator/health` endpoint with a MockMvc smoke test asserting `200`/`UP`
  (no database, no Testcontainers). Pulls in the `spring-boot-webmvc-test`
  module, which Spring Boot 4 split out of `starter-test`.
- Static-analysis gates bound to `verify`, both failing the build on any
  finding: Checkstyle (committed Google config forced to **error** severity,
  checkstyle 14.1.0) and SpotBugs (effort=Max, threshold=Low, failOnError).
  Both gates proven to bite before the scaffold was made to pass them.
- JaCoCo coverage gate bound to `verify` (jacoco 0.8.15): **85% line / 80%
  branch**, excluding the `Application` bootstrap class. Proven real by
  temporarily un-excluding `Application` and confirming the build went red
  (0.00 line ratio &lt; 0.85), then reverting.
- PIT mutation-testing plumbing (`pitest-maven` 1.30.0 + junit5 plugin 1.2.3),
  runnable via `mvnw org.pitest:pitest-maven:mutationCoverage`. **Enforcement is
  deferred:** the ≥70% mutation-score gate (bound to `verify`) switches on in
  Phase 1, where the first real branching logic (the order book) appears. A
  health endpoint has no meaningful mutants, so a threshold here would be
  vacuous. This is the only pre-authorized Phase 0 gate deferral.
- jqwik property-based testing wired (test scope, 1.10.1) and proven runnable
  and counted inside `mvn verify` with a trivial overflow-safe property. jqwik
  runs cleanly on Spring Boot 4's JUnit Platform 6.0.3. Surefire is configured
  to also include `*Properties.java` so property classes are not silently
  skipped by the default name filter (the correctness phases rely on this).
- JCTools (`jctools-core` 4.0.7, compile scope) wired and proven runnable with a
  unit test that offers and polls a `MpscArrayQueue<Long>` in FIFO order. This is
  the ingress ring buffer for later phases.
- Multi-stage `Dockerfile`: `eclipse-temurin:25-jdk` build stage (runs
  `./mvnw -B clean package`, tests included) and a slim, non-root
  `eclipse-temurin:25-jre` runtime. `.dockerignore` keeps the context lean.
  Image builds and the container serves `/actuator/health` → `200 UP`.
- `docker-compose.yml` wiring the app plus a pinned `postgres:18.6` service for
  Phase 6's benefit; the app does not connect to it yet.
- GitHub Actions CI (`.github/workflows/ci.yml`): runs `./mvnw -B verify` on JDK
  25 (Temurin, `actions/setup-java@v5`) with Maven caching, on pushes and pull
  requests. The `mvnw` executable bit is tracked so `./mvnw` runs on Linux CI.
