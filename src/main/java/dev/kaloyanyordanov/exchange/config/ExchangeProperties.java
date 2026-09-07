package dev.kaloyanyordanov.exchange.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised configuration for the exchange: the traded symbol, ingress
 * capacity, the trader roster (API-key hashes and opening balances), and the
 * real-time broadcast settings.
 *
 * @param symbol          the traded symbol and its scaling rules
 * @param ingressCapacity the ingress queue capacity (rounded to a power of two)
 * @param traders         the configured traders
 * @param broadcast       the real-time broadcast settings
 */
@ConfigurationProperties(prefix = "exchange")
public record ExchangeProperties(
    SymbolProperties symbol,
    int ingressCapacity,
    List<TraderProperties> traders,
    BroadcastProperties broadcast) {

  /** Defensive copy of the trader list; default broadcast settings if unset. */
  public ExchangeProperties {
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
