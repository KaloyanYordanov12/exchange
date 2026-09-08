package dev.kaloyanyordanov.exchange.payment;

import dev.kaloyanyordanov.exchange.ledger.CashLedger;
import dev.kaloyanyordanov.exchange.ledger.WithdrawalResult;
import java.time.Duration;
import java.util.Objects;

/**
 * Orchestrates deposits and withdrawals across the {@link PaymentProvider} and the
 * shared {@link CashLedger}, keeping the balance-changing step serial on the cash
 * ledger's single thread.
 *
 * <p><b>Ordering.</b> Both operations authorize with the provider first to obtain a
 * reference, then apply the balance change on the cash thread, which is the sole
 * authority on funds:
 *
 * <ul>
 *   <li><b>Deposit</b> - the provider authorizes the incoming funds, then the cash
 *       ledger credits the account's available cash.
 *   <li><b>Withdrawal</b> - the provider authorizes a reference, then the cash ledger
 *       performs the sufficient-funds check and debit atomically. The debit is the
 *       authority: it can never over-draw, it touches only available (not reserved)
 *       cash, and the demo provider moves no real money. A real adapter must add its
 *       settlement step after this debit succeeds, never before.
 * </ul>
 *
 * <p>The system is fail-secure: if the provider declines, nothing is credited or
 * debited.
 */
public final class PaymentService {

  private final PaymentProvider provider;
  private final CashLedger cashLedger;
  private final Duration withdrawalTimeout;

  /**
   * Creates a payment service.
   *
   * @param provider          the payment provider (the demo provider in this build)
   * @param cashLedger        the shared cash ledger that applies the balance change
   * @param withdrawalTimeout how long to wait for the cash thread's withdrawal outcome
   */
  public PaymentService(
      PaymentProvider provider, CashLedger cashLedger, Duration withdrawalTimeout) {
    this.provider = Objects.requireNonNull(provider, "provider");
    this.cashLedger = Objects.requireNonNull(cashLedger, "cashLedger");
    this.withdrawalTimeout = Objects.requireNonNull(withdrawalTimeout, "withdrawalTimeout");
  }

  /**
   * Deposits {@code amount} into {@code accountId}: the provider authorizes it, then
   * the credit is enqueued for the cash ledger.
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
    boolean enqueued = cashLedger.deposit(accountId, amount, authorization.reference());
    FundingStatus status = enqueued ? FundingStatus.ACCEPTED : FundingStatus.UNAVAILABLE;
    return new FundingResult(status, authorization.reference());
  }

  /**
   * Withdraws {@code amount} from {@code accountId}: the provider authorizes a
   * reference, then the cash ledger performs the sufficient-funds check and debit
   * atomically.
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
    WithdrawalResult outcome =
        cashLedger.withdraw(accountId, amount, authorization.reference(), withdrawalTimeout);
    FundingStatus status =
        switch (outcome) {
          case APPLIED -> FundingStatus.APPLIED;
          case INSUFFICIENT_FUNDS -> FundingStatus.INSUFFICIENT_FUNDS;
          case UNAVAILABLE -> FundingStatus.UNAVAILABLE;
        };
    return new FundingResult(status, authorization.reference());
  }
}
