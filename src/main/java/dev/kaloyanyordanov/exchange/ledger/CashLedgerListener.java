package dev.kaloyanyordanov.exchange.ledger;

/**
 * A sink notified on the cash-ledger thread when cash enters or leaves the system,
 * so those movements can be durably audited off the hot path. Defined here (not in
 * the engine or persistence package) to keep the cash ledger free of any dependency
 * on them. Implementations must be non-blocking (they run on the cash thread).
 */
public interface CashLedgerListener {

  /**
   * A deposit was applied.
   *
   * @param account      the credited account
   * @param amount       the amount credited
   * @param newAvailable the account's available cash after the credit
   * @param reference    the payment provider's reference
   */
  void onDeposit(long account, long amount, long newAvailable, String reference);

  /**
   * A withdrawal was applied.
   *
   * @param account      the debited account
   * @param amount       the amount debited
   * @param newAvailable the account's available cash after the debit
   * @param reference    the reference recorded for the withdrawal
   */
  void onWithdrawal(long account, long amount, long newAvailable, String reference);
}
