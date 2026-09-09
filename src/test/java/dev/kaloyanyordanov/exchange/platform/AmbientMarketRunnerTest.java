package dev.kaloyanyordanov.exchange.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.exchange.sim.SimulatorConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AmbientMarketRunnerTest {

  private final ExchangeRegistry registry = mock(ExchangeRegistry.class);

  private static List<PairInfo> pairs(int n) {
    List<PairInfo> out = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      out.add(new PairInfo("P" + i + "-USD", "P" + i, "USD", 10L, 1L, 100_000L));
    }
    return out;
  }

  private AmbientMarketRunner runner(int ambient, int cap) {
    return new AmbientMarketRunner(registry, List.of(1L, 2L), ambient, cap, 42L);
  }

  @Test
  void offByDefaultStartsNothing() {
    AmbientMarketRunner runner = runner(0, Integer.MAX_VALUE);
    runner.start();
    verify(registry, never()).startSimulator(any(), any());
    assertThat(runner.startedCount()).isZero();
  }

  @Test
  void spreadsTradersAcrossPairsAsContinuousPacedRuns() {
    when(registry.pairs()).thenReturn(pairs(5));
    AmbientMarketRunner runner = runner(10, Integer.MAX_VALUE);
    ArgumentCaptor<SimulatorConfig> captor = ArgumentCaptor.forClass(SimulatorConfig.class);

    runner.start();

    verify(registry, times(5)).startSimulator(any(), captor.capture());
    assertThat(runner.startedCount()).isEqualTo(5);
    for (SimulatorConfig config : captor.getAllValues()) {
      assertThat(config.traderCount()).isEqualTo(2); // 10 / 5
      assertThat(config.ordersPerTrader()).isZero(); // unbounded
      assertThat(config.maxDurationMillis()).isZero(); // continuous
      assertThat(config.minThinkMillis()).isEqualTo(2_000L);
      assertThat(config.maxThinkMillis()).isEqualTo(8_000L);
      assertThat(config.aggression()).isEqualTo(0.30);
      assertThat(config.midPrice()).isEqualTo(100_000L);
    }
  }

  @Test
  void unevenSplitDistributesTheRemainder() {
    when(registry.pairs()).thenReturn(pairs(4));
    ArgumentCaptor<SimulatorConfig> captor = ArgumentCaptor.forClass(SimulatorConfig.class);

    runner(6, Integer.MAX_VALUE).start(); // 6 / 4 -> 2,2,1,1

    verify(registry, times(4)).startSimulator(any(), captor.capture());
    assertThat(captor.getAllValues().stream().map(SimulatorConfig::traderCount))
        .containsExactly(2, 2, 1, 1);
  }

  @Test
  void clampsEachShareToThePublicCap() {
    when(registry.pairs()).thenReturn(pairs(2));
    ArgumentCaptor<SimulatorConfig> captor = ArgumentCaptor.forClass(SimulatorConfig.class);

    runner(100, 20).start(); // share 50 each, capped to 20

    verify(registry, times(2)).startSimulator(any(), captor.capture());
    assertThat(captor.getAllValues())
        .allSatisfy(c -> assertThat(c.traderCount()).isEqualTo(20));
  }

  @Test
  void skipsPairsAlreadyRunning() {
    when(registry.pairs()).thenReturn(pairs(2));
    doThrow(new IllegalStateException("already running"))
        .when(registry)
        .startSimulator(eq("P0-USD"), any());

    AmbientMarketRunner runner = runner(4, Integer.MAX_VALUE);
    runner.start();

    assertThat(runner.startedCount()).isEqualTo(1); // only P1-USD tracked
    runner.stop();
    verify(registry).stopSimulator("P1-USD");
    verify(registry, never()).stopSimulator("P0-USD");
    assertThat(runner.startedCount()).isZero();
  }
}
