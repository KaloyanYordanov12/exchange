# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

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
