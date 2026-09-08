package dev.kaloyanyordanov.exchange.invariant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.exchange.ledger.CashAccount;
import dev.kaloyanyordanov.exchange.ledger.CashSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class CashInvariantCheckerTest {

  private static boolean passed(CheckReport report, Invariant invariant) {
    return report.results().stream()
        .filter(result -> result.invariant() == invariant)
        .findFirst()
        .orElseThrow()
        .passed();
  }

  @Test
  void conservingSnapshotPasses() {
    // available+reserved = 300+200 = 500 = deposited 800 - withdrawn 300.
    CashSnapshot snapshot =
        new CashSnapshot(
            List.of(new CashAccount(1L, 300L, 200L), new CashAccount(2L, 0L, 0L)), 800L, 300L);
    CheckReport report = CashInvariantChecker.check(snapshot);
    assertThat(report.allPassed()).isTrue();
    assertThat(report.results()).hasSize(2);
  }

  @Test
  void detectsConservationViolation() {
    // Total cash 500 but deposited-withdrawn = 700.
    CashSnapshot snapshot =
        new CashSnapshot(List.of(new CashAccount(1L, 300L, 200L)), 900L, 200L);
    CheckReport report = CashInvariantChecker.check(snapshot);
    assertThat(report.allPassed()).isFalse();
    assertThat(passed(report, Invariant.CASH_CONSERVATION)).isFalse();
  }

  @Test
  void detectsNegativeAvailable() {
    CashSnapshot snapshot =
        new CashSnapshot(List.of(new CashAccount(1L, -100L, 100L)), 0L, 0L);
    CheckReport report = CashInvariantChecker.check(snapshot);
    assertThat(passed(report, Invariant.NO_NEGATIVE_BALANCES)).isFalse();
  }

  @Test
  void detectsNegativeReserved() {
    CashSnapshot snapshot =
        new CashSnapshot(List.of(new CashAccount(1L, 100L, -100L)), 0L, 0L);
    CheckReport report = CashInvariantChecker.check(snapshot);
    assertThat(passed(report, Invariant.NO_NEGATIVE_BALANCES)).isFalse();
  }
}
