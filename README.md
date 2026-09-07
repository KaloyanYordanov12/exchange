# exchange

A single-pair trading exchange built around a **single-threaded matching core**.
One dedicated thread owns the order book and in-memory balances; many producers
offer orders to a bounded lock-free MPSC ring buffer, and one consumer drains it.
Correctness comes from the architecture (serial, deterministic core; scaled-integer
money) and is held in place by property-based tests, not by hope.

> Status: **Phase 0 — scaffolding & gates.** No domain logic yet. This phase
> proves every gate that will police the build actually bites.

## Correctness / gates

`./mvnw verify` is the one command that defines "done." It fails the build unless
every gate below passes:

| Gate | Tool | Threshold |
|---|---|---|
| Tests (incl. property-based) | JUnit + jqwik | all green |
| Line / branch coverage | JaCoCo | ≥ 85% line / ≥ 80% branch |
| Mutation score | PIT (`pitest-maven`) | ≥ 70% (enforced from Phase 1) |
| Style | Checkstyle (Google, **error** severity) | 0 violations |
| Static analysis | SpotBugs (effort=max, threshold=low) | 0 findings |

Thresholds ratchet up, never down. PIT enforcement is deferred to Phase 1 (the
first real branching logic); a health endpoint has no meaningful mutants.

Core building blocks proven runnable in Phase 0: **jqwik** (property tests) and
**JCTools** (`MpscArrayQueue`, the ingress ring buffer).

## Build & run

```sh
./mvnw verify        # build + all gates
./mvnw spring-boot:run
```

Health check: `GET /actuator/health` → `200 {"status":"UP"}`.

### Docker

```sh
docker compose up --build
```

## License

[MIT](LICENSE).
