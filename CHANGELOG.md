# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

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
