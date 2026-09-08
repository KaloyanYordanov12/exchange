package dev.kaloyanyordanov.exchange.invariant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.exchange.book.BookSnapshot;
import dev.kaloyanyordanov.exchange.book.PriceLevel;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.engine.AccountBalance;
import dev.kaloyanyordanov.exchange.engine.EngineSnapshot;
import dev.kaloyanyordanov.exchange.engine.RestingOrder;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvariantCheckerTest {

  private static final BookSnapshot VALID_BOOK =
      new BookSnapshot(
          List.of(new PriceLevel(100L, 10L), new PriceLevel(99L, 3L)),
          List.of(new PriceLevel(101L, 4L), new PriceLevel(102L, 2L)));
  private static final List<RestingOrder> VALID_RESTING =
      List.of(
          new RestingOrder(1L, Side.BUY, 100L, 5L, 5L, 1L),
          new RestingOrder(2L, Side.BUY, 100L, 5L, 5L, 2L),
          new RestingOrder(3L, Side.SELL, 101L, 4L, 4L, 3L));
  private static final List<AccountBalance> VALID_ACCOUNTS =
      List.of(new AccountBalance(1L, 10L), new AccountBalance(2L, 20L));

  /** A snapshot whose book, resting orders, asset totals, and trade counters are consistent. */
  private static EngineSnapshot valid() {
    return snapshot(VALID_BOOK, VALID_RESTING, VALID_ACCOUNTS, 30L);
  }

  private static EngineSnapshot snapshot(
      BookSnapshot book, List<RestingOrder> resting, List<AccountBalance> accounts, long asset) {
    return new EngineSnapshot(book, resting, accounts, asset, 1_000L, 1_000L, 10L, 10L);
  }

  private static boolean passed(CheckReport report, Invariant invariant) {
    return report.results().stream()
        .filter(result -> result.invariant() == invariant)
        .findFirst()
        .orElseThrow()
        .passed();
  }

  private static void assertDetects(EngineSnapshot corrupted, Invariant invariant) {
    CheckReport report = InvariantChecker.check(corrupted);
    assertThat(report.allPassed()).isFalse();
    assertThat(passed(report, invariant)).as("%s should have failed", invariant).isFalse();
  }

  @Test
  void validSnapshotPassesEverySixInvariants() {
    CheckReport report = InvariantChecker.check(valid());
    assertThat(report.allPassed()).isTrue();
    assertThat(report.results()).hasSize(6).allMatch(InvariantResult::passed);
    assertThat(report.results().stream().map(InvariantResult::invariant))
        .doesNotContain(Invariant.CASH_CONSERVATION);
  }

  @Test
  void detectsAssetConservationViolation() {
    assertDetects(
        snapshot(VALID_BOOK, VALID_RESTING, VALID_ACCOUNTS, 999L), Invariant.ASSET_CONSERVATION);
  }

  @Test
  void detectsNegativeAsset() {
    List<AccountBalance> accounts =
        List.of(new AccountBalance(1L, -5L), new AccountBalance(2L, 35L));
    assertDetects(
        snapshot(VALID_BOOK, VALID_RESTING, accounts, 30L), Invariant.NO_NEGATIVE_BALANCES);
  }

  @Test
  void detectsOverfilledOrder() {
    List<RestingOrder> resting =
        List.of(new RestingOrder(1L, Side.BUY, 100L, 5L, 6L, 1L)); // remaining 6 > quantity 5
    assertDetects(snapshot(VALID_BOOK, resting, VALID_ACCOUNTS, 30L), Invariant.NO_OVERFILL);
  }

  @Test
  void detectsBrokenPriceOrdering() {
    BookSnapshot book =
        new BookSnapshot(
            List.of(new PriceLevel(99L, 3L), new PriceLevel(100L, 10L)), // bids ascending
            List.of(new PriceLevel(101L, 4L)));
    assertDetects(
        snapshot(book, VALID_RESTING, VALID_ACCOUNTS, 30L), Invariant.PRICE_TIME_PRIORITY);
  }

  @Test
  void detectsBrokenFifoWithinPriceLevel() {
    List<RestingOrder> resting =
        List.of(
            new RestingOrder(1L, Side.BUY, 100L, 5L, 5L, 2L),
            new RestingOrder(2L, Side.BUY, 100L, 5L, 5L, 1L)); // sequence goes backwards
    assertDetects(
        snapshot(VALID_BOOK, resting, VALID_ACCOUNTS, 30L), Invariant.PRICE_TIME_PRIORITY);
  }

  @Test
  void detectsCrossedBook() {
    BookSnapshot book =
        new BookSnapshot(List.of(new PriceLevel(101L, 5L)), List.of(new PriceLevel(100L, 5L)));
    assertDetects(snapshot(book, VALID_RESTING, VALID_ACCOUNTS, 30L), Invariant.BOOK_NOT_CROSSED);
  }

  @Test
  void detectsUnbalancedTradeCash() {
    EngineSnapshot corrupted =
        new EngineSnapshot(VALID_BOOK, VALID_RESTING, VALID_ACCOUNTS, 30L, 1_000L, 999L, 10L, 10L);
    assertDetects(corrupted, Invariant.TRADES_BALANCE);
  }

  @Test
  void detectsUnbalancedTradeAsset() {
    EngineSnapshot corrupted =
        new EngineSnapshot(VALID_BOOK, VALID_RESTING, VALID_ACCOUNTS, 30L, 1_000L, 1_000L, 10L, 9L);
    assertDetects(corrupted, Invariant.TRADES_BALANCE);
  }
}
