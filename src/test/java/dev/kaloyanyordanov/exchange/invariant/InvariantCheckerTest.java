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
      List.of(new AccountBalance(1L, 500L, 10L), new AccountBalance(2L, 300L, 20L));

  private static EngineSnapshot valid() {
    return new EngineSnapshot(
        VALID_BOOK, VALID_RESTING, VALID_ACCOUNTS, 800L, 30L, 1_000L, 1_000L, 10L, 10L);
  }

  private static boolean failed(CheckReport report, Invariant invariant) {
    return report.results().stream()
        .filter(result -> result.invariant() == invariant)
        .findFirst()
        .orElseThrow()
        .passed();
  }

  private static void assertDetects(EngineSnapshot corrupted, Invariant invariant) {
    CheckReport report = InvariantChecker.check(corrupted);
    assertThat(report.allPassed()).isFalse();
    assertThat(failed(report, invariant)).as("%s should have failed", invariant).isFalse();
  }

  @Test
  void validSnapshotPassesEveryInvariant() {
    CheckReport report = InvariantChecker.check(valid());
    assertThat(report.allPassed()).isTrue();
    assertThat(report.results()).hasSize(7).allMatch(InvariantResult::passed);
  }

  @Test
  void detectsCashConservationViolation() {
    assertDetects(
        new EngineSnapshot(
            VALID_BOOK, VALID_RESTING, VALID_ACCOUNTS, 999L, 30L, 1_000L, 1_000L, 10L, 10L),
        Invariant.CASH_CONSERVATION);
  }

  @Test
  void detectsAssetConservationViolation() {
    assertDetects(
        new EngineSnapshot(
            VALID_BOOK, VALID_RESTING, VALID_ACCOUNTS, 800L, 999L, 1_000L, 1_000L, 10L, 10L),
        Invariant.ASSET_CONSERVATION);
  }

  @Test
  void detectsNegativeBalance() {
    // Cash still sums to 800 (conservation holds), but one balance is negative.
    List<AccountBalance> accounts =
        List.of(new AccountBalance(1L, -100L, 10L), new AccountBalance(2L, 900L, 20L));
    assertDetects(
        new EngineSnapshot(
            VALID_BOOK, VALID_RESTING, accounts, 800L, 30L, 1_000L, 1_000L, 10L, 10L),
        Invariant.NO_NEGATIVE_BALANCES);
  }

  @Test
  void detectsOverfilledOrder() {
    List<RestingOrder> resting =
        List.of(new RestingOrder(1L, Side.BUY, 100L, 5L, 6L, 1L)); // remaining 6 > quantity 5
    assertDetects(
        new EngineSnapshot(
            VALID_BOOK, resting, VALID_ACCOUNTS, 800L, 30L, 1_000L, 1_000L, 10L, 10L),
        Invariant.NO_OVERFILL);
  }

  @Test
  void detectsBrokenPriceOrdering() {
    BookSnapshot book =
        new BookSnapshot(
            List.of(new PriceLevel(99L, 3L), new PriceLevel(100L, 10L)), // bids ascending
            List.of(new PriceLevel(101L, 4L)));
    assertDetects(
        new EngineSnapshot(
            book, VALID_RESTING, VALID_ACCOUNTS, 800L, 30L, 1_000L, 1_000L, 10L, 10L),
        Invariant.PRICE_TIME_PRIORITY);
  }

  @Test
  void detectsBrokenFifoWithinPriceLevel() {
    List<RestingOrder> resting =
        List.of(
            new RestingOrder(1L, Side.BUY, 100L, 5L, 5L, 2L),
            new RestingOrder(2L, Side.BUY, 100L, 5L, 5L, 1L)); // sequence goes backwards
    assertDetects(
        new EngineSnapshot(
            VALID_BOOK, resting, VALID_ACCOUNTS, 800L, 30L, 1_000L, 1_000L, 10L, 10L),
        Invariant.PRICE_TIME_PRIORITY);
  }

  @Test
  void detectsCrossedBook() {
    BookSnapshot book =
        new BookSnapshot(List.of(new PriceLevel(101L, 5L)), List.of(new PriceLevel(100L, 5L)));
    assertDetects(
        new EngineSnapshot(
            book, VALID_RESTING, VALID_ACCOUNTS, 800L, 30L, 1_000L, 1_000L, 10L, 10L),
        Invariant.BOOK_NOT_CROSSED);
  }

  @Test
  void detectsUnbalancedTradeCash() {
    assertDetects(
        new EngineSnapshot(
            VALID_BOOK, VALID_RESTING, VALID_ACCOUNTS, 800L, 30L, 1_000L, 999L, 10L, 10L),
        Invariant.TRADES_BALANCE);
  }

  @Test
  void detectsUnbalancedTradeAsset() {
    assertDetects(
        new EngineSnapshot(
            VALID_BOOK, VALID_RESTING, VALID_ACCOUNTS, 800L, 30L, 1_000L, 1_000L, 10L, 9L),
        Invariant.TRADES_BALANCE);
  }
}
