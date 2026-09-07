package dev.kaloyanyordanov.exchange.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DemoPaymentProviderTest {

  private final DemoPaymentProvider provider = new DemoPaymentProvider();

  @Test
  void depositSucceedsWithReference() {
    PaymentResult result = provider.initiateDeposit(1L, 1_000L);
    assertThat(result.success()).isTrue();
    assertThat(result.reference()).startsWith("demo-deposit-");
  }

  @Test
  void withdrawalSucceedsWithReference() {
    PaymentResult result = provider.initiateWithdrawal(1L, 500L);
    assertThat(result.success()).isTrue();
    assertThat(result.reference()).startsWith("demo-withdrawal-");
  }

  @Test
  void nonPositiveAmountsAreRejected() {
    assertThat(provider.initiateDeposit(1L, 0L).success()).isFalse();
    assertThat(provider.initiateWithdrawal(1L, -1L).success()).isFalse();
  }

  @Test
  void referencesAreUnique() {
    String first = provider.initiateDeposit(1L, 10L).reference();
    String second = provider.initiateDeposit(1L, 10L).reference();
    assertThat(first).isNotEqualTo(second);
  }
}
