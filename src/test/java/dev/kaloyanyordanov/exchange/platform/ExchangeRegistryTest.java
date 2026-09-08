package dev.kaloyanyordanov.exchange.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.kaloyanyordanov.exchange.api.PlacementOutcome;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.candle.CandleStore;
import dev.kaloyanyordanov.exchange.config.ExchangeProperties.PairProperties;
import dev.kaloyanyordanov.exchange.config.ExchangeProperties.TraderProperties;
import dev.kaloyanyordanov.exchange.ledger.CashLedger;
import dev.kaloyanyordanov.exchange.payment.DemoPaymentProvider;
import dev.kaloyanyordanov.exchange.payment.PaymentService;
import dev.kaloyanyordanov.exchange.realtime.PairBroadcasters;
import java.time.Duration;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExchangeRegistryTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private CashLedger cash;
  private ExchangeRegistry registry;

  @BeforeEach
  void setUp() {
    cash = new CashLedger(4096);
    PaymentService payment = new PaymentService(new DemoPaymentProvider(), cash, TIMEOUT);
    List<PairProperties> pairs =
        List.of(
            new PairProperties("AAA", "USD", 1L, 1L, 100L),
            new PairProperties("BBB", "USD", 1L, 1L, 100L));
    PairBroadcasters broadcasters =
        new PairBroadcasters(
            List.of("AAA-USD", "BBB-USD"), new ObjectMapper()::writeValueAsString, 10, 8, 64);
    List<TraderProperties> traders =
        List.of(
            new TraderProperties(1L, "h", 1_000_000L, 1_000L),
            new TraderProperties(2L, "h", 1_000_000L, 1_000L));
    registry =
        new ExchangeRegistry(
            pairs, 4096, cash, payment, broadcasters, null, new CandleStore(), traders, TIMEOUT,
            1000L, 2000L);
    registry.start();
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    registry.stop();
  }

  @Test
  void routesByPairAndKeepsEnginesIndependent() {
    // A resting sell on AAA must never appear on BBB's book.
    assertThat(registry.place("AAA-USD", 2L, Side.SELL, 100L, 5L).status())
        .isEqualTo(PlacementOutcome.Status.ACCEPTED);

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(registry.book("AAA-USD").asks()).isNotEmpty());
    assertThat(registry.book("BBB-USD").asks()).isEmpty();
    assertThat(registry.book("BBB-USD").bids()).isEmpty();
  }

  @Test
  void tradeOnOnePairSettlesAndBothPairsStayInvariant() {
    // Account 2 sells on AAA; account 1 reserves and buys, crossing.
    registry.place("AAA-USD", 2L, Side.SELL, 100L, 5L);
    assertThat(registry.place("AAA-USD", 1L, Side.BUY, 100L, 5L).status())
        .isEqualTo(PlacementOutcome.Status.ACCEPTED);

    // Every per-pair invariant (six) plus the two cross-account cash invariants hold
    // on both engines.
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(registry.checkInvariants("AAA-USD").allPassed()).isTrue();
              assertThat(registry.checkInvariants("AAA-USD").results()).hasSize(8);
              assertThat(registry.checkInvariants("BBB-USD").allPassed()).isTrue();
            });
  }

  @Test
  void balanceReportsSharedCashAndPerPairAssets() {
    ExchangeRegistry.AccountBalances balances = registry.balance(1L);
    assertThat(balances.accountId()).isEqualTo(1L);
    assertThat(balances.cash()).isEqualTo(1_000_000L); // genesis cash, shared
    assertThat(balances.holdings()).hasSize(2); // one per pair
    assertThat(balances.holdings()).allSatisfy(h -> assertThat(h.asset()).isEqualTo(1_000L));
  }

  @Test
  void unknownPairIsReported() {
    assertThat(registry.hasPair("AAA-USD")).isTrue();
    assertThat(registry.hasPair("ZZZ-USD")).isFalse();
  }
}
