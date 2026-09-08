package dev.kaloyanyordanov.exchange.platform;

import dev.kaloyanyordanov.exchange.api.ExchangeService;
import dev.kaloyanyordanov.exchange.api.PlacementOutcome;
import dev.kaloyanyordanov.exchange.book.BookSnapshot;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.candle.CandleAggregatorWorker;
import dev.kaloyanyordanov.exchange.candle.CandleStore;
import dev.kaloyanyordanov.exchange.config.ExchangeProperties.PairProperties;
import dev.kaloyanyordanov.exchange.config.ExchangeProperties.TraderProperties;
import dev.kaloyanyordanov.exchange.config.GenesisFunder;
import dev.kaloyanyordanov.exchange.engine.EventPublisher;
import dev.kaloyanyordanov.exchange.engine.FanoutPublisher;
import dev.kaloyanyordanov.exchange.engine.MarketDataCache;
import dev.kaloyanyordanov.exchange.engine.MatchingEngine;
import dev.kaloyanyordanov.exchange.invariant.InvariantMonitor;
import dev.kaloyanyordanov.exchange.invariant.InvariantReport;
import dev.kaloyanyordanov.exchange.ledger.AssetLedger;
import dev.kaloyanyordanov.exchange.ledger.CashLedger;
import dev.kaloyanyordanov.exchange.payment.FundingResult;
import dev.kaloyanyordanov.exchange.payment.PaymentService;
import dev.kaloyanyordanov.exchange.realtime.PairBroadcasters;
import dev.kaloyanyordanov.exchange.sim.LoadSimulator;
import dev.kaloyanyordanov.exchange.sim.MetricsSnapshot;
import dev.kaloyanyordanov.exchange.sim.SimulatorConfig;
import dev.kaloyanyordanov.exchange.sim.SimulatorMetricsSink;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the five independent pair engines and routes every operation to the right one
 * by pair id. Each pair has its own single matching thread, order book, asset ledger,
 * read model, invariant monitor, and simulator - no mutable state is shared between
 * engines. Cash is the single exception, and it is not shared by memory: all five
 * engines settle through the one {@link CashLedger} actor by message passing (see
 * that class), which is why cross-pair cash is race-free.
 *
 * <p>This registry is also the lifecycle owner: it starts the cash ledger, funds
 * genesis cash, and starts every engine and monitor on {@link #start()}, and stops
 * them in the reverse order on {@link #stop()} so no settlement is lost.
 */
public final class ExchangeRegistry {

  /** The per-pair bundle; fields accessed only within this class. */
  private static final class PairEngine {
    private final Symbol symbol;
    private final PairProperties properties;
    private final MatchingEngine engine;
    private final MarketDataCache marketData;
    private final ExchangeService service;
    private final InvariantMonitor monitor;
    private final LoadSimulator simulator;
    private final CandleAggregatorWorker candleWorker;

    private PairEngine(
        Symbol symbol,
        PairProperties properties,
        MatchingEngine engine,
        MarketDataCache marketData,
        ExchangeService service,
        InvariantMonitor monitor,
        LoadSimulator simulator,
        CandleAggregatorWorker candleWorker) {
      this.symbol = symbol;
      this.properties = properties;
      this.engine = engine;
      this.marketData = marketData;
      this.service = service;
      this.monitor = monitor;
      this.simulator = simulator;
      this.candleWorker = candleWorker;
    }
  }

  private final Map<String, PairEngine> pairs = new LinkedHashMap<>();
  private final CashLedger cashLedger;
  private final PaymentService paymentService;
  private final List<TraderProperties> traders;
  private final Duration genesisTimeout = Duration.ofSeconds(5);

  /**
   * Builds the registry: one fully-wired engine per configured pair, sharing the cash
   * ledger, payment service, broadcaster, and (optional) persistence sink.
   *
   * @param pairConfigs         the configured pairs
   * @param ingressCapacity     each engine's ingress capacity
   * @param cashLedger          the shared cash ledger
   * @param paymentService      the deposit/withdrawal orchestrator
   * @param broadcasters        the per-pair real-time broadcast sinks
   * @param persistence         the optional shared persistence sink
   * @param candleStore         the shared candle store the per-pair aggregators feed
   * @param traders             the configured traders (for genesis endowment)
   * @param ledgerTimeout       reservation/snapshot timeout
   * @param checkIntervalMillis the invariant check interval
   * @param snapshotTimeoutMs   the invariant snapshot timeout
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "the cash ledger, payment service, broadcasters, and persistence sink are shared"
              + " singleton services; the registry stores the shared references and owns the"
              + " engines it builds around them by design")
  public ExchangeRegistry(
      List<PairProperties> pairConfigs,
      int ingressCapacity,
      CashLedger cashLedger,
      PaymentService paymentService,
      PairBroadcasters broadcasters,
      EventPublisher persistence,
      CandleStore candleStore,
      List<TraderProperties> traders,
      Duration ledgerTimeout,
      long checkIntervalMillis,
      long snapshotTimeoutMs) {
    this.cashLedger = cashLedger;
    this.paymentService = paymentService;
    this.traders = List.copyOf(traders);

    long index = 0;
    for (PairProperties config : pairConfigs) {
      Symbol symbol = config.toSymbol();
      AssetLedger assetLedger = new AssetLedger();
      MarketDataCache marketData = new MarketDataCache();
      for (TraderProperties trader : traders) {
        assetLedger.endow(trader.accountId(), trader.asset());
        marketData.seedAsset(trader.accountId(), trader.asset());
      }
      SimulatorMetricsSink metricsSink = new SimulatorMetricsSink();
      CandleAggregatorWorker candleWorker =
          new CandleAggregatorWorker(symbol.pairId(), candleStore, ingressCapacity, 500);
      EventPublisher pairBroadcaster = broadcasters.forPair(symbol.pairId());
      List<EventPublisher> sinks =
          new ArrayList<>(List.of(marketData, pairBroadcaster, metricsSink, candleWorker));
      if (persistence != null) {
        sinks.add(persistence);
      }
      FanoutPublisher publisher = new FanoutPublisher(sinks);

      // Distinct, non-overlapping id ranges per pair so shared persistence keys stay
      // globally unique across engines.
      long apiOrderBase = index * 1_000_000_000L;
      long simOrderBase = apiOrderBase + 500_000_000L;
      long tradeSeqBase = index * 1_000_000_000_000L;

      MatchingEngine engine =
          new MatchingEngine(
              symbol, ingressCapacity, publisher, assetLedger, cashLedger, tradeSeqBase);
      ExchangeService service =
          new ExchangeService(
              engine, marketData, cashLedger, paymentService, symbol, ledgerTimeout, apiOrderBase);
      InvariantMonitor monitor =
          new InvariantMonitor(engine, cashLedger, checkIntervalMillis, snapshotTimeoutMs);
      LoadSimulator simulator =
          new LoadSimulator(engine, symbol, cashLedger, metricsSink, simOrderBase);

      pairs.put(
          symbol.pairId(),
          new PairEngine(
              symbol, config, engine, marketData, service, monitor, simulator, candleWorker));
      index++;
    }
  }

  /** Starts the cash ledger, funds genesis cash, and starts every engine and monitor. */
  public void start() {
    cashLedger.start();
    new GenesisFunder(cashLedger, traders, genesisTimeout).fund();
    for (PairEngine pair : pairs.values()) {
      pair.candleWorker.start();
      pair.engine.start();
      pair.monitor.start();
    }
  }

  /**
   * Stops every monitor and engine, then the cash ledger (so no settlement is lost).
   *
   * @throws InterruptedException if interrupted while stopping
   */
  public void stop() throws InterruptedException {
    for (PairEngine pair : pairs.values()) {
      pair.monitor.stop();
      pair.engine.stop();
      pair.candleWorker.stop();
    }
    cashLedger.stop();
  }

  /**
   * The configured pairs, in order.
   *
   * @return the pair descriptors
   */
  public List<PairInfo> pairs() {
    List<PairInfo> infos = new ArrayList<>(pairs.size());
    for (PairEngine pair : pairs.values()) {
      PairProperties p = pair.properties;
      infos.add(
          new PairInfo(
              pair.symbol.pairId(), p.base(), p.quote(), p.tickSize(), p.lotSize(),
              p.referencePrice()));
    }
    return infos;
  }

  /**
   * Whether a pair id is known.
   *
   * @param pairId the pair id
   * @return {@code true} if a pair engine exists for it
   */
  public boolean hasPair(String pairId) {
    return pairs.containsKey(pairId);
  }

  /**
   * Places an order on a pair.
   *
   * @param pairId   the pair
   * @param account  the account
   * @param side     buy or sell
   * @param price    the limit price in ticks
   * @param quantity the quantity in units
   * @return the placement outcome
   */
  public PlacementOutcome place(String pairId, long account, Side side, long price, long quantity) {
    return require(pairId).service.place(account, side, price, quantity);
  }

  /**
   * The latest book snapshot for a pair.
   *
   * @param pairId the pair
   * @return the book snapshot
   */
  public BookSnapshot book(String pairId) {
    return require(pairId).service.book();
  }

  /**
   * The latest continuous invariant verdict for a pair.
   *
   * @param pairId the pair
   * @return the latest report
   */
  public InvariantReport invariants(String pairId) {
    return require(pairId).monitor.latest();
  }

  /**
   * A fresh on-demand invariant check for a pair.
   *
   * @param pairId the pair
   * @return the fresh report
   */
  public InvariantReport checkInvariants(String pairId) {
    return require(pairId).monitor.checkNow();
  }

  /**
   * Starts the simulator for a pair.
   *
   * @param pairId the pair
   * @param config the run configuration
   */
  public void startSimulator(String pairId, SimulatorConfig config) {
    require(pairId).simulator.start(config);
  }

  /**
   * Stops the simulator for a pair.
   *
   * @param pairId the pair
   */
  public void stopSimulator(String pairId) {
    require(pairId).simulator.stop();
  }

  /**
   * The simulator metrics for a pair.
   *
   * @param pairId the pair
   * @return the metrics snapshot
   */
  public MetricsSnapshot simulatorMetrics(String pairId) {
    return require(pairId).simulator.metrics();
  }

  /**
   * Deposits cash into an account (pair-independent).
   *
   * @param account the account
   * @param amount  the amount
   * @return the funding result
   */
  public FundingResult deposit(long account, long amount) {
    return paymentService.deposit(account, amount);
  }

  /**
   * Withdraws cash from an account (pair-independent).
   *
   * @param account the account
   * @param amount  the amount
   * @return the funding result
   */
  public FundingResult withdraw(long account, long amount) {
    return paymentService.withdraw(account, amount);
  }

  /**
   * An account's balances: shared cash (available plus reserved) and per-pair asset.
   *
   * @param account the account
   * @return the balances
   */
  public AccountBalances balance(long account) {
    long cash =
        cashLedger.snapshot(Duration.ofSeconds(2))
            .map(
                snapshot ->
                    snapshot.accounts().stream()
                        .filter(entry -> entry.accountId() == account)
                        .findFirst()
                        .map(entry -> entry.available() + entry.reserved())
                        .orElse(0L))
            .orElse(0L);
    List<AssetHolding> holdings = new ArrayList<>(pairs.size());
    for (PairEngine pair : pairs.values()) {
      holdings.add(
          new AssetHolding(pair.symbol.pairId(), pair.marketData.assetOf(account).orElse(0L)));
    }
    return new AccountBalances(account, cash, holdings);
  }

  private PairEngine require(String pairId) {
    PairEngine pair = pairs.get(pairId);
    if (pair == null) {
      throw new UnknownPairException(pairId);
    }
    return pair;
  }

  /** An account's asset holding in one pair. */
  public record AssetHolding(String pairId, long asset) {}

  /** An account's shared cash plus its per-pair asset holdings. */
  public record AccountBalances(long accountId, long cash, List<AssetHolding> holdings) {

    /** Defensive copy. */
    public AccountBalances {
      holdings = List.copyOf(holdings);
    }
  }

  /** Thrown when an operation names a pair with no engine. */
  public static final class UnknownPairException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param pairId the unknown pair id
     */
    public UnknownPairException(String pairId) {
      super("unknown pair: " + pairId);
    }
  }
}
