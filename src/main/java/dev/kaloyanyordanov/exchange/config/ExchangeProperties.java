package dev.kaloyanyordanov.exchange.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised configuration for the exchange: the traded symbol, ingress
 * capacity, and the trader roster (API-key hashes and opening balances).
 *
 * @param symbol          the traded symbol and its scaling rules
 * @param ingressCapacity the ingress queue capacity (rounded to a power of two)
 * @param traders         the configured traders
 */
@ConfigurationProperties(prefix = "exchange")
public record ExchangeProperties(
    SymbolProperties symbol, int ingressCapacity, List<TraderProperties> traders) {

  /** Defensive copy of the trader list. */
  public ExchangeProperties {
    traders = traders == null ? List.of() : List.copyOf(traders);
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
}
