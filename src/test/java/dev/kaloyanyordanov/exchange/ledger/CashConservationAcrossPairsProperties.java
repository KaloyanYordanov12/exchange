package dev.kaloyanyordanov.exchange.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Invariant — deposit-aware cash conservation across all pairs. Over any random
 * valid sequence of deposits, withdrawals, reservations, releases, and settlements
 * (the cross-pair cash operations the five engines issue), the single-owner cash
 * ledger keeps {@code Σ(available + reserved) == Σdeposited − Σwithdrawn} and no
 * balance ever goes negative — checked after every operation, against an
 * independent model.
 */
class CashConservationAcrossPairsProperties {

  // Generous so the many per-operation snapshots do not time out under heavy CI load.
  private static final Duration TIMEOUT = Duration.ofSeconds(15);
  private static final int ACCOUNTS = 4;

  enum Kind {
    DEPOSIT,
    WITHDRAW,
    RESERVE,
    RELEASE,
    SETTLE
  }

  record Op(Kind kind, long account, long other, long amount) {}

  @Provide
  Arbitrary<List<Op>> operations() {
    Arbitrary<Kind> kinds = Arbitraries.of(Kind.values());
    Arbitrary<Long> accounts = Arbitraries.longs().between(0L, ACCOUNTS - 1L);
    Arbitrary<Long> others = Arbitraries.longs().between(0L, ACCOUNTS - 1L);
    Arbitrary<Long> amounts = Arbitraries.longs().between(1L, 1_000L);
    Arbitrary<Op> op = Combinators.combine(kinds, accounts, others, amounts).as(Op::new);
    return op.list().ofMinSize(0).ofMaxSize(40);
  }

  @Property(tries = 30)
  void cashIsConservedAndNonNegativeAfterEveryOperation(@ForAll("operations") List<Op> ops)
      throws InterruptedException {
    CashLedger ledger = new CashLedger(1 << 12);
    ledger.start();
    try {
      Map<Long, long[]> model = new HashMap<>(); // account -> {available, reserved}
      long deposited = 0L;
      long withdrawn = 0L;

      for (Op op : ops) {
        long[] a = model.computeIfAbsent(op.account(), id -> new long[2]);
        switch (op.kind()) {
          case DEPOSIT -> {
            ledger.deposit(op.account(), op.amount(), "d");
            a[0] += op.amount();
            deposited += op.amount();
          }
          case WITHDRAW -> {
            WithdrawalResult expected =
                a[0] >= op.amount()
                    ? WithdrawalResult.APPLIED
                    : WithdrawalResult.INSUFFICIENT_FUNDS;
            WithdrawalResult result = ledger.withdraw(op.account(), op.amount(), "w", TIMEOUT);
            assertThat(result).isEqualTo(expected);
            if (expected == WithdrawalResult.APPLIED) {
              a[0] -= op.amount();
              withdrawn += op.amount();
            }
          }
          case RESERVE -> {
            ReservationOutcome expected =
                a[0] >= op.amount()
                    ? ReservationOutcome.RESERVED
                    : ReservationOutcome.INSUFFICIENT_FUNDS;
            assertThat(ledger.reserve(op.account(), op.amount(), TIMEOUT)).isEqualTo(expected);
            if (expected == ReservationOutcome.RESERVED) {
              a[0] -= op.amount();
              a[1] += op.amount();
            }
          }
          case RELEASE -> {
            long amount = Math.min(op.amount(), a[1]);
            if (amount > 0) {
              ledger.release(op.account(), amount);
              a[1] -= amount;
              a[0] += amount;
            }
          }
          case SETTLE -> {
            long amount = Math.min(op.amount(), a[1]);
            if (amount > 0) {
              long[] b = model.computeIfAbsent(op.other(), id -> new long[2]);
              ledger.settle(op.account(), op.other(), amount);
              a[1] -= amount;
              b[0] += amount;
            }
          }
          default -> throw new IllegalStateException();
        }

        CashSnapshot snapshot = ledger.snapshot(TIMEOUT).orElseThrow();
        // The ledger matches the independent model exactly.
        for (Map.Entry<Long, long[]> entry : model.entrySet()) {
          CashAccount account =
              snapshot.accounts().stream()
                  .filter(x -> x.accountId() == entry.getKey())
                  .findFirst()
                  .orElse(new CashAccount(entry.getKey(), 0L, 0L));
          assertThat(account.available()).isEqualTo(entry.getValue()[0]);
          assertThat(account.reserved()).isEqualTo(entry.getValue()[1]);
          assertThat(account.available()).isGreaterThanOrEqualTo(0L);
          assertThat(account.reserved()).isGreaterThanOrEqualTo(0L);
        }
        // Conservation holds after every step.
        assertThat(snapshot.totalCash()).isEqualTo(deposited - withdrawn);
        assertThat(snapshot.cumulativeDeposited()).isEqualTo(deposited);
        assertThat(snapshot.cumulativeWithdrawn()).isEqualTo(withdrawn);
      }
    } finally {
      ledger.stop();
    }
  }
}
