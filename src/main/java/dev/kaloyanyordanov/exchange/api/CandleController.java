package dev.kaloyanyordanov.exchange.api;

import dev.kaloyanyordanov.exchange.candle.CandleService;
import dev.kaloyanyordanov.exchange.candle.Timeframe;
import dev.kaloyanyordanov.exchange.platform.ExchangeRegistry;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public chart data: OHLCV candles for a pair, timeframe, and time range, with the
 * SEEDED/REAL boundary. The {@code 1y} timeframe is served as daily candles over the
 * requested range.
 */
@RestController
public class CandleController {

  private static final Instant DEFAULT_TO = Instant.ofEpochSecond(4_102_444_800L); // year 2100

  private final CandleService candleService;
  private final ExchangeRegistry registry;

  /**
   * Creates the controller.
   *
   * @param candleService the candle query facade
   * @param registry      the pair registry (for pair validation)
   */
  public CandleController(CandleService candleService, ExchangeRegistry registry) {
    this.candleService = candleService;
    this.registry = registry;
  }

  /**
   * Candles for a pair and timeframe over an optional epoch-second range.
   *
   * @param pair      the pair id
   * @param timeframe the timeframe label ({@code 1m}..{@code 1M}, or {@code 1y})
   * @param from      the inclusive lower bound, epoch seconds (default 0)
   * @param to        the inclusive upper bound, epoch seconds (default far future)
   * @return the candle series, 404 for an unknown pair, or 400 for an unknown timeframe
   */
  @GetMapping("/candles")
  public ResponseEntity<Object> candles(
      @RequestParam String pair,
      @RequestParam String timeframe,
      @RequestParam(required = false) Long from,
      @RequestParam(required = false) Long to) {
    if (!registry.hasPair(pair)) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "unknown pair"));
    }
    Timeframe resolved = "1y".equals(timeframe) ? Timeframe.D1 : Timeframe.fromLabel(timeframe);
    if (resolved == null) {
      return ResponseEntity.badRequest().body(Map.of("error", "unknown timeframe"));
    }
    Instant fromInstant = from == null ? Instant.EPOCH : Instant.ofEpochSecond(from);
    Instant toInstant = to == null ? DEFAULT_TO : Instant.ofEpochSecond(to);
    return ResponseEntity.ok(candleService.series(pair, resolved, fromInstant, toInstant));
  }
}
