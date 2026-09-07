package dev.kaloyanyordanov.exchange.payment;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The <b>only</b> {@link PaymentProvider} implementation and the deployed default:
 * instant, simulated success, with <b>no real money and no external call</b>.
 *
 * <p>This is deliberate. A real provider (a card processor, a crypto rail, a bank
 * rail) would be a drop-in adapter implementing {@link PaymentProvider}, but it is
 * <b>intentionally out of scope</b> and not built. The system is fail-secure: with
 * no real provider configured (and none exists), this demo provider is used, so
 * there is no path to real money.
 */
public final class DemoPaymentProvider implements PaymentProvider {

  private final AtomicLong sequence = new AtomicLong();

  @Override
  public PaymentResult initiateDeposit(long accountId, long amount) {
    return simulate("demo-deposit", amount);
  }

  @Override
  public PaymentResult initiateWithdrawal(long accountId, long amount) {
    return simulate("demo-withdrawal", amount);
  }

  private PaymentResult simulate(String prefix, long amount) {
    if (amount <= 0) {
      return new PaymentResult(false, prefix + "-rejected");
    }
    return new PaymentResult(true, prefix + "-" + sequence.incrementAndGet());
  }
}
