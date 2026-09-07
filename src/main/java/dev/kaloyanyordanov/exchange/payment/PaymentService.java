package dev.kaloyanyordanov.exchange.payment;

import dev.kaloyanyordanov.exchange.engine.MatchingEngine;
import dev.kaloyanyordanov.exchange.engine.SubmitResult;
import dev.kaloyanyordanov.exchange.engine.WithdrawalOutcome;
import java.time.Duration;
import java.util.Objects;

/**
 * Orchestrates deposits and withdrawals across the {@link PaymentProvider} and the
 * {@link MatchingEngine}, keeping the balance-changing step serial on the matching
 * thread.
 *
 * <p><b>Ordering.</b> Both operations authorize with the provider first to obtain
 * a reference, then apply the balance change on the matching thread, which is the
 * sole authority on funds:
 *
 * <ul>
 *   <li><b>Deposit</b> — the provider authorizes the incoming funds, then the
 *       engine credits the account (a serial, audited {@code CashDeposited} event).
 *   <li><b>Withdrawal</b> — the provider authorizes a reference, then the engine
 *       performs the sufficient-funds check and debit atomically on the matching
 *       thread. The debit is the authority: it can never over-draw committed funds
 *       or drive a balance negative. The demo provider moves no real money, so a
 *       real adapter must add its settlement step <em>after</em> this debit
 *       succeeds — never before.
 * </ul>
 *
 * <p>The system is fail-secure: if the provider declines, nothing is credited or
 * debited.
 */
public final class PaymentService {

  private final PaymentProvider provider;
  private final MatchingEngine engine;
  private final Duration withdrawalTimeout;

  /**
   * Creates a payment service.
   *
   * @param provider          the payment provider (the demo provider in this build)
   * @param engine            the matching engine that applies the balance change
   * @param withdrawalTimeout how long to wait for the matching thread's withdrawal
   *     outcome
   */
  public PaymentService(
      PaymentProvider provider, MatchingEngine engine, Duration withdrawalTimeout) {
    this.provider = Objects.requireNonNull(provider, "provider");
    this.engine = Objects.requireNonNull(engine, "engine");
    this.withdrawalTimeout = Objects.requireNonNull(withdrawalTimeout, "withdrawalTimeout");
  }

  /**
   * Deposits {@code amount} into {@code accountId}: the provider authorizes it,
   * then the credit is enqueued for the matching thread.
   *
   * @param accountId the account to credit
   * @param amount    the amount in scaled integer quote units (must be positive)
   * @return the funding result
   */
  public FundingResult deposit(long accountId, long amount) {
    PaymentResult authorization = provider.initiateDeposit(accountId, amount);
    if (!authorization.success()) {
      return new FundingResult(FundingStatus.PROVIDER_DECLINED, authorization.reference());
    }
    SubmitResult submit = engine.deposit(accountId, amount, authorization.reference());
    if (submit != SubmitResult.ENQUEUED) {
      return new FundingResult(FundingStatus.UNAVAILABLE, authorization.reference());
    }
    return new FundingResult(FundingStatus.ACCEPTED, authorization.reference());
  }

  /**
   * Withdraws {@code amount} from {@code accountId}: the provider authorizes a
   * reference, then the matching thread performs the sufficient-funds check and
   * debit atomically.
   *
   * @param accountId the account to debit
   * @param amount    the amount in scaled integer quote units (must be positive)
   * @return the funding result
   */
  public FundingResult withdraw(long accountId, long amount) {
    PaymentResult authorization = provider.initiateWithdrawal(accountId, amount);
    if (!authorization.success()) {
      return new FundingResult(FundingStatus.PROVIDER_DECLINED, authorization.reference());
    }
    WithdrawalOutcome outcome =
        engine.withdraw(accountId, amount, authorization.reference(), withdrawalTimeout);
    FundingStatus status =
        switch (outcome) {
          case APPLIED -> FundingStatus.APPLIED;
          case INSUFFICIENT_FUNDS -> FundingStatus.INSUFFICIENT_FUNDS;
          case UNAVAILABLE -> FundingStatus.UNAVAILABLE;
        };
    return new FundingResult(status, authorization.reference());
  }
}
