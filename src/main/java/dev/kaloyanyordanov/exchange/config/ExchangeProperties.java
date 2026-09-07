package dev.kaloyanyordanov.exchange.config;

import dev.kaloyanyordanov.exchange.book.Symbol;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised configuration for the exchange: the traded pairs, ingress capacity,
 * the trader roster (API-key hashes and opening balances), and the real-time
 * broadcast settings.
 *
 * @param symbol          the primary traded symbol (legacy single-pair wiring)
 * @param pairs           the traded pairs (the five-pair platform); defaults to
 *     {@link #STANDARD_PAIRS} when unset
 * @param ingressCapacity the ingress queue capacity (rounded to a power of two)
 * @param traders         the configured traders
 * @param broadcast       the real-time broadcast settings
 */
@ConfigurationProperties(prefix = "exchange")
public record ExchangeProperties(
    SymbolProperties symbol,
    List<PairProperties> pairs,
    int ingressCapacity,
    List<TraderProperties> traders,
    BroadcastProperties broadcast) {

  /**
   * The five default pairs of the platform, spanning a wide price magnitude (BTC
   * ~10^4 down to DOGE ~10^-2). Prices are scaled integers in micro-USD (1e-6 USD),
   * so all magnitudes stay exact in {@code long} arithmetic.
   */
  public static final List<PairProperties> STANDARD_PAIRS =
      List.of(
          new PairProperties("BTC", "USD", 10_000L, 1L, 40_000_000_000L),
          new PairProperties("ETH", "USD", 1_000L, 1L, 2_000_000_000L),
          new PairProperties("SOL", "USD", 100L, 1L, 100_000_000L),
          new PairProperties("XRP", "USD", 10L, 1L, 500_000L),
          new PairProperties("DOGE", "USD", 1L, 1L, 80_000L));

  /** Defensive copies; defaults for unset lists and broadcast settings. */
  public ExchangeProperties {
    pairs = List.copyOf(pairs == null || pairs.isEmpty() ? STANDARD_PAIRS : pairs);
    traders = traders == null ? List.of() : List.copyOf(traders);
    broadcast = broadcast == null ? new BroadcastProperties(0, 0, 0, 0) : broadcast;
  }

  /**
   * Symbol configuration.
   *
   * @param base     the base asset
   * @param quote    the quote asset
   * @param tickSize the minimum price increment in ticks
   * @param lotSize  the minimum quantity increment in units
   */
  public record SymbolProperties(String base, String quote, long tickSize, long lotSize) {}

  /**
   * One traded pair: its symbol scaling rules plus a reference price used to seed
   * simulated order flow and synthetic history. All values are scaled integers.
   *
   * @param base           the base asset (e.g. {@code BTC})
   * @param quote          the quote asset (e.g. {@code USD})
   * @param tickSize       the minimum price increment in scaled integer ticks
   * @param lotSize        the minimum quantity increment in scaled integer units
   * @param referencePrice the reference/seed price in scaled integer ticks; must be
   *     positive and aligned to the tick grid
   */
  public record PairProperties(
      String base, String quote, long tickSize, long lotSize, long referencePrice) {

    /** Validates the pair configuration. */
    public PairProperties {
      if (base == null || base.isBlank()) {
        throw new IllegalArgumentException("base asset must be provided");
      }
      if (quote == null || quote.isBlank()) {
        throw new IllegalArgumentException("quote asset must be provided");
      }
      if (tickSize <= 0) {
        throw new IllegalArgumentException("tick size must be positive: " + tickSize);
      }
      if (lotSize <= 0) {
        throw new IllegalArgumentException("lot size must be positive: " + lotSize);
      }
      if (referencePrice <= 0 || referencePrice % tickSize != 0) {
        throw new IllegalArgumentException(
            "reference price must be positive and tick-aligned: " + referencePrice);
      }
    }

    /**
     * The {@link Symbol} for this pair.
     *
     * @return the symbol
     */
    public Symbol toSymbol() {
      return new Symbol(base, quote, tickSize, lotSize);
    }

    /**
     * The stable pair id ({@code base-quote}).
     *
     * @return the pair id
     */
    public String pairId() {
      return base + "-" + quote;
    }
  }

  /**
   * One trader: an account with a bcrypt-hashed API key and opening balances.
   *
   * @param accountId  the account id
   * @param apiKeyHash the bcrypt hash of the trader's API key
   * @param cash       opening cash balance in quote units
   * @param asset      opening asset balance in units
   */
  public record TraderProperties(long accountId, String apiKeyHash, long cash, long asset) {}

  /**
   * Real-time broadcast settings; non-positive values fall back to defaults.
   *
   * @param bookHertz         book snapshot flush rate in Hz (default 10)
   * @param maxTradesPerFlush max trades pushed per flush (default 256)
   * @param tapeCapacity      bounded trade-tape capacity (default 1024)
   * @param clientBufferSize  per-client outbound buffer size (default 256)
   */
  public record BroadcastProperties(
      int bookHertz, int maxTradesPerFlush, int tapeCapacity, int clientBufferSize) {

    /** Applies defaults for any non-positive value. */
    public BroadcastProperties {
      bookHertz = bookHertz > 0 ? bookHertz : 10;
      maxTradesPerFlush = maxTradesPerFlush > 0 ? maxTradesPerFlush : 256;
      tapeCapacity = tapeCapacity > 0 ? tapeCapacity : 1024;
      clientBufferSize = clientBufferSize > 0 ? clientBufferSize : 256;
    }
  }
}
